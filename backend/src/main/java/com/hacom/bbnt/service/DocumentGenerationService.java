package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.GenerateRequest;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.GeneratedDocument;
import com.hacom.bbnt.model.JobContext;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentGenerationService {
    private final TemporaryStore store;
    private final PdfConversionService pdfConversionService;

    public DocumentGenerationService(TemporaryStore store, PdfConversionService pdfConversionService) {
        this.store = store;
        this.pdfConversionService = pdfConversionService;
    }

    public GenerationResult generate(String jobId, GenerateRequest request) {
        JobContext job = store.getJob(jobId);
        List<String> available = job.outputSheets().getOrDefault(request.workItemNumber(), List.of());
        if (available.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Số danh mục này chưa có sheet biểu mẫu tương ứng trong file BBNT.");
        }

        Set<String> selected = new HashSet<>(request.selectedSheets());
        if (selected.isEmpty() || !available.containsAll(selected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Danh sách sheet được chọn không hợp lệ đối với số danh mục " + request.workItemNumber() + ".");
        }

        String documentId = store.newId();
        Instant now = Instant.now();
        try {
            Path directory = store.createDocumentDirectory(documentId);
            String extension = extensionOf(job.sourcePath());
            Path excelPath = directory.resolve(safeFileName("Ho_so_DM_" + request.workItemNumber()) + extension);
            createEditableWorkbook(job.sourcePath(), excelPath, selected, request.workItemNumber());

            Path pdfPath = null;
            String pdfMessage = null;
            if (request.createPdf()) {
                Path printSource = directory.resolve("print_source" + extension);
                try {
                    createPrintWorkbook(excelPath, printSource, selected);
                    pdfPath = pdfConversionService.convert(
                            printSource,
                            directory.resolve(safeFileName("Ho_so_DM_" + request.workItemNumber()) + ".pdf")
                    );
                } catch (RuntimeException exception) {
                    pdfMessage = exception.getMessage();
                } finally {
                    Files.deleteIfExists(printSource);
                }
            }

            GeneratedDocument document = new GeneratedDocument(
                    documentId,
                    jobId,
                    request.workItemNumber(),
                    List.copyOf(request.selectedSheets()),
                    excelPath,
                    pdfPath,
                    now,
                    now.plus(store.ttl())
            );
            store.saveDocument(document);
            return new GenerationResult(document, pdfMessage);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không tạo được file kết quả: " + exception.getMessage());
        }
    }

    /**
     * File Excel tải về vẫn giữ toàn bộ sheet phụ thuộc để công thức hoạt động,
     * nhưng chỉ các biểu mẫu người dùng chọn được hiển thị.
     */
    private void createEditableWorkbook(
            Path source,
            Path target,
            Set<String> selectedSheets,
            int workItemNumber
    ) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(source.toFile())) {
            int firstSelected = -1;
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                boolean selected = selectedSheets.contains(sheet.getSheetName());
                workbook.setSheetHidden(index, !selected);
                sheet.setSelected(selected);
                if (selected) {
                    setReferenceNumber(sheet, workItemNumber);
                    if (firstSelected < 0) firstSelected = index;
                }
            }
            if (firstSelected < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Không có sheet hợp lệ được chọn.");
            }
            workbook.setActiveSheet(firstSelected);
            workbook.setSelectedTab(firstSelected);
            workbook.setForceFormulaRecalculation(true);
            writeWorkbook(workbook, target);
        }
    }

    /**
     * LibreOffice có thể in cả các sheet ẩn của workbook .xls.
     * Vì vậy bản dành riêng cho PDF sẽ đóng băng giá trị công thức trên các sheet đã chọn
     * rồi xóa toàn bộ sheet khác. PDF chỉ còn đúng các trang người dùng yêu cầu.
     */
    private void createPrintWorkbook(Path source, Path target, Set<String> selectedSheets) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(source.toFile())) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (String sheetName : selectedSheets) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet != null) flattenFormulas(sheet, evaluator);
            }

            for (int index = workbook.getNumberOfSheets() - 1; index >= 0; index--) {
                String sheetName = workbook.getSheetName(index);
                if (!selectedSheets.contains(sheetName)) workbook.removeSheetAt(index);
            }
            if (workbook.getNumberOfSheets() == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Không còn sheet để tạo PDF.");
            }
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                workbook.setSheetHidden(index, false);
                workbook.getSheetAt(index).setSelected(index == 0);
            }
            workbook.setActiveSheet(0);
            workbook.setSelectedTab(0);
            writeWorkbook(workbook, target);
        }
    }

    private void flattenFormulas(Sheet sheet, FormulaEvaluator evaluator) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() != CellType.FORMULA) continue;
                EvaluatedValue value = evaluate(cell, evaluator);
                applyValue(cell, value);
            }
        }
    }

    private EvaluatedValue evaluate(Cell cell, FormulaEvaluator evaluator) {
        try {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated != null) {
                return switch (evaluated.getCellType()) {
                    case STRING -> new EvaluatedValue(CellType.STRING, evaluated.getStringValue(), 0, false, (byte) 0);
                    case NUMERIC -> new EvaluatedValue(CellType.NUMERIC, null, evaluated.getNumberValue(), false, (byte) 0);
                    case BOOLEAN -> new EvaluatedValue(CellType.BOOLEAN, null, 0, evaluated.getBooleanValue(), (byte) 0);
                    case ERROR -> new EvaluatedValue(CellType.ERROR, null, 0, false, evaluated.getErrorValue());
                    default -> EvaluatedValue.blank();
                };
            }
        } catch (RuntimeException ignored) {
            // Một số công thức cũ có external link/#REF nhưng vẫn có cached value trong file.
        }

        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> new EvaluatedValue(CellType.STRING, cell.getStringCellValue(), 0, false, (byte) 0);
                case NUMERIC -> new EvaluatedValue(CellType.NUMERIC, null, cell.getNumericCellValue(), false, (byte) 0);
                case BOOLEAN -> new EvaluatedValue(CellType.BOOLEAN, null, 0, cell.getBooleanCellValue(), (byte) 0);
                case ERROR -> new EvaluatedValue(CellType.ERROR, null, 0, false, cell.getErrorCellValue());
                default -> EvaluatedValue.blank();
            };
        } catch (RuntimeException ignored) {
            return EvaluatedValue.blank();
        }
    }

    private void applyValue(Cell cell, EvaluatedValue value) {
        cell.setBlank();
        switch (value.type()) {
            case STRING -> cell.setCellValue(value.text());
            case NUMERIC -> cell.setCellValue(value.number());
            case BOOLEAN -> cell.setCellValue(value.bool());
            case ERROR -> cell.setCellErrorValue(value.error());
            default -> cell.setBlank();
        }
    }

    /**
     * Trong file BBNT hiện tại, P1 là ô tham chiếu nằm ngoài vùng in.
     */
    private void setReferenceNumber(Sheet sheet, int workItemNumber) {
        Row row = sheet.getRow(0);
        if (row == null) row = sheet.createRow(0);
        Cell cell = row.getCell(15); // P1
        if (cell == null) cell = row.createCell(15);
        cell.setCellValue(workItemNumber);
    }

    private void writeWorkbook(Workbook workbook, Path target) throws IOException {
        try (OutputStream output = Files.newOutputStream(target)) {
            workbook.write(output);
        }
    }

    private String extensionOf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx") ? ".xlsx" : ".xls";
    }

    private String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record GenerationResult(GeneratedDocument document, String pdfMessage) {
    }

    private record EvaluatedValue(CellType type, String text, double number, boolean bool, byte error) {
        static EvaluatedValue blank() {
            return new EvaluatedValue(CellType.BLANK, null, 0, false, (byte) 0);
        }
    }
}
