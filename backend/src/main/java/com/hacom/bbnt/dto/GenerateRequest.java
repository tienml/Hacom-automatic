package com.hacom.bbnt.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record GenerateRequest(
        @Positive int workItemNumber,
        @NotNull @NotEmpty List<String> selectedSheets,
        boolean createPdf
) {
}
