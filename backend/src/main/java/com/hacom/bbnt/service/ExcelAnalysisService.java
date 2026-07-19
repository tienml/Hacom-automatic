package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.ProjectSummary;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.JobContext;
import com.hacom.bbnt.model.TemplateRegistry;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExcelAnalysisService {
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Pattern ITEM_TEXT = Pattern.compile("^([0-9]+[A-Za-z]?)$");

    private final TemporaryStore store;
    private final SheetNameParser sheetNameParser;
    private final TemplateRegistryService templateRegistryService;
    private final WorkItemPlanningService planningService;

    public ExcelAnalysisService(
            TemporaryStore store,
            SheetNameParser sheetNameParser,
            TemplateRegistryService templateRegistryService,
            WorkItemPlanningService planningService
    ) {
        this.store = store;
        this.sheetNameParser = sheetNameParser;
        this.templateRegistryService = templateRegistryService;
        this.planningService = planningService;
    }

    public JobContext analyze(MultipartFile file) {
        validateFile(file);
        String jobId = store.newId();
        Instant now = Instant.now();

        try {
            Path jobDirectory = store.createJobDirectory(jobId);
            String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "workbook.xls");
            String extension = extensionOf(originalName);
            Path sourcePath = jobDirectory.resolve("source" + extension);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, sourcePath, StandardCopyOption.REPLACE_EXISTING);
            }

            try (InputStream input = Files.newInputStream(sourcePath);
                 Workbook workbook = WorkbookFactory.create(input)) {
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
                Sheet dmSheet = findDmSheet(workbook, formatter, evaluator);
                ColumnMapping mapping = detectColumnMapping(dmSheet, formatter, evaluator);
                ProjectSummary project = readProjectSummary(dmSheet, formatter, evaluator);
                Map<String, List<String>> outputSheets = findOutputSheets(workbook);
                TemplateRegistry templateRegistry = templateRegistryService.build(workbook);
                List<WorkItemDto> items = readWorkItems(
                        dmSheet,
                        mapping,
                        formatter,
                        evaluator,
                        outputSheets,
                        templateRegistry,
                        project
                );

                if (items.isEmpty()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Đã tìm thấy sheet DM nhưng không đọc được dòng công việc hợp lệ.");
                }

                JobContext context = new JobContext(
                        jobId,
                        originalName,
                        sourcePath,
                        dmSheet.getSheetName(),
                        project,
                        mapping.warnings(),
                        List.copyOf(items),
                        immutableLists(outputSheets),
                        templateRegistry,
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
                    "Không đọc được workbook BBNT: " + rootMessage(exception));
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn file Excel BBNT.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Chỉ nhận file tối đa 50 MB.");
        }
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xls") || name.endsWith(".xlsx"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ file .xls hoặc .xlsx.");
        }
    }

    private Sheet findDmSheet(Workbook workbook, DataFormatter formatter, FormulaEvaluator evaluator) {
        Sheet exactWithTrailingSpace = workbook.getSheet("DM ");
        if (exactWithTrailingSpace != null) return exactWithTrailingSpace;
        Sheet exact = workbook.getSheet("DM");
        if (exact != null) return exact;
        for (Sheet sheet : workbook) {
            if (sheet.getSheetName().trim().equalsIgnoreCase("DM")) return sheet;
        }
        for (Sheet sheet : workbook) {
            if (looksLikeDmSheet(sheet, formatter, evaluator)) return sheet;
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Không tìm thấy sheet DM theo tên hoặc tiêu đề cột.");
    }

    private boolean looksLikeDmSheet(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int maxRow = Math.min(sheet.getLastRowNum(), 60);
        boolean content = false;
        boolean position = false;
        boolean record = false;
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            int maxColumn = Math.min(Math.max(row.getLastCellNum(), 0), 20);
            for (int column = 0; column < maxColumn; column++) {
                String normalized = TextNormalizer.asciiLower(cellText(row.getCell(column), formatter, evaluator));
                content |= normalized.contains("noi dung");
                position |= normalized.contains("vi tri");
                record |= normalized.contains("nghiem thu cong viec") || normalized.contains("so bien ban");
            }
        }
        return content && position && record;
    }

    private ColumnMapping detectColumnMapping(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<Field, Integer> detected = new HashMap<>();
        List<HeaderCandidate> localOrderCandidates = new ArrayList<>();
        int maxRow = Math.min(sheet.getLastRowNum(), 80);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            int maxColumn = Math.min(Math.max(row.getLastCellNum(), 0), 30);
            for (int column = 0; column < maxColumn; column++) {
                String normalized = TextNormalizer.asciiLower(cellText(row.getCell(column), formatter, evaluator));
                if (normalized.isBlank()) continue;
                if (normalized.contains("noi dung")) detected.putIfAbsent(Field.CONTENT, column);
                if (normalized.contains("vi tri")) detected.putIfAbsent(Field.POSITION, column);
                if (normalized.contains("ngay lay mau")) detected.putIfAbsent(Field.SAMPLE_DATE, column);
                if (normalized.equals("thoi gian") || normalized.contains("thoi gian nghiem thu")) {
                    detected.putIfAbsent(Field.ACCEPTANCE_TIME, column);
                }
                if (normalized.contains("so bien ban")) detected.putIfAbsent(Field.RECORD_NUMBER, column);
                if (normalized.equals("stt")) localOrderCandidates.add(new HeaderCandidate(rowIndex, column));
            }
        }

        List<String> warnings = new ArrayList<>();
        int localOrder = chooseLocalOrderColumn(sheet, localOrderCandidates, formatter, evaluator);
        if (localOrder < 0) {
            localOrder = 1;
            warnings.add("Không xác minh được cột STT cục bộ; dùng fallback cột B.");
        }
        int itemNumber = chooseItemNumberColumn(sheet, localOrder, formatter, evaluator);
        if (itemNumber < 0) {
            itemNumber = 0;
            warnings.add("Không xác minh được cột mã danh mục; dùng fallback cột A.");
        }

        int content = detected.getOrDefault(Field.CONTENT, 2);
        int position = detected.getOrDefault(Field.POSITION, 3);
        int acceptance = detected.getOrDefault(Field.ACCEPTANCE_TIME, 4);
        int record = detected.getOrDefault(Field.RECORD_NUMBER, 5);
        int sample = detected.getOrDefault(Field.SAMPLE_DATE, 6);
        if (!detected.containsKey(Field.CONTENT)) warnings.add("Không nhận diện được header NỘI DUNG; dùng fallback cột C.");
        if (!detected.containsKey(Field.POSITION)) warnings.add("Không nhận diện được header VỊ TRÍ; dùng fallback cột D.");
        if (!detected.containsKey(Field.ACCEPTANCE_TIME)) warnings.add("Không nhận diện được header THỜI GIAN; dùng fallback cột E.");
        if (!detected.containsKey(Field.RECORD_NUMBER)) warnings.add("Không nhận diện được header SỐ BIÊN BẢN; dùng fallback cột F.");
        if (!detected.containsKey(Field.SAMPLE_DATE)) warnings.add("Không nhận diện được header NGÀY LẤY MẪU; dùng fallback cột G.");

        return new ColumnMapping(itemNumber, localOrder, content, position, acceptance, record, sample, List.copyOf(warnings));
    }

    private int chooseLocalOrderColumn(
            Sheet sheet,
            List<HeaderCandidate> candidates,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        return candidates.stream()
                .max(Comparator.comparingInt(candidate -> localOrderScore(sheet, candidate, formatter, evaluator)))
                .filter(candidate -> localOrderScore(sheet, candidate, formatter, evaluator) >= 4)
                .map(HeaderCandidate::column)
                .orElse(-1);
    }

    private int localOrderScore(
            Sheet sheet,
            HeaderCandidate candidate,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        int score = candidate.column() > 0 ? 3 : 0;
        int end = Math.min(sheet.getLastRowNum(), candidate.row() + 80);
        for (int rowIndex = candidate.row() + 1; rowIndex <= end; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String local = cellText(row.getCell(candidate.column()), formatter, evaluator).trim();
            String previous = candidate.column() > 0
                    ? itemNumber(row.getCell(candidate.column() - 1), formatter, evaluator)
                    : null;
            if (local.matches("[0-9]+[A-Za-z]?")) score++;
            if (previous != null) score += 2;
        }
        return score;
    }

    private int chooseItemNumberColumn(
            Sheet sheet,
            int localOrderColumn,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        if (localOrderColumn > 0) {
            int candidate = localOrderColumn - 1;
            int matches = countItemNumbers(sheet, candidate, formatter, evaluator);
            if (matches >= 5) return candidate;
        }
        int bestColumn = -1;
        int bestMatches = 0;
        for (int column = 0; column < Math.min(10, localOrderColumn + 3); column++) {
            if (column == localOrderColumn) continue;
            int matches = countItemNumbers(sheet, column, formatter, evaluator);
            if (matches > bestMatches) {
                bestMatches = matches;
                bestColumn = column;
            }
        }
        return bestMatches >= 5 ? bestColumn : -1;
    }

    private int countItemNumbers(
            Sheet sheet,
            int column,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        int matches = 0;
        int maxRow = Math.min(sheet.getLastRowNum(), 300);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && itemNumber(row.getCell(column), formatter, evaluator) != null) matches++;
        }
        return matches;
    }

    private ProjectSummary readProjectSummary(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        String project = null;
        String location = null;
        String packageName = null;
        String contractor = null;
        int maxRow = Math.min(sheet.getLastRowNum(), 30);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            int maxColumn = Math.min(Math.max(row.getLastCellNum(), 0), 16);
            for (int column = 0; column < maxColumn; column++) {
                String text = cellText(row.getCell(column), formatter, evaluator).trim();
                String lower = text.toLowerCase(Locale.forLanguageTag("vi-VN"));
                if (lower.startsWith("dự án")) project = afterColon(text);
                else if (lower.startsWith("địa điểm")) location = afterColon(text);
                else if (lower.startsWith("gói thầu")) packageName = afterColon(text);
                else if (lower.startsWith("nhà thầu")) contractor = afterColon(text);
            }
        }
        return new ProjectSummary(project, location, packageName, contractor);
    }

    private List<WorkItemDto> readWorkItems(
            Sheet sheet,
            ColumnMapping mapping,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            Map<String, List<String>> outputs,
            TemplateRegistry templateRegistry,
            ProjectSummary project
    ) {
        List<WorkItemDto> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String itemNumber = itemNumber(row.getCell(mapping.itemNumber()), formatter, evaluator);
            if (itemNumber == null || !seen.add(itemNumber)) continue;

            String content = cellText(row.getCell(mapping.content()), formatter, evaluator).trim();
            String position = cellText(row.getCell(mapping.position()), formatter, evaluator).trim();
            if (content.isBlank() && position.isBlank()) continue;

            String localOrder = cellText(row.getCell(mapping.localOrder()), formatter, evaluator).trim();
            String inspectionTime = cellText(row.getCell(mapping.acceptanceTime()), formatter, evaluator).trim();
            String recordNumber = cellText(row.getCell(mapping.recordNumber()), formatter, evaluator).trim();
            String sampleDate = cellText(row.getCell(mapping.sampleDate()), formatter, evaluator).trim();
            List<String> existing = outputs.getOrDefault(itemNumber, List.of());
            items.add(planningService.plan(
                    itemNumber,
                    localOrder,
                    content,
                    position,
                    inspectionTime,
                    recordNumber,
                    sampleDate,
                    rowIndex + 1,
                    existing,
                    templateRegistry,
                    project,
                    mapping.warnings()
            ));
        }
        items.sort(Comparator.comparing(WorkItemDto::itemNumber, this::compareItemNumbers));
        return items;
    }

    private Map<String, List<String>> findOutputSheets(Workbook workbook) {
        Map<String, List<String>> outputs = new LinkedHashMap<>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            String sheetName = workbook.getSheetName(index);
            sheetNameParser.parse(sheetName).ifPresent(parsed -> outputs
                    .computeIfAbsent(parsed.itemNumber(), ignored -> new ArrayList<>())
                    .add(sheetName));
        }
        return outputs;
    }

    private String itemNumber(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                if (Math.rint(value) == value && value >= 0) return Long.toString(Math.round(value));
            }
            String text = cellText(cell, formatter, evaluator).trim();
            Matcher matcher = ITEM_TEXT.matcher(text);
            return matcher.matches() ? sheetNameParser.normalizeItemNumber(matcher.group(1)) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException exception) {
            try {
                return formatter.formatCellValue(cell);
            } catch (RuntimeException ignored) {
                return "";
            }
        }
    }

    private int compareItemNumbers(String left, String right) {
        Matcher a = Pattern.compile("^(\\d+)([A-Z]?)$").matcher(left);
        Matcher b = Pattern.compile("^(\\d+)([A-Z]?)$").matcher(right);
        if (a.matches() && b.matches()) {
            int numeric = Long.compare(Long.parseLong(a.group(1)), Long.parseLong(b.group(1)));
            if (numeric != 0) return numeric;
            return a.group(2).compareTo(b.group(2));
        }
        return left.compareToIgnoreCase(right);
    }

    private Map<String, List<String>> immutableLists(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private String afterColon(String value) {
        int index = value.indexOf(':');
        return index >= 0 ? value.substring(index + 1).trim() : value.trim();
    }

    private String extensionOf(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? ".xlsx" : ".xls";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }

    private enum Field {
        LOCAL_ORDER,
        CONTENT,
        POSITION,
        ACCEPTANCE_TIME,
        RECORD_NUMBER,
        SAMPLE_DATE
    }

    private record ColumnMapping(
            int itemNumber,
            int localOrder,
            int content,
            int position,
            int acceptanceTime,
            int recordNumber,
            int sampleDate,
            List<String> warnings
    ) {
    }

    private record HeaderCandidate(int row, int column) {
    }
}
