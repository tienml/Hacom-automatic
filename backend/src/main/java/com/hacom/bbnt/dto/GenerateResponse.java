package com.hacom.bbnt.dto;

import java.time.Instant;
import java.util.List;

public record GenerateResponse(
        String documentId,
        int workItemNumber,
        List<String> selectedSheets,
        String excelDownloadUrl,
        String pdfPreviewUrl,
        String pdfDownloadUrl,
        boolean pdfAvailable,
        String pdfMessage,
        String excelFileName,
        String pdfFileName,
        long excelSize,
        long pdfSize,
        Instant createdAt,
        Instant expiresAt
) {
}
