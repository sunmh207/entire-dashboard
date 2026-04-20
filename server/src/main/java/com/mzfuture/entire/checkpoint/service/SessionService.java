package com.mzfuture.entire.checkpoint.service;

import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptDTO;
import com.mzfuture.entire.checkpoint.dto.response.SessionDTO;

import java.util.List;
import java.util.Optional;

/// Session read and content API (sessions are synced from Git; no create/update via API).
public interface SessionService {

    /// Get single session by ID
    SessionDTO get(Long id);

    /// List sessions by checkpoint (Checkpoint.id), ordered by session_index
    List<SessionDTO> listByCheckpointId(Long checkpointId);

    /// Get session content from Git (prompt, context, or transcript). Uses metadata branch revision.
    /// @param file one of: prompt, context, transcript (maps to prompt.txt, context.md, full.jsonl)
    Optional<String> getContent(Long sessionId, String file);

    /// Parsed transcript for admin UI (OpenCode JSON, Cursor NDJSON, or unknown with warnings).
    Optional<NormalizedTranscriptDTO> getNormalizedTranscript(Long sessionId);
}
