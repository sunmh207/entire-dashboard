package com.mzfuture.entire.checkpoint.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// Aggregated line stats per file (from OpenCode summary diffs).
@Data
@Schema(description = "Per-file addition/deletion totals")
public class TranscriptFileChangeSummaryDTO {

    @Schema(description = "Relative file path")
    private String file;

    @Schema(description = "Lines added")
    private Integer additions;

    @Schema(description = "Lines deleted")
    private Integer deletions;
}
