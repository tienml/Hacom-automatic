package com.hacom.bbnt.dto;

import java.util.List;

public record TemplatePairDto(
        String lm,
        String gm,
        String reason,
        boolean recommended,
        boolean profileCompatible,
        int mergedRegionCount,
        int drawingCount,
        boolean hasPrintArea,
        List<String> validationWarnings
) {
    public TemplatePairDto {
        validationWarnings = validationWarnings == null ? List.of() : List.copyOf(validationWarnings);
    }
}
