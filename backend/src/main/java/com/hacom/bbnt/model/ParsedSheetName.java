package com.hacom.bbnt.model;

public record ParsedSheetName(
        String originalSheetName,
        DocumentType documentType,
        MaterialFamily materialFamily,
        String itemNumber,
        boolean mainSheet
) {
}
