package com.hacom.bbnt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class PdfConversionService {
    private final String configuredMode;
    private final String gotenbergBaseUrl;
    private final String libreOfficeCommand;
    private final Duration timeout;
    private final RestClient restClient;

    public PdfConversionService(
            RestClient.Builder builder,
            @Value("${app.pdf.mode:auto}") String configuredMode,
            @Value("${app.pdf.gotenberg-base-url:http://localhost:3000}") String gotenbergBaseUrl,
            @Value("${app.pdf.libreoffice-command:soffice}") String libreOfficeCommand,
            @Value("${app.pdf.timeout-seconds:120}") long timeoutSeconds
    ) {
        this.configuredMode = normalizeMode(configuredMode);
        this.gotenbergBaseUrl = gotenbergBaseUrl.replaceAll("/+$", "");
        this.libreOfficeCommand = libreOfficeCommand;
        this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(this.timeout);
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(this.gotenbergBaseUrl)
                .build();
    }

    public Path convert(Path officeFile, Path targetPdf) {
        return switch (configuredMode) {
            case "gotenberg" -> convertWithGotenberg(officeFile, targetPdf);
            case "libreoffice" -> convertWithLibreOffice(officeFile, targetPdf);
            case "disabled" -> throw new IllegalStateException(
                    "Chức năng PDF đang tắt. Đặt PDF_MODE=gotenberg hoặc PDF_MODE=libreoffice.");
            default -> convertAutomatically(officeFile, targetPdf);
        };
    }

    public PdfStatus status() {
        if ("disabled".equals(configuredMode)) {
            return new PdfStatus(configuredMode, "disabled", false, "PDF đang tắt trong cấu hình.");
        }
        if (("auto".equals(configuredMode) || "gotenberg".equals(configuredMode)) && isGotenbergAvailable()) {
            return new PdfStatus(configuredMode, "gotenberg", true, "Gotenberg sẵn sàng.");
        }
        if (("auto".equals(configuredMode) || "libreoffice".equals(configuredMode)) && isLibreOfficeAvailable()) {
            return new PdfStatus(configuredMode, "libreoffice", true, "LibreOffice cục bộ sẵn sàng.");
        }
        return new PdfStatus(
                configuredMode,
                "none",
                false,
                "Chưa tìm thấy Gotenberg hoặc LibreOffice. Excel vẫn xuất được, nhưng chưa preview PDF."
        );
    }

    private Path convertAutomatically(Path officeFile, Path targetPdf) {
        RuntimeException gotenbergError = null;
        try {
            return convertWithGotenberg(officeFile, targetPdf);
        } catch (RuntimeException exception) {
            gotenbergError = exception;
        }
        try {
            return convertWithLibreOffice(officeFile, targetPdf);
        } catch (RuntimeException libreOfficeError) {
            throw new IllegalStateException(
                    "Không tạo được PDF. Gotenberg: " + rootMessage(gotenbergError)
                            + "; LibreOffice: " + rootMessage(libreOfficeError)
            );
        }
    }

    private Path convertWithGotenberg(Path officeFile, Path targetPdf) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new FileSystemResource(officeFile));
            byte[] pdf = restClient.post()
                    .uri("/forms/libreoffice/convert")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Gotenberg-Output-Filename", stripExtension(officeFile.getFileName().toString()))
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            if (pdf == null || pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P') {
                throw new IllegalStateException("Gotenberg trả về dữ liệu PDF không hợp lệ.");
            }
            Files.write(targetPdf, pdf);
            return targetPdf;
        } catch (Exception exception) {
            throw new IllegalStateException("Không chuyển được PDF qua Gotenberg: " + rootMessage(exception), exception);
        }
    }

    private Path convertWithLibreOffice(Path officeFile, Path targetPdf) {
        Path outputDirectory = targetPdf.getParent().resolve("libreoffice-output");
        try {
            Files.createDirectories(outputDirectory);
            Process process = new ProcessBuilder(
                    libreOfficeCommand,
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", outputDirectory.toString(),
                    officeFile.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            ByteArrayOutputStream log = new ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> copy(process.getInputStream(), log));
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("LibreOffice vượt quá thời gian xử lý " + timeout.toSeconds() + " giây.");
            }
            reader.join(Duration.ofSeconds(2));
            if (process.exitValue() != 0) {
                throw new IllegalStateException("LibreOffice lỗi: " + log.toString(StandardCharsets.UTF_8));
            }

            Path generated = findGeneratedPdf(outputDirectory, stripExtension(officeFile.getFileName().toString()));
            Files.move(generated, targetPdf, StandardCopyOption.REPLACE_EXISTING);
            deleteQuietly(outputDirectory);
            return targetPdf;
        } catch (Exception exception) {
            deleteQuietly(outputDirectory);
            throw new IllegalStateException("Không chuyển được PDF bằng LibreOffice: " + rootMessage(exception), exception);
        }
    }

    private Path findGeneratedPdf(Path directory, String expectedBaseName) throws IOException {
        Path expected = directory.resolve(expectedBaseName + ".pdf");
        if (Files.exists(expected)) return expected;
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("LibreOffice không tạo ra file PDF."));
        }
    }

    private boolean isGotenbergAvailable() {
        try {
            String body = restClient.get().uri("/health").retrieve().body(String.class);
            return body != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLibreOfficeAvailable() {
        try {
            Process process = new ProcessBuilder(libreOfficeCommand, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            return finished && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "gotenberg", "libreoffice", "disabled" -> normalized;
            default -> "auto";
        };
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) return "không xác định";
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void copy(InputStream input, ByteArrayOutputStream output) {
        try (input; output) {
            input.transferTo(output);
        } catch (IOException ignored) {
        }
    }

    private void deleteQuietly(Path directory) {
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

    public record PdfStatus(
            String configuredMode,
            String activeEngine,
            boolean available,
            String message
    ) {
    }
}
