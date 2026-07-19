package com.hacom.bbnt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateRequest(
        @NotNull @NotEmpty List<@Valid GenerateSelection> selections,
        boolean createPdf
) {
}
