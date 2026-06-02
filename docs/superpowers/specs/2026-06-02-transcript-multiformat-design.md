# Multi-Format Transcript Normalization — Design

**Date:** 2026-06-02
**Status:** Approved (pending user review of written spec)
**Scope:** Server-side `TranscriptNormalizer` refactor + 6 new agent format parsers

---

## 1. Background

### Current state

`server/.../transcript/TranscriptNormalizer.java` parses the `full.jsonl` transcript files
produced by `entireio-cli` for each AI agent session, normalizing them into a stable
`NormalizedTranscriptDTO` consumed by the admin session-detail UI.

The current normalizer handles only **two** of the **eight** agents that entireio-cli supports:

| Agent | Currently rendered? | Why |
|---|---|---|
| OpenCode | ✅ | `isObject() && has("messages") && messages.isArray()` |
| Cursor | ✅ | NDJSON probe (`role` + `message` per line) |
| Claude Code | ❌ | `isLikelyCursorNdjson` requires `role` field; Claude Code uses `type` instead |
| Codex | ❌ | Envelope shape (`type`+`payload`) not recognized |
| Factory AI Droid | ❌ | Envelope shape (`type:"message"`+`message`) not recognized |
| Gemini CLI | ❌ | Single JSON discriminator (`sessionId`+`messages` no `info`) not recognized |
| Copilot CLI | ❌ | Event-sourced (`type`+`data`) not recognized |
| Pi | ❌ | Tree-shaped (`type`+`parentId`+`message`) not recognized |

The user reported that Claude Code transcripts cannot be displayed. Exploration of
`entireio-cli`'s source (`cmd/entire/cli/transcript/compact/compact.go` and the
per-agent `transcript.go` files) shows the parser must be generalized to support
all six missing agents.

### Goals

- All eight agents render correctly with stable, useful output.
- Format detection is fast (O(1) probe, not full-line scan).
- Adding a future agent (e.g. a 9th) is a single new class — no change to the entry point.
- Existing OpenCode and Cursor behavior is preserved (zero regression on the two paths
  that work today).
- `NormalizedTranscriptDTO` shape and `SCHEMA_VERSION = 1` are preserved.

### Non-goals

- Replicating `entireio-cli`'s "compact" output (which is for normalized storage, not
  UI). The dashboard continues to render its own UI-shaped view.
- Copilot's full `parentId` tree reconstruction — we use a simplified linear event merge.
- Pi's strict active-branch filtering — we emit all branches in order with a warning.
- Streaming parse — full files are read into memory (KB–MB range).
- Frontend `transcript-parser.ts` fallback parser (separate concern, untouched).
- Schema version bump — `SCHEMA_VERSION` stays at 1 because the wire shape is unchanged.

---

## 2. Architecture

### 2.1 Strategy interface

```java
public interface TranscriptFormat {
    String name();                                              // e.g. "claude-code-ndjson"
    int priority();                                             // higher = tried first
    boolean matches(String raw);                                // cheap O(1) detection
    NormalizedTranscriptDTO parse(String raw,
                                  long rawBytesLength,
                                  List<String> warnings);      // throws → caller captures into warnings
}
```

Implementations are Spring `@Component` beans. The normalizer injects
`List<TranscriptFormat>` and sorts by `priority()` descending at construction time.

### 2.2 Directory layout

```
transcript/
├── TranscriptNormalizer.java       # entry point: strategy chain
├── TranscriptFormat.java           # strategy interface
├── format/
│   ├── OpenCodeFormat.java         # moved from TranscriptNormalizer.parseOpenCode
│   ├── CursorFormat.java           # moved from TranscriptNormalizer.parseCursorLines
│   ├── ClaudeCodeFormat.java       # new
│   ├── GeminiFormat.java           # new
│   ├── DroidFormat.java            # new
│   ├── CodexFormat.java            # new
│   ├── CopilotFormat.java          # new
│   └── PiFormat.java               # new
└── support/
    ├── AnthropicContentBlocks.java # shared: parse Anthropic-API content[] blocks
    └── FilePathExtractor.java      # shared: extract file path from tool_use input variants
```

