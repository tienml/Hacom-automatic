package com.hacom.bbnt.model;

import java.util.List;

public record TemplatePair(
        MaterialFamily materialFamily,
        String lmSheetName,
        String gmSheetName,
        String reason,
        boolean profileCompatible,
        int mergedRegionCount,
        int drawingCount,
        boolean hasPrintArea,
        List<String> validationWarnings
) {
    public TemplatePair {
        validationWarnings = validationWarnings == null ? List.of() : List.copyOf(validationWarnings);
    }

    public boolean complete() {
        return lmSheetName != null && !lmSheetName.isBlank()
                && gmSheetName != null && !gmSheetName.isBlank();
    }

    public boolean usable() {
        return complete() && profileCompatible && hasPrintArea;
    }
}
