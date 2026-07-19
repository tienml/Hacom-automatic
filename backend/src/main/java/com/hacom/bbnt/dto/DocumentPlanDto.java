package com.hacom.bbnt.dto;

import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;

import java.util.List;

public record DocumentPlanDto(
        DocumentType documentType,
        OutputAvailability availability,
        GenerationMode generationMode,
        String existingSheetName,
        String plannedSheetName,
        MaterialFamily materialFamily,
        String sourceTemplate,
        List<String> availableSourceTemplates,
        List<FieldDecisionDto> fieldDecisions,
        List<String> warnings
) {
    public DocumentPlanDto {
        availableSourceTemplates = availableSourceTemplates == null ? List.of() : List.copyOf(availableSourceTemplates);
        fieldDecisions = fieldDecisions == null ? List.of() : List.copyOf(fieldDecisions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