**Abstraction level: coarse.** We deliberately do **not** introduce a
`SingleJsonFormat` / `JsonLinesFormat` base class, nor a `ContentBlockParser`
abstraction. Anthropic-API-compatible formats (Claude Code, Cursor, Droid) share
`AnthropicContentBlocks` and `FilePathExtractor` helpers — that's the only
sharing. Duplicated parse logic in the three classes is acceptable.

### 2.3 Entry point

```java
@Component
public class TranscriptNormalizer {
    private final List<TranscriptFormat> formats; // injected, sorted desc by priority

    public NormalizedTranscriptDTO normalize(String raw) {
        List<String> warnings = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return empty(lenBytes(raw == null ? "" : raw), warnings, FORMAT_UNKNOWN);
        }
        String trimmed = raw.trim();
        long rawBytesLength = lenBytes(raw);

        for (TranscriptFormat f : formats) {
            if (f.matches(trimmed)) {
                try {
                    NormalizedTranscriptDTO dto = f.parse(trimmed, rawBytesLength, warnings);
                    if (dto != null) {
                        return dto;
                    }
                } catch (Exception e) {
                    warnings.add(f.name() + " parse error: " + e.getMessage());
                }
                // matches() hit but parse() failed: keep trying lower priorities
                break;
            }
        }

        warnings.add("Unrecognized transcript format; no messages extracted");
        return empty(rawBytesLength, warnings, FORMAT_UNKNOWN);
    }
}
```

`normalize` never throws. On total failure it returns empty `messages`/`fileChanges`
with `sourceFormat = "unknown"` and warnings collected from each attempt.

---

## 3. Format priority, detection, naming

### 3.1 Priority order (higher = tried first)

| Priority | Format | Detection probe |
|---|---|---|
| 100 | `opencode-json` | trimmed parses as single JSON object, has `info` + `messages` |
| 90 | `gemini-json` | trimmed parses as single JSON object, has `sessionId` + `messages`, no `info` |
| 80 | `claude-code-ndjson` | non-empty first line is JSON object, `type ∈ {user, assistant}`, has `message` |
| 75 | `cursor-ndjson` | non-empty first line is JSON object, `role ∈ {user, assistant}`, has `message` |
| 70 | `droid-ndjson` | non-empty first line is JSON object, `type == "message"`, inner `message.role` exists |
| 60 | `codex-ndjson` | **any** line has `type ∈ {session_meta, response_item, event_msg, turn_context}` |
| 50 | `copilot-cli-ndjson` | **any** line has `type ∈ {session.start, user.message, assistant.message, tool.execution_complete}` |
| 40 | `pi-ndjson` | non-empty first line is JSON object, `type == "session"`, has `version` |

### 3.2 Detection rules

- **First-line vs any-line**:
  - ClaudeCode / Cursor / Droid / Pi use first-line detection (`type`/`role`/`version`
    is stable on the first meaningful line).
  - Codex / Copilot use any-line detection (their first line is often a header like
    `session_meta` / `session.start`, not a message).
- **No full-line pre-scan** (replaces the old `isLikelyCursorNdjson`). The new
  `matches()` is O(1) for first-line formats and O(n) for any-line formats, but
  stops at first hit. `parse()` is responsible for tolerating bad rows.
- **No exception in `matches()`** — return `false` on any parse error.

### 3.3 Format name strings

Names match `entireio-cli`'s `agent/registry.go` registry keys (kebab-case), suffixed
with the structural form. **This is a breaking change for `sourceFormat` values**
(see Migration §6).

| Old | New |
|---|---|
| `cursor_ndjson` | `cursor-ndjson` |
| `opencode_json` | `opencode-json` |
| (new) | `claude-code-ndjson` |
| (new) | `gemini-json` |
| (new) | `droid-ndjson` |
| (new) | `codex-ndjson` |
| (new) | `copilot-cli-ndjson` |
| (new) | `pi-ndjson` |
| `unknown` | `unknown` (unchanged) |

