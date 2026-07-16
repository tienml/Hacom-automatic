package com.hacom.bbnt.controller;

import com.hacom.bbnt.dto.*;
import com.hacom.bbnt.model.JobContext;
import com.hacom.bbnt.service.DocumentGenerationService;
import com.hacom.bbnt.service.ExcelAnalysisService;
import com.hacom.bbnt.service.OutputSheetService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
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
        return new AnalyzeResponse(
                context.id(),
                context.originalFileName(),
                context.dmSheetName(),
                context.project(),
                context.workItems().size(),
                outputSheetCount,
                withSampleCount,
                context.workItems().size() - withSampleCount,
                context.workItems(),
                context.createdAt(),
                context.expiresAt()
        );
    }

    @GetMapping("/{jobId}/work-items/{number}/outputs")
    public List<OutputSheetDto> outputs(@PathVariable String jobId, @PathVariable int number) {
        return outputSheetService.outputs(jobId, number);
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
                document.workItemNumber(),
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
