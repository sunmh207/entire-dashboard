package com.mzfuture.entire.checkpoint.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// Tool invocation aligned with the admin UI transcript viewer.
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Tool use in a transcript message")
public class TranscriptToolUseDTO {

    @Schema(description = "Tool call id when present")
    private String callID;

    @Schema(description = "Tool name")
    private String tool;

    @Schema(description = "Tool input payload")
    private JsonNode input;

    @Schema(description = "Tool output when present")
    private JsonNode output;

    @Schema(description = "Optional short title")
    private String title;
}
