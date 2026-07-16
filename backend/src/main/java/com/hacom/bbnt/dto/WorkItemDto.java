package com.hacom.bbnt.dto;

public record WorkItemDto(
        int number,
        String localOrder,
        String content,
        String position,
        String inspectionTime,
        String recordNumber,
        String sampleDate,
        int excelRow,
        boolean hasOutputSheets
) {
}