`sourceFormat` is consumed by the frontend only for display; no if/else branches
depend on it. Renaming is safe.

---

## 4. Per-format parsing details

### 4.1 OpenCode (existing — moved)

Unchanged from current `parseOpenCode`. Single JSON object, `info`+`messages`+`parts`.
Per-message diffs from `info.summary.diffs[]`. Tool calls from `parts[type="tool"]`.
Project root stripping for relative file paths.

### 4.2 Gemini (new)

Single JSON object, `sessionId`+`messages`+no `info`. Per-message `type` is `user` /
`gemini` (assistant) / `info` (drop). Tool calls live at the message level in
`toolCalls[]` (not inside `content`). Reasoning is `thoughts[]` — concatenate
descriptions with newlines into the `reasoning` field. Token usage:
`messages[i].tokens.{input, output, cached, thoughts, tool, total}`. File changes:
extract from `toolCalls[i].args.file_path` / `args.path` / `args.filename` and
`toolCalls[i].resultDisplay.filePath` if present.

### 4.3 Cursor (existing — moved)

Unchanged from current `parseCursorMessage`. NDJSON, `role`+`message` per line.
File-path extraction from `tool_use.input` already implemented.

**Pre-existing bug fix included**: Cursor transcripts per `entireio-cli`'s
`cursor/transcript.go:108-113` **do not contain `tool_use` blocks**. The current
`parseCursorMessage` reads them anyway, which is harmless but produces empty
`fileChanges`. We retain the `tool_use` reading (older transcripts may have them)
and add a warning when no `tool_use` blocks are found:
`"cursor: tool_use blocks absent; file_changes may be empty"`.

### 4.4 Claude Code (new — primary user pain point)

NDJSON, `type`+`message` per line. Key behaviors:

- **Type filter**: keep only `type == "user"` and `type == "assistant"`. Drop
  `system`, `progress`, `file-history-snapshot`, `queue-operation`,
  `rate_limit_event`, `result`.
- **Streaming dedup**: multiple JSONL rows can share the same `message.id`
  (Anthropic API streaming). Merge by:
  - Cache the latest assistant line for each `message.id`.
  - Same id arriving → overwrite-merge `content[]` (dedupe by `type`), take
    the maximum `usage.output_tokens` value seen.
  - Emit the final merged row when a different `id` arrives or end of input.
- **Content blocks**: use shared `AnthropicContentBlocks` helper to parse
  `content[]` array of `{type, text|thinking|tool_use|tool_result|image}` blocks.
- **Tool result merging**: a `type:"user"` line whose `message.content[]` contains
  `tool_result` blocks should fold its `content` into the matching prior
  assistant's `tool_use.output`.
