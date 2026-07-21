package com.hacom.bbnt.dto;

import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.WorkItemSheetStatus;

import java.util.List;

public record WorkItemDto(
        String itemNumber,
        String localOrder,
        String content,
        String position,
        String majorCategory,
        String inspectionTime,
        String recordNumber,
        String sampleDate,
        int excelRow,
        boolean hasOutputSheets,
        List<String> existingSheetNames,
        boolean hasMainSheet,
        boolean hasLmSheet,
        boolean hasGmSheet,
        boolean hasCompleteSamplePair,
        boolean hasPartialSamplePair,
        WorkItemSheetStatus sheetStatus,
        GenerationMode generationMode,
        MaterialFamily materialFamily,
        String detectionReason,
        TemplatePairDto templatePair,
        List<TemplatePairDto> availableTemplatePairs,
        boolean requiresTemplateSelection,
        DocumentPlanDto mainPlan,
        DocumentPlanDto lmPlan,
        DocumentPlanDto gmPlan,
        List<FieldDecisionDto> fieldDecisions,
        List<String> autoFilledFields,
        List<String> blankFields,
        List<String> warnings
) {
    public WorkItemDto {
        existingSheetNames = existingSheetNames == null ? List.of() : List.copyOf(existingSheetNames);
        availableTemplatePairs = availableTemplatePairs == null ? List.of() : List.copyOf(availableTemplatePairs);
        fieldDecisions = fieldDecisions == null ? List.of() : List.copyOf(fieldDecisions);
        autoFilledFields = autoFilledFields == null ? List.of() : List.copyOf(autoFilledFields);
        blankFields = blankFields == null ? List.of() : List.copyOf(blankFields);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public String number() {
        return itemNumber;
    }
}
