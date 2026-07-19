package com.hacom.bbnt.dto;

import com.hacom.bbnt.model.DataCertainty;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.FieldAction;

import java.util.List;

public record FieldDecisionDto(
        String fieldName,
        String source,
        String value,
        DataCertainty certainty,
        FieldAction action,
        List<String> targetCells,
        List<String> targetRanges,
        DocumentType documentType,
        String reason
) {
    public FieldDecisionDto {
        targetCells = targetCells == null ? List.of() : List.copyOf(targetCells);
        targetRanges = targetRanges == null ? List.of() : List.copyOf(targetRanges);
    }
}