- **Tool name mapping**: `Read`, `Edit`, `Write`, `Bash`, `Grep`, `Glob`, `Skill`,
  `NotebookEdit`, `WebFetch`, `WebSearch`, `TodoWrite`, etc. — passed through
  unchanged (the dashboard's UI shows the raw tool name).

### 4.5 Droid (new)

NDJSON, `type`+`message` envelope. Outer `type` is always `"message"` for chat
lines (plus `session_start` / `session_event` non-message lines we drop). The
inner `message` shape is identical to Claude Code's Anthropic-API shape — we
delegate to the same `AnthropicContentBlocks` helper. Streaming dedup applies
identically to Claude Code.

### 4.6 Codex (new)

NDJSON with `type`+`payload` envelope. Two-level dispatch:

```
type: response_item
  payload.type: "message"           → emit MessageView
  payload.type: "function_call"     → attach tool_use to most recent assistant
  payload.type: "custom_tool_call"  → same (apply_patch style)
  payload.type: "reasoning"         → attach to most recent assistant's reasoning
  payload.type: "function_call_output" / "custom_tool_call_output" → fill matching tool_use.output
type: event_msg
  payload.type: "token_count"       → cumulative token tracker (see below)
  payload.type: "user_message"      → emit user MessageView
  payload.type: "agent_message"     → emit assistant MessageView
  payload.type: "task_started" / "task_completed" / "turn_***" → drop
type: session_meta / turn_context  → drop
```

**Token aggregation**: `event_msg.payload.token_count` gives cumulative
`{input_tokens, output_tokens}` per `model`. Track `Map<modelName, lastCounts>`
across the parse; on each `token_count` event, compute
`delta = current - lastCounts[model]` and attach to the most recent assistant
message's token field. If `token_count` is missing for the whole transcript,
omit the token field entirely (do not fabricate).

### 4.7 Copilot CLI (new — simplified)

NDJSON event stream, `type`+`data` envelope. **Simplified merge strategy**
(we do **not** reconstruct the full parentId turn tree):

- `session.start`, `session.info`, `hook.start`, `hook.end` → drop.
- `user.message.data.content` (string) → new user MessageView.
- `assistant.message.data.content` (string) → new assistant MessageView; if
  `data.toolRequests[]` is present, attach to that same message.
- `tool.execution_complete.data` → find `toolCallId` in the most recent
  assistant message's `tools[]` and backfill `output`. If
  `result.detailedContent` looks like a git diff, attempt a file-diff parse
  for the first file mentioned in `toolTelemetry.properties.filePaths`
  (JSON-stringified array of paths).
- `session.shutdown.data.modelMetrics` → optional meta-level token summary.
- `assistant.turn_start` / `assistant.turn_end` → drop.

Add a warning: `"copilot-cli: linear event merge; tool/turn ordering may be approximate"`.

### 4.8 Pi (new — simplified)

NDJSON tree, `type`+`parentId`+`message`. **Simplified branch handling**
(we **do not** filter abandoned branches):

- Keep only `type == "message"` entries, in file order.
- `message.role: "user" | "assistant"` → MessageView. `role: "toolResult"` →
  fold `content` into the most recent assistant's `tool_use.output` matched
  by `toolCallId`.
- `message.content` is either a string or an array of `{type:"text"}` /
  `{type:"toolCall", name, id, arguments}` blocks. Parse accordingly.
- File changes: extract from tool-call `arguments.path` (tool names: `write`, `edit`).
- Per-message `message.usage.{input, output, cacheRead, cacheWrite}` → token field.

Add a warning: `"pi: branch filtering not implemented; abandoned branches included"`.

---

## 5. Data flow & error handling

### 5.1 Per-call contract

- `normalize(raw)` is the only public entry point. **Never throws.**
- On `raw == null` or empty → empty DTO with `sourceFormat = "unknown"`.
- On every format probe failing → empty DTO with `sourceFormat = "unknown"`.
- On a format matching but `parse()` throwing → that exception is captured as
  a warning; the loop stops at the matched format (we don't try lower
  priorities after a match — the matched format is the right agent, the data
  is just malformed).

### 5.2 Warning accumulation

`warnings: List<String>` is a mutable list passed into each `parse()` call.
Parsers may add to it at any point. Typical entries:
- `"cursor: tool_use blocks absent; file_changes may be empty"`
- `"copilot-cli: linear event merge; tool/turn ordering may be approximate"`
- `"pi: branch filtering not implemented; abandoned branches included"`
- `"claude-code-ndjson parse error: <message>"`
- `"Unrecognized transcript format; no messages extracted"`

Warnings are surfaced via `meta.warnings` to the admin UI for debug visibility.

---

## 6. Migration plan

### 6.1 New files (11)

- `TranscriptFormat.java` (interface)
- `format/OpenCodeFormat.java`
- `format/CursorFormat.java`
- `format/ClaudeCodeFormat.java`
- `format/GeminiFormat.java`
- `format/DroidFormat.java`
- `format/CodexFormat.java`
- `format/CopilotFormat.java`
- `format/PiFormat.java`
- `support/AnthropicContentBlocks.java`
- `support/FilePathExtractor.java`

Plus one test file:
- `TranscriptNormalizerTest.java`

### 6.2 Modified files (1)

- `TranscriptNormalizer.java` — delete `parseOpenCode`, `parseCursorLines`,
  `isLikelyCursorNdjson`, `parseCursorMessage`, `buildCursorFileChanges`,
  `extractCursorPath`, `estimateCursorToolLineDelta`, `countLines`,
  `sharedDirectoryPrefix`, `commonPrefixLength`, `toRelativePath`,
  `splitNonEmptyLines`. Replace the body with the strategy chain
  (§2.3). Public surface (`normalize(...)`, `SCHEMA_VERSION`, format name
  constants) is preserved.

### 6.3 Unchanged files

- `NormalizedTranscriptDTO.java` — output shape unchanged.
- `NormalizedTranscriptMetaDTO.java` — `sourceFormat` field already a string.
- `SessionServiceImpl.java`, `SessionService.java`, `SessionController.java` —
  no API change.
- Frontend `transcript-parser.ts` — its OpenCode fallback parser is independent.

### 6.4 Schema version

`SCHEMA_VERSION = 1` stays. Wire shape (`messages[]`, `fileChanges[]`, `tools[]`)
is unchanged. Bumping to 2 is reserved for actual shape changes.

---

## 7. Testing

### 7.1 Fixtures

Copy from `entireio-cli/cmd/entire/cli/transcript/compact/testdata/` into
`server/src/test/resources/transcript-fixtures/`:

| Source | Destination |
|---|---|
| `claude_full.jsonl` | `claude-code.jsonl` |
| `claude_full2.jsonl` | `claude-code-2.jsonl` |
| `codex_full.jsonl` | `codex.jsonl` |
| `copilot_full.jsonl` | `copilot-cli.jsonl` |
| `droid_full.jsonl` | `droid.jsonl` |
| `gemini_full.jsonl` | `gemini.jsonl` |
| `opencode_full.jsonl` | `opencode.jsonl` |

These are the real-shape fixtures used by entireio-cli's own tests, one per
agent. Future agents get a fixture copied the same way.

### 7.2 Test cases (`TranscriptNormalizerTest.java`)

One `@Nested` class per format:

- `ClaudeCode` — parses `claude-code.jsonl`; deduplicates streaming in
  `claude-code-2.jsonl`; skips non-message types.
- `Cursor` — parses a small inline fixture; behavior is **frozen** at current
  state (no regression).
- `OpenCode` — parses fixture; behavior is **frozen** at current state.
- `Gemini` — parses fixture; tool calls from `toolCalls[]` work.
- `Droid` — parses fixture; envelope unwrap works.
- `Codex` — `response_item` dispatch; cumulative token delta is non-negative.
- `CopilotCli` — parses fixture; user/assistant/tool merge happens.
- `Pi` — parses fixture; warning about branch filtering is present.
- `Unknown` — input of `"not json at all\n\n!!!"` returns `sourceFormat =
  "unknown"` and the "Unrecognized transcript format" warning.

### 7.3 Assertion style

Assert on **shape**, not on exact message content (so fixture micro-changes
don't break tests):
- `dto.getMeta().getSourceFormat()` equals the expected constant.
- `dto.getMessages()` is non-empty.
- `dto.getStepsCount() >= 1`.
- At least one message has non-empty `text` and at least one message has
  `tools` populated (where the format supports tools).
- For formats with file changes, `dto.getFileChanges()` is non-empty for the
  fixture.

### 7.4 Verification sequence (on completion)

1. `mvn test` — all unit tests pass.
2. `mvn spring-boot:run` — backend boots cleanly with new strategy beans.
3. For each agent, hit
   `GET /api/sessions/{id}/transcript/normalized` with a real transcript in
   the local checkout and confirm the response renders sensibly in the
   session-detail UI.

---

## 8. Out of scope (explicitly not done)

- Copilot's full `parentId` tree reconstruction (we use simplified linear merge).
- Pi's strict active-branch filtering.
- A `SingleJsonFormat` / `JsonLinesFormat` base class.
- A `ContentBlockParser` strategy interface.
- Modifying `NormalizedTranscriptDTO` fields.
- Modifying the frontend fallback parser.
- Streaming parse (full files are loaded into memory).
- Bumping `SCHEMA_VERSION`.
