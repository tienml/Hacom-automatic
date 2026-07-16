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
        List<WorkItemDto> workItems,
        Map<Integer, List<String>> outputSheets,
        Instant createdAt,
        Instant expiresAt
) {
}
