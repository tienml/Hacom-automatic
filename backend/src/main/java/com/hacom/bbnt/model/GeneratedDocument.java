package com.hacom.bbnt.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record GeneratedDocument(
        String id,
        String jobId,
        List<String> workItemNumbers,
        List<String> selectedSheets,
        Path excelPath,
        Path pdfPath,
        List<String> warnings,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean hasPdf() {
        return pdfPath != null;
    }
}
