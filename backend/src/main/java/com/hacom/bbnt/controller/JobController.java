package com.hacom.bbnt.controller;

import com.hacom.bbnt.dto.AnalyzeResponse;
import com.hacom.bbnt.dto.GenerateRequest;
import com.hacom.bbnt.dto.GenerateResponse;
import com.hacom.bbnt.dto.OutputSheetDto;
import com.hacom.bbnt.model.JobContext;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.service.DocumentGenerationService;
import com.hacom.bbnt.service.ExcelAnalysisService;
import com.hacom.bbnt.service.OutputSheetService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final ExcelAnalysisService analysisService;
    private final OutputSheetService outputSheetService;
    private final DocumentGenerationService generationService;

    public JobController(
            ExcelAnalysisService analysisService,
            OutputSheetService outputSheetService,
            DocumentGenerationService generationService
    ) {
        this.analysisService = analysisService;
        this.outputSheetService = outputSheetService;
        this.generationService = generationService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyze(@RequestPart("file") MultipartFile file) {
        JobContext context = analysisService.analyze(file);
        int outputSheetCount = context.outputSheets().values().stream().mapToInt(List::size).sum();
        int withSampleCount = (int) context.workItems().stream()
                .filter(item -> item.sampleDate() != null && !item.sampleDate().isBlank())
                .count();
        int existingCount = (int) context.workItems().stream()
                .filter(item -> item.hasOutputSheets())
                .count();
        int cloneCount = (int) context.workItems().stream()
                .filter(item -> (item.lmPlan() != null && item.lmPlan().generationMode() == com.hacom.bbnt.model.GenerationMode.CLONE_TEMPLATE)
                        || (item.gmPlan() != null && item.gmPlan().generationMode() == com.hacom.bbnt.model.GenerationMode.CLONE_TEMPLATE))
                .count();
        int mainOnlyCount = (int) context.workItems().stream().filter(item -> item.sheetStatus() == com.hacom.bbnt.model.WorkItemSheetStatus.MAIN_ONLY).count();
        int completePairCount = (int) context.workItems().stream().filter(item -> item.hasCompleteSamplePair()).count();
        int partialPairCount = (int) context.workItems().stream().filter(item -> item.hasPartialSamplePair()).count();
        int unknownCount = (int) context.workItems().stream()
                .filter(item -> item.materialFamily() == MaterialFamily.UNKNOWN)
                .count();
        return new AnalyzeResponse(
                context.id(),
                context.originalFileName(),
                context.dmSheetName(),
                context.project(),
                context.analysisWarnings(),
                context.workItems().size(),
                outputSheetCount,
                withSampleCount,
                context.workItems().size() - withSampleCount,
                existingCount,
                cloneCount,
                mainOnlyCount,
                completePairCount,
                partialPairCount,
                unknownCount,
                context.workItems(),
                context.createdAt(),
                context.expiresAt()
        );
    }

    @GetMapping("/{jobId}/work-items/{itemNumber}/outputs")
    public List<OutputSheetDto> outputs(
            @PathVariable String jobId,
            @PathVariable String itemNumber,
            @RequestParam(required = false) MaterialFamily materialFamily,
            @RequestParam(required = false) String lmTemplateSheet,
            @RequestParam(required = false) String gmTemplateSheet
    ) {
        return outputSheetService.outputs(jobId, itemNumber, materialFamily, lmTemplateSheet, gmTemplateSheet);
    }

    @PostMapping("/{jobId}/generate")
    public GenerateResponse generate(@PathVariable String jobId, @Valid @RequestBody GenerateRequest request) {
        var result = generationService.generate(jobId, request);
        var document = result.document();
        String base = "/api/v1/documents/" + document.id();
        long excelSize = safeSize(document.excelPath());
        long pdfSize = document.hasPdf() ? safeSize(document.pdfPath()) : 0L;
        return new GenerateResponse(
                document.id(),
                document.workItemNumbers(),
                document.selectedSheets(),
                base + "/excel",
                document.hasPdf() ? base + "/pdf?disposition=inline" : null,
                document.hasPdf() ? base + "/pdf?disposition=attachment" : null,
                document.hasPdf(),
                result.pdfMessage(),
                document.excelPath().getFileName().toString(),
                document.hasPdf() ? document.pdfPath().getFileName().toString() : null,
                excelSize,
                pdfSize,
                document.warnings(),
                document.createdAt(),
                document.expiresAt()
        );
    }

    private long safeSize(java.nio.file.Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
