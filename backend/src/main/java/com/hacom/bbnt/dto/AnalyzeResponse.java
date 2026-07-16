package com.hacom.bbnt.dto;

import java.time.Instant;
import java.util.List;

public record AnalyzeResponse(
        String jobId,
        String fileName,
        String dmSheetName,
        ProjectSummary project,
        int workItemCount,
        int outputSheetCount,
        int withSampleCount,
        int withoutSampleCount,
        List<WorkItemDto> workItems,
        Instant createdAt,
        Instant expiresAt
) {
}
