package com.hacom.bbnt.service;

import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.GeneratedDocument;
import com.hacom.bbnt.model.JobContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemporaryStore {
    private final Map<String, JobContext> jobs = new ConcurrentHashMap<>();
    private final Map<String, GeneratedDocument> documents = new ConcurrentHashMap<>();
    private final Path root;
    private final Duration ttl;

    public TemporaryStore(
            @Value("${app.storage.root:${java.io.tmpdir}/hacom-bbnt-v1}") String root,
            @Value("${app.storage.ttl-minutes:60}") long ttlMinutes
    ) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        Files.createDirectories(this.root);
    }

    public Duration ttl() {
        return ttl;
    }

    public Path createJobDirectory(String jobId) throws IOException {
        Path directory = root.resolve("jobs").resolve(jobId).normalize();
        ensureInsideRoot(directory);
        Files.createDirectories(directory);
        return directory;
    }

    public Path createDocumentDirectory(String documentId) throws IOException {
        Path directory = root.resolve("documents").resolve(documentId).normalize();
        ensureInsideRoot(directory);
        Files.createDirectories(directory);
        return directory;
    }

    public String newId() {
        return UUID.randomUUID().toString();
    }

    public void saveJob(JobContext context) {
        jobs.put(context.id(), context);
    }

    public JobContext getJob(String id) {
        JobContext job = jobs.get(id);
        if (job == null || job.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Phiên xử lý không tồn tại hoặc đã hết hạn. Vui lòng tải file lại.");
        }
        return job;
    }

    public void saveDocument(GeneratedDocument document) {
        documents.put(document.id(), document);
    }

    public GeneratedDocument getDocument(String id) {
        GeneratedDocument document = documents.get(id);
        if (document == null || document.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tài liệu không tồn tại hoặc đã hết hạn.");
        }
        return document;
    }

    @Scheduled(fixedDelayString = "${app.storage.cleanup-delay-ms:600000}")
    public void cleanup() {
        Instant now = Instant.now();
        jobs.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isAfter(now)) return false;
            deleteRecursively(entry.getValue().sourcePath().getParent());
            return true;
        });
        documents.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isAfter(now)) return false;
            deleteRecursively(entry.getValue().excelPath().getParent());
            return true;
        });
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn tạm không hợp lệ.");
        }
    }
}
