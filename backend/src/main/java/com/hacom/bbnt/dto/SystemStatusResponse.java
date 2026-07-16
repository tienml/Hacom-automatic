package com.hacom.bbnt.dto;

public record SystemStatusResponse(
        String application,
        String version,
        String configuredPdfMode,
        String activePdfEngine,
        boolean pdfAvailable,
        String message
) {
}
