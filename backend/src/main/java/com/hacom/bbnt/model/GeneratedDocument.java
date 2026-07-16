package com.hacom.bbnt.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record GeneratedDocument(
        String id,
        String jobId,
        int workItemNumber,
        List<String> selectedSheets,
        Path excelPath,
        Path pdfPath,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean hasPdf() {
        return pdfPath != null;
    }
}
