package com.hacom.bbnt.dto;

import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;

import java.util.List;

public record OutputSheetDto(
        String sheetName,
        String displayName,
        String type,
        DocumentType documentType,
        String description,
        boolean available,
        boolean generated,
        String sourceTemplate,
        List<String> availableSourceTemplates,
        GenerationMode generationMode,
        OutputAvailability availability,
        MaterialFamily materialFamily,
        List<FieldDecisionDto> fieldDecisions,
        List<String> warnings
) {
    public OutputSheetDto {
        availableSourceTemplates = availableSourceTemplates == null ? List.of() : List.copyOf(availableSourceTemplates);
        fieldDecisions = fieldDecisions == null ? List.of() : List.copyOf(fieldDecisions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
