package com.mzfuture.entire.checkpoint.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptDTO;
import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptMetaDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptFileChangeSummaryDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptFileDiffDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptMessageViewDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptToolUseDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Parses raw `full.jsonl` / transcript text into the stable `NormalizedTranscriptDTO` used by the admin UI.
/// Supports OpenCode single JSON and Cursor-style NDJSON (one JSON object per line).
@Component
public class TranscriptNormalizer {

    public static final int SCHEMA_VERSION = 1;
    public static final String FORMAT_CURSOR_NDJSON = "cursor_ndjson";
    public static final String FORMAT_OPENCODE_JSON = "opencode_json";
    public static final String FORMAT_UNKNOWN = "unknown";

    // Use a local mapper: this project does not expose ObjectMapper as a Spring bean (see SessionMetadataParser).
    private final ObjectMapper objectMapper = new ObjectMapper();

    /// Normalize transcript text. Never throws; on total failure returns empty messages and `unknown` format with warnings.
    public NormalizedTranscriptDTO normalize(String raw) {
        List<String> warnings = new ArrayList<>();
        if (raw == null) {
            return empty(lenBytes(""), warnings, FORMAT_UNKNOWN);
        }
        long rawBytesLength = lenBytes(raw);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            warnings.add("Empty transcript");
            return empty(rawBytesLength, warnings, FORMAT_UNKNOWN);
        }

        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root.isObject()) {
                if (root.has("messages") && root.get("messages").isArray()) {
                    return parseOpenCode(root, rawBytesLength, warnings);
                }
                if (root.has("role") && root.has("message")) {
                    return parseCursorLines(Collections.singletonList(trimmed), rawBytesLength, warnings);
                }
            }
        } catch (Exception ignored) {
            // Try NDJSON below
        }

        List<String> lines = splitNonEmptyLines(trimmed);
        if (!lines.isEmpty() && isLikelyCursorNdjson(lines)) {
            try {
                return parseCursorLines(lines, rawBytesLength, warnings);
            } catch (Exception e) {
                warnings.add("cursor_ndjson parse error: " + e.getMessage());
            }
        }

        warnings.add("Unrecognized transcript format; no messages extracted");
        return empty(rawBytesLength, warnings, FORMAT_UNKNOWN);
    }

    private static long lenBytes(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8).length;
    }

    private NormalizedTranscriptDTO empty(long rawBytesLength, List<String> warnings, String format) {
        NormalizedTranscriptDTO dto = new NormalizedTranscriptDTO();
        dto.setSchemaVersion(SCHEMA_VERSION);
        dto.setMeta(meta(format, rawBytesLength, warnings));
        dto.setStepsCount(0);
        dto.setFileChanges(Collections.emptyList());
        dto.setMessages(Collections.emptyList());
        return dto;
    }

    private NormalizedTranscriptMetaDTO meta(String sourceFormat, long rawBytesLength, List<String> warnings) {
        NormalizedTranscriptMetaDTO m = new NormalizedTranscriptMetaDTO();
        m.setSourceFormat(sourceFormat);
        m.setRawBytesLength(rawBytesLength);
        if (!warnings.isEmpty()) {
            m.setWarnings(new ArrayList<>(warnings));
        }
        return m;
    }

    private List<String> splitNonEmptyLines(String trimmed) {
        String[] parts = trimmed.split("\\R");
        List<String> lines = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        return lines;
    }

    private boolean isLikelyCursorNdjson(List<String> lines) {
        for (String line : lines) {
            try {
                JsonNode n = objectMapper.readTree(line);
                if (!n.isObject() || !n.has("role") || !n.has("message")) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private NormalizedTranscriptDTO parseCursorLines(List<String> lines, long rawBytesLength, List<String> warnings)
            throws Exception {
        List<TranscriptMessageViewDTO> messageViews = new ArrayList<>();
        int stepsCount = 0;
        for (String line : lines) {
            JsonNode n = objectMapper.readTree(line);
            TranscriptMessageViewDTO mv = parseCursorMessage(n);
            if ("user".equals(mv.getRole())) {
                stepsCount++;
            }
            messageViews.add(mv);
        }
        List<TranscriptFileChangeSummaryDTO> fileChanges = buildCursorFileChanges(messageViews);

        NormalizedTranscriptDTO dto = new NormalizedTranscriptDTO();
        dto.setSchemaVersion(SCHEMA_VERSION);
        dto.setMeta(meta(FORMAT_CURSOR_NDJSON, rawBytesLength, warnings));
        dto.setStepsCount(stepsCount);
        dto.setFileChanges(fileChanges);
        dto.setMessages(messageViews);
        return dto;
    }

    /// Aggregate touched files from Cursor `tool_use` blocks (path in input, StrReplace line deltas).
    private List<TranscriptFileChangeSummaryDTO> buildCursorFileChanges(List<TranscriptMessageViewDTO> messages) {
        List<PathDelta> raw = new ArrayList<>();
        for (TranscriptMessageViewDTO msg : messages) {
            if (msg.getTools() == null) {
                continue;
            }
            for (TranscriptToolUseDTO tu : msg.getTools()) {
                String normalizedPath = extractCursorPath(tu);
                if (normalizedPath == null || normalizedPath.isBlank()) {
                    continue;
                }
                int[] d = estimateCursorToolLineDelta(tu);
                raw.add(new PathDelta(normalizedPath, d[0], d[1]));
            }
        }
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> distinct = raw.stream().map(p -> p.path).distinct().toList();
        String dirPrefix = sharedDirectoryPrefix(distinct);

        Map<String, int[]> merged = new LinkedHashMap<>();
        for (PathDelta pd : raw) {
            String key = toRelativePath(pd.path, dirPrefix);
            merged.compute(key, (k, v) -> {
                if (v == null) {
                    return new int[]{pd.additions, pd.deletions};
                }
                v[0] += pd.additions;
                v[1] += pd.deletions;
                return v;
            });
        }

        List<TranscriptFileChangeSummaryDTO> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : merged.entrySet()) {
            TranscriptFileChangeSummaryDTO s = new TranscriptFileChangeSummaryDTO();
            s.setFile(e.getKey());
            s.setAdditions(e.getValue()[0]);
            s.setDeletions(e.getValue()[1]);
            out.add(s);
        }
        return out;
    }

    private static final class PathDelta {
        final String path;
        final int additions;
        final int deletions;

        PathDelta(String path, int additions, int deletions) {
            this.path = path;
            this.additions = additions;
            this.deletions = deletions;
        }
    }

    /// Normalize slashes; return null if no usable path field.
    private String extractCursorPath(TranscriptToolUseDTO tu) {
        JsonNode in = tu.getInput();
        if (in == null || !in.isObject()) {
            return null;
        }
        String[] keys = {"path", "file", "target_file", "target_directory", "file_path"};
        for (String k : keys) {
            if (in.has(k) && in.get(k).isTextual()) {
                String p = in.get(k).asText("");
                if (!p.isBlank()) {
                    return p.replace('\\', '/').trim();
                }
            }
        }
        return null;
    }

    /// Rough line-delta from StrReplace / Write; other tools count as touch-only (0,0).
    private int[] estimateCursorToolLineDelta(TranscriptToolUseDTO tu) {
        String name = tu.getTool() == null ? "" : tu.getTool().trim();
        String lower = name.toLowerCase(Locale.ROOT);
        JsonNode in = tu.getInput();
        if (in == null || !in.isObject()) {
            return new int[]{0, 0};
        }
        if ("strreplace".equals(lower) || "search_replace".equals(lower) || "replace".equals(lower)) {
            String oldS = in.has("old_string") && in.get("old_string").isTextual() ? in.get("old_string").asText("") : "";
            String newS = in.has("new_string") && in.get("new_string").isTextual() ? in.get("new_string").asText("") : "";
            int oldLines = countLines(oldS);
            int newLines = countLines(newS);
            int add = Math.max(0, newLines - oldLines);
            int del = Math.max(0, oldLines - newLines);
            return new int[]{add, del};
        }
        if ("write".equals(lower) || "apply_patch".equals(lower)) {
            String contents = "";
            if (in.has("contents") && in.get("contents").isTextual()) {
                contents = in.get("contents").asText("");
            } else if (in.has("content") && in.get("content").isTextual()) {
                contents = in.get("content").asText("");
            }
            int lines = countLines(contents);
            return new int[]{lines, 0};
        }
        if ("delete_file".equals(lower) || "delete".equals(lower)) {
            return new int[]{0, 1};
        }
        return new int[]{0, 0};
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /// Longest shared directory prefix ending with `/` (so relative paths are stable across tools).
    private static String sharedDirectoryPrefix(List<String> paths) {
        if (paths.isEmpty()) {
            return "";
        }
        if (paths.size() == 1) {
            String p = paths.get(0);
            int slash = p.lastIndexOf('/');
            if (slash <= 0) {
                return "";
            }
            return p.substring(0, slash + 1);
        }
        String first = paths.get(0);
        int minLen = first.length();
        for (String p : paths) {
            minLen = Math.min(minLen, commonPrefixLength(first, p));
        }
        String prefix = first.substring(0, minLen);
        int slash = prefix.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        return prefix.substring(0, slash + 1);
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static String toRelativePath(String absoluteOrRel, String dirPrefix) {
        if (dirPrefix != null && !dirPrefix.isEmpty() && absoluteOrRel.startsWith(dirPrefix)) {
            return absoluteOrRel.substring(dirPrefix.length());
        }
        return absoluteOrRel;
    }

    private TranscriptMessageViewDTO parseCursorMessage(JsonNode root) {
        String roleRaw = textOrNull(root, "role");
        String role = "user".equalsIgnoreCase(roleRaw) ? "user" : "assistant";

        JsonNode message = root.path("message");
        JsonNode content = message.path("content");

        StringBuilder textParts = new StringBuilder();
        List<TranscriptToolUseDTO> tools = new ArrayList<>();

        if (content.isArray()) {
            for (JsonNode block : content) {
                String type = textOrNull(block, "type");
                if ("text".equals(type)) {
                    String t = block.has("text") ? block.get("text").asText("") : "";
                    if (textParts.length() > 0) {
                        textParts.append('\n');
                    }
                    textParts.append(t);
                } else if ("tool_use".equals(type)) {
                    TranscriptToolUseDTO tu = new TranscriptToolUseDTO();
                    tu.setCallID(firstNonEmpty(textOrNull(block, "id"), textOrNull(block, "call_id")));
                    tu.setTool(textOrNull(block, "name"));
                    if (block.has("input") && !block.get("input").isNull()) {
                        tu.setInput(block.get("input"));
                    }
                    tools.add(tu);
                }
            }
        }

        TranscriptMessageViewDTO mv = new TranscriptMessageViewDTO();
        mv.setId("");
        mv.setRole(role);
        mv.setText(textParts.toString().trim());
        mv.setTools(tools);
        mv.setToolsCount(tools.size());
        return mv;
    }

    private NormalizedTranscriptDTO parseOpenCode(JsonNode root, long rawBytesLength, List<String> warnings) {
        JsonNode info = root.path("info");
        String title = textOrNull(info, "title");
        String projectRoot = textOrNull(info, "directory");

        Map<String, int[]> fileChangeMap = new LinkedHashMap<>();
        List<TranscriptMessageViewDTO> messageViews = new ArrayList<>();
        int stepsCount = 0;

        JsonNode messages = root.path("messages");
        if (!messages.isArray()) {
            warnings.add("OpenCode: missing messages array");
        } else {
            for (JsonNode msg : messages) {
                JsonNode infoN = msg.path("info");
                String roleStr = textOrNull(infoN, "role");
                boolean isUser = "user".equalsIgnoreCase(roleStr);
                if (isUser) {
                    stepsCount++;
                }

                JsonNode parts = msg.path("parts");
                StringBuilder textParts = new StringBuilder();
                String reasoning = null;
                List<TranscriptToolUseDTO> tools = new ArrayList<>();

                if (parts.isArray()) {
                    for (JsonNode p : parts) {
                        String type = textOrNull(p, "type");
                        if ("text".equals(type)) {
                            String t = p.has("text") ? p.get("text").asText("") : "";
                            if (textParts.length() > 0) {
                                textParts.append('\n');
                            }
                            textParts.append(t);
                        } else if ("reasoning".equals(type)) {
                            reasoning = p.has("text") ? p.get("text").asText("") : null;
                        } else if ("tool".equals(type)) {
                            TranscriptToolUseDTO tu = new TranscriptToolUseDTO();
                            tu.setCallID(textOrNull(p, "callID"));
                            tu.setTool(textOrNull(p, "tool"));
                            JsonNode state = p.path("state");
                            if (!state.isMissingNode() && !state.isNull()) {
                                if (state.has("input")) {
                                    tu.setInput(state.get("input"));
                                }
                                if (state.has("output")) {
                                    tu.setOutput(state.get("output"));
                                }
                                tu.setTitle(textOrNull(state, "title"));
                            }
                            tools.add(tu);
                        }
                    }
                }

                JsonNode summary = infoN.path("summary");
                List<TranscriptFileDiffDTO> diffs = null;
                if (summary.has("diffs") && summary.get("diffs").isArray()) {
                    diffs = new ArrayList<>();
                    for (JsonNode d : summary.get("diffs")) {
                        TranscriptFileDiffDTO fd = new TranscriptFileDiffDTO();
                        String file = d.has("file") ? d.get("file").asText("") : "";
                        fd.setFile(stripProjectRoot(file, projectRoot));
                        fd.setBefore(textOrNull(d, "before"));
                        fd.setAfter(textOrNull(d, "after"));
                        int add = d.has("additions") ? d.get("additions").asInt() : 0;
                        int del = d.has("deletions") ? d.get("deletions").asInt() : 0;
                        fd.setAdditions(add);
                        fd.setDeletions(del);
                        diffs.add(fd);
                        String rel = fd.getFile();
                        fileChangeMap.compute(rel, (k, v) -> {
                            if (v == null) {
                                return new int[]{add, del};
                            }
                            v[0] += add;
                            v[1] += del;
                            return v;
                        });
                    }
                }

                TranscriptMessageViewDTO mv = new TranscriptMessageViewDTO();
                mv.setId(textOrNull(infoN, "id"));
                mv.setRole(isUser ? "user" : "assistant");
                mv.setText(textParts.toString().trim());
                mv.setTaskTitle(textOrNull(summary, "title"));
                mv.setReasoning(reasoning);
                JsonNode time = infoN.path("time");
                if (time.has("created")) {
                    mv.setCreatedAt(time.get("created").asLong());
                }
                mv.setTools(tools);
                mv.setToolsCount(tools.size());
                mv.setDiffs(diffs);
                messageViews.add(mv);
            }
        }

        List<TranscriptFileChangeSummaryDTO> fileChanges = new ArrayList<>();
        for (Map.Entry<String, int[]> e : fileChangeMap.entrySet()) {
            TranscriptFileChangeSummaryDTO s = new TranscriptFileChangeSummaryDTO();
            s.setFile(e.getKey());
            s.setAdditions(e.getValue()[0]);
            s.setDeletions(e.getValue()[1]);
            fileChanges.add(s);
        }

        NormalizedTranscriptDTO dto = new NormalizedTranscriptDTO();
        dto.setSchemaVersion(SCHEMA_VERSION);
        dto.setMeta(meta(FORMAT_OPENCODE_JSON, rawBytesLength, warnings));
        dto.setTitle(title);
        dto.setProjectRoot(projectRoot);
        dto.setStepsCount(stepsCount);
        dto.setFileChanges(fileChanges);
        dto.setMessages(messageViews);
        return dto;
    }

    private static String stripProjectRoot(String filePath, String projectRoot) {
        if (projectRoot == null || projectRoot.isEmpty()) {
            return filePath;
        }
        String normalized = filePath.replace('\\', '/');
        String root = projectRoot.replace('\\', '/').replaceAll("/$", "");
        if (normalized.startsWith(root + "/")) {
            return normalized.substring(root.length() + 1);
        }
        return filePath;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v.isValueNode()) {
            return v.asText();
        }
        return null;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        return a != null ? a : b;
    }
}
