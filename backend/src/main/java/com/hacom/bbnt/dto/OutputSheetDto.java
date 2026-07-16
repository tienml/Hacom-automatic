package com.hacom.bbnt.dto;

public record OutputSheetDto(
        String sheetName,
        String displayName,
        String type,
        String description,
        boolean available
) {
}
