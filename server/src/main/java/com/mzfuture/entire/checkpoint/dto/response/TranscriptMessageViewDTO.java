package com.mzfuture.entire.checkpoint.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/// One user or assistant turn in normalized form.
///
/// NON_NULL so empty `tools` / `diffs` arrays are still emitted (NON_EMPTY would omit them).
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Single transcript message for UI")
public class TranscriptMessageViewDTO {

    private String id;

    @Schema(description = "user or assistant")
    private String role;

    @Schema(description = "Visible text (merged text parts)")
    private String text;

    @Schema(description = "Task title when provided (OpenCode)")
    private String taskTitle;

    @Schema(description = "Reasoning block text when present (OpenCode)")
    private String reasoning;

    @Schema(description = "Creation time in epoch ms when present")
    private Long createdAt;

    private List<TranscriptToolUseDTO> tools;

    @Schema(description = "Number of tools in this message")
    private Integer toolsCount;

    @Schema(description = "File diffs on this message when present")
    private List<TranscriptFileDiffDTO> diffs;
}
