package com.hacom.bbnt.dto;

import com.hacom.bbnt.model.MaterialFamily;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateSelection(
        @NotBlank String itemNumber,
        @NotNull @NotEmpty List<@Valid GenerateOutputSelection> outputs,
        MaterialFamily materialFamily
) {
}
