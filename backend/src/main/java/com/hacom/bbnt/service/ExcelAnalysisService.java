package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.ProjectSummary;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.JobContext;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ExcelAnalysisService {
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final Pattern INTEGER_TEXT = Pattern.compile("^\\d+$");

    private final TemporaryStore store;

    public ExcelAnalysisService(TemporaryStore store) {
        this.store = store;
    }

    public JobContext analyze(MultipartFile file) {
        validateFile(file);
        String jobId = store.newId();
        Instant now = Instant.now();

        try {
            Path jobDirectory = store.createJobDirectory(jobId);
            String extension = extensionOf(Objects.requireNonNullElse(file.getOriginalFilename(), "workbook.xls"));
            Path sourcePath = jobDirectory.resolve("source" + extension);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, sourcePath, StandardCopyOption.REPLACE_EXISTING);
            }

            try (Workbook workbook = WorkbookFactory.create(sourcePath.toFile())) {
                Sheet dmSheet = findDmSheet(workbook);
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
                ProjectSummary project = readProjectSummary(dmSheet, formatter, evaluator);
                Map<Integer, List<String>> outputSheets = findOutputSheets(workbook);
                List<WorkItemDto> items = readWorkItems(dmSheet, formatter, evaluator, outputSheets);

                if (items.isEmpty()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Đã tìm thấy sheet DM nhưng không đọc được dòng công việc nào ở cột A-G.");
                }

                JobContext context = new JobContext(
                        jobId,
                        Objects.requireNonNullElse(file.getOriginalFilename(), sourcePath.getFileName().toString()),
                        sourcePath,
                        dmSheet.getSheetName(),
                        project,
                        List.copyOf(items),
                        Map.copyOf(outputSheets),
                        now,
                        now.plus(store.ttl())
                );
                store.saveJob(context);
                return context;
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không đọc được workbook BBNT: " + exception.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn file Excel BBNT.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "V1 chỉ nhận file tối đa 25 MB.");
        }
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xls") || name.endsWith(".xlsx"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ file .xls hoặc .xlsx.");
        }
    }

    private Sheet findDmSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            if (sheet.getSheetName().trim().equalsIgnoreCase("DM")) {
                return sheet;
            }
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Không tìm thấy sheet DM trong workbook.");
    }

    private ProjectSummary readProjectSummary(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        String project = null;
        String location = null;
        String packageName = null;
        String contractor = null;

        int maxRow = Math.min(sheet.getLastRowNum(), 20);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String text = cellText(row.getCell(1), formatter, evaluator).trim();
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("dự án")) project = afterColon(text);
            else if (lower.startsWith("địa điểm")) location = afterColon(text);
            else if (lower.startsWith("gói thầu")) packageName = afterColon(text);
            else if (lower.startsWith("nhà thầu")) contractor = afterColon(text);
        }
        return new ProjectSummary(project, location, packageName, contractor);
    }

    private List<WorkItemDto> readWorkItems(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            Map<Integer, List<String>> outputs
    ) {
        List<WorkItemDto> items = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Integer number = integerValue(row.getCell(0), formatter, evaluator);
            if (number == null || number <= 0 || !seen.add(number)) continue;

            String content = cellText(row.getCell(2), formatter, evaluator).trim();
            String position = cellText(row.getCell(3), formatter, evaluator).trim();
            if (content.isBlank() && position.isBlank()) continue;

            String localOrder = cellText(row.getCell(1), formatter, evaluator).trim();
            String inspectionTime = cellText(row.getCell(4), formatter, evaluator).trim();
            String recordNumber = cellText(row.getCell(5), formatter, evaluator).trim();
            String sampleDate = cellText(row.getCell(6), formatter, evaluator).trim();

            items.add(new WorkItemDto(
                    number,
                    localOrder,
                    content,
                    position,
                    inspectionTime,
                    recordNumber,
                    sampleDate.isBlank() ? null : sampleDate,
                    rowIndex + 1,
                    outputs.containsKey(number) && !outputs.get(number).isEmpty()
            ));
        }
        items.sort(Comparator.comparingInt(WorkItemDto::number));
        return items;
    }

    private Map<Integer, List<String>> findOutputSheets(Workbook workbook) {
        Map<Integer, List<String>> outputs = new HashMap<>();
        Pattern exact = Pattern.compile("^\\s*(\\d+)\\s*$");
        Pattern related = Pattern.compile(".*\\(\\s*(\\d+)\\s*\\).*", Pattern.CASE_INSENSITIVE);

        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            String sheetName = workbook.getSheetName(index);
            var exactMatcher = exact.matcher(sheetName);
            var relatedMatcher = related.matcher(sheetName);
            Integer number = null;
            if (exactMatcher.matches()) number = Integer.valueOf(exactMatcher.group(1));
            else if (relatedMatcher.matches()) number = Integer.valueOf(relatedMatcher.group(1));
            if (number != null) {
                outputs.computeIfAbsent(number, ignored -> new ArrayList<>()).add(sheetName);
            }
        }
        outputs.values().forEach(list -> list.sort(Comparator.comparingInt(workbook::getSheetIndex)));
        return outputs;
    }

    private Integer integerValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                if (Math.rint(value) == value && value <= Integer.MAX_VALUE) return (int) value;
            }
            String text = cellText(cell, formatter, evaluator).trim();
            if (INTEGER_TEXT.matcher(text).matches()) return Integer.valueOf(text);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException exception) {
            return formatter.formatCellValue(cell);
        }
    }

    private String afterColon(String value) {
        int index = value.indexOf(':');
        return index >= 0 ? value.substring(index + 1).trim() : value.trim();
    }

    private String extensionOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") ? ".xlsx" : ".xls";
    }
}
