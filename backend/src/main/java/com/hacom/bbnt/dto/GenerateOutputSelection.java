package com.hacom.bbnt.dto;

import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GenerationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateOutputSelection(
        @NotBlank String sheetName,
        @NotNull DocumentType documentType,
        @NotNull GenerationMode generationMode,
        String sourceTemplate
) {
}
