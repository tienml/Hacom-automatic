package com.hacom.bbnt.dto;

import java.time.Instant;
import java.util.List;

public record AnalyzeResponse(
        String jobId,
        String fileName,
        String dmSheetName,
        ProjectSummary project,
        List<String> analysisWarnings,
        int workItemCount,
        int outputSheetCount,
        int withSampleCount,
        int withoutSampleCount,
        int existingSheetCount,
        int cloneTemplateCount,
        int mainOnlyCount,
        int completeSamplePairCount,
        int partialSamplePairCount,
        int unknownMaterialCount,
        List<WorkItemDto> workItems,
        Instant createdAt,
        Instant expiresAt
) {
    public AnalyzeResponse {
        analysisWarnings = analysisWarnings == null ? List.of() : List.copyOf(analysisWarnings);
    }
}
