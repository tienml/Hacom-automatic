package com.hacom.bbnt.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workbook-specific field map resolved and validated from one LM/GM template.
 * Fixed addresses are kept here (not in clone logic) and may only be used after
 * the surrounding labels/structure have been verified by TemplateProfileService.
 */
public record TemplateProfile(
        MaterialFamily materialFamily,
        DocumentType documentType,
        String sheetName,
        Map<String, List<String>> targetCells,
        List<String> variableCells,
        List<String> uncertainCells,
        List<String> uncertainRanges,
        int dynamicTableStartRow,
        List<String> validationMarkers,
        List<String> warnings
) {
    public TemplateProfile {
        Map<String, List<String>> cellsCopy = new LinkedHashMap<>();
        if (targetCells != null) {
            targetCells.forEach((key, value) -> cellsCopy.put(key, value == null ? List.of() : List.copyOf(value)));
        }
        targetCells = Map.copyOf(cellsCopy);
        variableCells = variableCells == null ? List.of() : List.copyOf(variableCells);
        uncertainCells = uncertainCells == null ? List.of() : List.copyOf(uncertainCells);
        uncertainRanges = uncertainRanges == null ? List.of() : List.copyOf(uncertainRanges);
        validationMarkers = validationMarkers == null ? List.of() : List.copyOf(validationMarkers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public List<String> targets(String fieldName) {
        return targetCells.getOrDefault(fieldName, List.of());
    }
}
