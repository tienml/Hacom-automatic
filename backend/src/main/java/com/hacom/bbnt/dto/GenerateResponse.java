package com.hacom.bbnt.dto;

import java.time.Instant;
import java.util.List;

public record GenerateResponse(
        String documentId,
        List<String> workItemNumbers,
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
        List<String> warnings,
        Instant createdAt,
        Instant expiresAt
) {
}
