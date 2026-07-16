package com.hacom.bbnt.controller;

import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.GeneratedDocument;
import com.hacom.bbnt.service.TemporaryStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final TemporaryStore store;

    public DocumentController(TemporaryStore store) {
        this.store = store;
    }

    @GetMapping("/{documentId}/excel")
    public ResponseEntity<Resource> excel(@PathVariable String documentId) {
        GeneratedDocument document = store.getDocument(documentId);
        return fileResponse(document.excelPath(), MediaType.APPLICATION_OCTET_STREAM, "attachment");
    }

    @GetMapping("/{documentId}/pdf")
    public ResponseEntity<Resource> pdf(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "inline") String disposition
    ) {
        GeneratedDocument document = store.getDocument(documentId);
        if (!document.hasPdf()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tài liệu chưa có bản PDF.");
        }
        String safeDisposition = disposition.equalsIgnoreCase("attachment") ? "attachment" : "inline";
        return fileResponse(document.pdfPath(), MediaType.APPLICATION_PDF, safeDisposition);
    }

    private ResponseEntity<Resource> fileResponse(Path path, MediaType mediaType, String disposition) {
        if (path == null || !Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File kết quả không còn tồn tại.");
        }
        ContentDisposition contentDisposition = ContentDisposition.builder(disposition)
                .filename(path.getFileName().toString(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new FileSystemResource(path));
    }
}
