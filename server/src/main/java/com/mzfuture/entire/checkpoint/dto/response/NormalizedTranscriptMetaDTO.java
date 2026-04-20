package com.mzfuture.entire.checkpoint.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/// Metadata for a normalized transcript response (source format, size, parse notes).
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Normalized transcript metadata")
public class NormalizedTranscriptMetaDTO {

    @Schema(description = "Detected source format", example = "cursor_ndjson")
    private String sourceFormat;

    @Schema(description = "UTF-8 byte length of raw transcript before parsing")
    private Long rawBytesLength;

    @Schema(description = "Non-fatal parse notes or partial-failure hints")
    private List<String> warnings;
}
