package com.mzfuture.entire.checkpoint.controller;

import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptDTO;
import com.mzfuture.entire.checkpoint.dto.response.SessionDTO;
import com.mzfuture.entire.checkpoint.service.SessionService;
import com.mzfuture.entire.common.dto.IdPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Session API: list by checkpoint, get by id, get content (prompt/context/transcript from Git).
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/session")
@Tag(name = "Session", description = "Session list, get and content APIs")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/get")
    @Operation(summary = "Get session", description = "Get session by ID")
    public SessionDTO get(@ModelAttribute @Valid IdPayload payload) {
        return sessionService.get(payload.getId());
    }

    @GetMapping("/list")
    @Operation(summary = "List sessions by checkpoint", description = "List sessions for a checkpoint (Checkpoint.id), ordered by session_index")
    public List<SessionDTO> list(
            @Parameter(description = "Checkpoint ID (Checkpoint.id, not 12-char hex)")
            @RequestParam Long checkpointId) {
        return sessionService.listByCheckpointId(checkpointId);
    }

    @GetMapping(value = "/content", produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Get session content", description = "Get prompt.txt, context.md or full.jsonl (transcript) from Git for the session. Uses metadata branch.")
    public ResponseEntity<String> content(
            @Parameter(description = "Session ID")
            @RequestParam Long sessionId,
            @Parameter(description = "File: prompt, context, or transcript")
            @RequestParam String file) {
        return sessionService.getContent(sessionId, file)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/transcript/normalized", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get normalized transcript", description = "Returns stable JSON for UI (OpenCode JSON, Cursor NDJSON, or unknown with warnings). Raw file unchanged; use /content?file=transcript for original text.")
    public ResponseEntity<NormalizedTranscriptDTO> normalizedTranscript(
            @Parameter(description = "Session ID")
            @RequestParam Long sessionId) {
        return sessionService.getNormalizedTranscript(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
