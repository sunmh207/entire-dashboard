package com.mzfuture.entire.checkpoint.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// File diff attached to a single message (OpenCode).
@Data
@Schema(description = "File diff snapshot for one message")
public class TranscriptFileDiffDTO {

    private String file;
    private String before;
    private String after;

    @Schema(description = "Line additions count")
    private Integer additions;

    @Schema(description = "Line deletions count")
    private Integer deletions;
}
