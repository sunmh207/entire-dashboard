package com.mzfuture.entire.checkpoint.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/// Canonical transcript view for the admin session detail UI. Versioned for forward compatibility.
///
/// Use NON_NULL (not NON_EMPTY): empty `messages` / `fileChanges` must still serialize so clients
/// always receive arrays; NON_EMPTY omits empty collections and breaks strict UI parsers.
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Normalized session transcript (stable JSON for UI)")
public class NormalizedTranscriptDTO {

    @Schema(description = "Schema version; increment when breaking field semantics change", example = "1")
    private Integer schemaVersion;

    private NormalizedTranscriptMetaDTO meta;

    @Schema(description = "Session title when present (OpenCode)")
    private String title;

    @Schema(description = "Project root directory when present (OpenCode)")
    private String projectRoot;

    @Schema(description = "User message count (step count)")
    private Integer stepsCount;

    private List<TranscriptFileChangeSummaryDTO> fileChanges;

    private List<TranscriptMessageViewDTO> messages;
}
