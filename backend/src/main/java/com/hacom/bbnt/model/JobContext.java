package com.hacom.bbnt.model;

import com.hacom.bbnt.dto.ProjectSummary;
import com.hacom.bbnt.dto.WorkItemDto;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record JobContext(
        String id,
        String originalFileName,
        Path sourcePath,
        String dmSheetName,
        ProjectSummary project,
        List<String> analysisWarnings,
        List<WorkItemDto> workItems,
        Map<String, List<String>> outputSheets,
        TemplateRegistry templateRegistry,
        Instant createdAt,
        Instant expiresAt
) {
    public JobContext {
        analysisWarnings = analysisWarnings == null ? List.of() : List.copyOf(analysisWarnings);
    }

    public WorkItemDto workItem(String itemNumber) {
        return workItems.stream()
                .filter(item -> item.itemNumber().equalsIgnoreCase(itemNumber))
                .findFirst()
                .orElse(null);
    }
}
