package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.GenerateOutputSelection;
import com.hacom.bbnt.dto.GenerateRequest;
import com.hacom.bbnt.dto.GenerateSelection;
import com.hacom.bbnt.dto.DocumentPlanDto;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GeneratedDocument;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.JobContext;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;
import com.hacom.bbnt.model.ParsedSheetName;
import com.hacom.bbnt.model.TemplatePair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentGenerationService {
    private final TemporaryStore store;
    private final PdfConversionService pdfConversionService;
    private final TemplateCloneService templateCloneService;
    private final SheetNameParser sheetNameParser;

    public DocumentGenerationService(
            TemporaryStore store,
            PdfConversionService pdfConversionService,
            TemplateCloneService templateCloneService,
            SheetNameParser sheetNameParser
    ) {
        this.store = store;
        this.pdfConversionService = pdfConversionService;
        this.templateCloneService = templateCloneService;
        this.sheetNameParser = sheetNameParser;
    }

    public GenerationResult generate(String jobId, GenerateRequest request) {
        JobContext job = store.getJob(jobId);
        List<ResolvedSelection> selections = resolveSelections(job, request.selections());
        String documentId = store.newId();
        Instant now = Instant.now();

        try {
            Path directory = store.createDocumentDirectory(documentId);
            String extension = extensionOf(job.sourcePath());
            List<String> workItemNumbers = selections.stream()
                    .map(selection -> selection.item().itemNumber())
                    .distinct()
                    .toList();
            String baseName = safeFileName("Ho_so_DM_" + String.join("_", workItemNumbers));
            Path excelPath = directory.resolve(baseName + extension);

            WorkbookBuildResult workbookResult = createEditableWorkbook(job, excelPath, selections);
            verifyWorkbook(excelPath, workbookResult.selectedSheets());

            Path pdfPath = null;
            String pdfMessage = null;
            if (request.createPdf()) {
                Path printSource = directory.resolve("print_source" + extension);
                try {
                    createPrintWorkbook(excelPath, printSource, new LinkedHashSet<>(workbookResult.selectedSheets()));
                    pdfPath = pdfConversionService.convert(printSource, directory.resolve(baseName + ".pdf"));
                    if (pdfPath == null || !Files.exists(pdfPath) || Files.size(pdfPath) == 0) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Bản PDF được tạo ra không hợp lệ.");
                    }
                } catch (RuntimeException exception) {
                    pdfMessage = exception.getMessage();
                } finally {
                    Files.deleteIfExists(printSource);
                }
            }

            GeneratedDocument document = new GeneratedDocument(
                    documentId,
                    jobId,
                    workItemNumbers,
                    workbookResult.selectedSheets(),
                    excelPath,
                    pdfPath,
                    workbookResult.warnings(),
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

    private List<ResolvedSelection> resolveSelections(JobContext job, List<GenerateSelection> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần chọn ít nhất một dòng DM để xuất hồ sơ.");
        }

        List<ResolvedSelection> resolved = new ArrayList<>();
        Set<String> seenItems = new LinkedHashSet<>();
        for (GenerateSelection request : requests) {
            String itemNumber = sheetNameParser.normalizeItemNumber(request.itemNumber());
            if (!seenItems.add(itemNumber)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Số danh mục " + itemNumber + " xuất hiện nhiều lần trong yêu cầu.");
            }
            WorkItemDto item = job.workItem(itemNumber);
            if (item == null) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy số danh mục " + itemNumber + " trong sheet DM.");
            }
            List<GenerateOutputSelection> requestedOutputs = request.outputs() == null
                    ? List.of()
                    : request.outputs().stream().distinct().toList();
            if (requestedOutputs.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Chưa chọn biểu mẫu cho số danh mục " + itemNumber + ".");
            }

            MaterialFamily family = resolveRequestedFamily(item, request.materialFamily(), itemNumber);
            Map<String, String> existingByNormalizedName = new LinkedHashMap<>();
            job.outputSheets().getOrDefault(itemNumber, List.of())
                    .forEach(name -> existingByNormalizedName.put(normalizeSheetName(name), name));
            List<ResolvedOutput> outputs = new ArrayList<>();
            Set<String> seenOutputNames = new LinkedHashSet<>();

            for (GenerateOutputSelection output : requestedOutputs) {
                if (!seenOutputNames.add(normalizeSheetName(output.sheetName()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Sheet " + output.sheetName() + " được chọn lặp lại cho DM " + itemNumber + ".");
                }
                if (output.generationMode() == GenerationMode.EXISTING_SHEET) {
                    DocumentPlanDto plan = planFor(item, output.documentType());
                    if (plan == null || plan.availability() != OutputAvailability.EXISTING) {
                        throw new ApiException(HttpStatus.BAD_REQUEST,
                                "DM " + itemNumber + " không có " + output.documentType()
                                        + " ở chế độ EXISTING_SHEET.");
                    }
                    String actual = existingByNormalizedName.get(normalizeSheetName(output.sheetName()));
                    if (actual == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST,
                                "Sheet " + output.sheetName() + " không phải output hiện có của DM " + itemNumber + ".");
                    }
                    validateExistingDocumentType(actual, output.documentType(), itemNumber);
                    if (!normalizeSheetName(actual).equals(normalizeSheetName(plan.existingSheetName()))) {
                        throw new ApiException(HttpStatus.BAD_REQUEST,
                                "Sheet hiện có được chọn không khớp plan " + output.documentType()
                                        + " của DM " + itemNumber + ".");
                    }
                    outputs.add(ResolvedOutput.existing(output.documentType(), actual));
                    continue;
                }

                if (output.generationMode() != GenerationMode.CLONE_TEMPLATE) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Generation mode không hợp lệ cho " + output.sheetName() + ".");
                }
                if (family == MaterialFamily.UNKNOWN) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "DM " + itemNumber + " chưa xác định loại vật liệu. Hãy chọn Vữa hoặc Bê tông.");
                }
                if (output.documentType() != DocumentType.LM && output.documentType() != DocumentType.GM) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Chỉ LM/GM mới có thể tạo bằng CLONE_TEMPLATE.");
                }
                DocumentPlanDto currentPlan = planFor(item, output.documentType());
                if (currentPlan != null && currentPlan.availability() == OutputAvailability.EXISTING) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "DM " + itemNumber + " đã có sheet " + output.documentType()
                                    + "; không được clone bản trùng thay cho EXISTING_SHEET.");
                }
                ParsedSheetName parsed = sheetNameParser.parse(output.sheetName()).orElse(null);
                if (parsed == null || parsed.documentType() != output.documentType()
                        || !parsed.itemNumber().equalsIgnoreCase(itemNumber)
                        || parsed.materialFamily() != family) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Tên sheet dự kiến không hợp lệ cho DM " + itemNumber + ": " + output.sheetName());
                }
                String template = output.sourceTemplate();
                if (template == null || template.isBlank()) {
                    TemplatePair recommended = job.templateRegistry().pairFor(family);
                    template = recommended == null ? null
                            : output.documentType() == DocumentType.LM
                            ? recommended.lmSheetName() : recommended.gmSheetName();
                }
                validateTemplate(job, template, output.documentType(), family, itemNumber);
                String plannedName = sheetNameParser.plannedSheetName(output.documentType(), family, itemNumber);
                if (!normalizeSheetName(plannedName).equals(normalizeSheetName(output.sheetName()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Tên sheet clone phải đúng plan " + plannedName + " cho DM " + itemNumber + ".");
                }
                outputs.add(ResolvedOutput.clone(new PlannedClone(
                        plannedName, template, output.documentType(), family
                )));
            }
            outputs.sort(Comparator.comparingInt(value -> documentOrder(value.documentType())));
            resolved.add(new ResolvedSelection(item, List.copyOf(outputs)));
        }
        return List.copyOf(resolved);
    }

    private MaterialFamily resolveRequestedFamily(
            WorkItemDto item,
            MaterialFamily requested,
            String itemNumber
    ) {
        MaterialFamily existingFamily = existingSampleFamily(item);
        MaterialFamily explicit = requested == null ? MaterialFamily.UNKNOWN : requested;
        if (existingFamily != MaterialFamily.UNKNOWN) {
            if (explicit != MaterialFamily.UNKNOWN && explicit != existingFamily) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "DM " + itemNumber + " đã có LM/GM thuộc " + existingFamily
                                + "; không thể tạo sheet còn thiếu theo " + explicit + ".");
            }
            return existingFamily;
        }
        return explicit == MaterialFamily.UNKNOWN ? item.materialFamily() : explicit;
    }

    private MaterialFamily existingSampleFamily(WorkItemDto item) {
        MaterialFamily lm = familyOfExistingPlan(item.lmPlan());
        MaterialFamily gm = familyOfExistingPlan(item.gmPlan());
        if (lm != MaterialFamily.UNKNOWN && gm != MaterialFamily.UNKNOWN && lm != gm) {
            return MaterialFamily.UNKNOWN;
        }
        return lm != MaterialFamily.UNKNOWN ? lm : gm;
    }

    private MaterialFamily familyOfExistingPlan(DocumentPlanDto plan) {
        if (plan == null || plan.availability() != OutputAvailability.EXISTING
                || plan.existingSheetName() == null) return MaterialFamily.UNKNOWN;
        ParsedSheetName parsed = sheetNameParser.parse(plan.existingSheetName()).orElse(null);
        return parsed == null ? MaterialFamily.UNKNOWN : parsed.materialFamily();
    }

    private DocumentPlanDto planFor(WorkItemDto item, DocumentType type) {
        return switch (type) {
            case MAIN -> item.mainPlan();
            case LM -> item.lmPlan();
            case GM -> item.gmPlan();
            default -> null;
        };
    }

    private int documentOrder(DocumentType type) {
        return switch (type) {
            case MAIN -> 0;
            case LM -> 1;
            case GM -> 2;
            default -> 3;
        };
    }

    private void validateExistingDocumentType(String sheetName, DocumentType requestedType, String itemNumber) {
        ParsedSheetName parsed = sheetNameParser.parse(sheetName).orElse(null);
        DocumentType actualType = parsed == null ? DocumentType.UNKNOWN
                : parsed.mainSheet() ? DocumentType.MAIN : parsed.documentType();
        if (actualType != requestedType) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Sheet " + sheetName + " không đúng documentType " + requestedType + " của DM " + itemNumber + ".");
        }
    }

    private void validateTemplate(
            JobContext job,
            String templateName,
            DocumentType expectedType,
            MaterialFamily expectedFamily,
            String itemNumber
    ) {
        if (templateName == null || templateName.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không tìm thấy template " + expectedType + " cho DM " + itemNumber + ".");
        }
        ParsedSheetName parsed = sheetNameParser.parse(templateName).orElse(null);
        if (parsed == null || parsed.documentType() != expectedType || parsed.materialFamily() != expectedFamily) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Template " + templateName + " không đúng loại " + expectedType + "/" + expectedFamily + ".");
        }
        if (job.outputSheets().values().stream().flatMap(List::stream)
                .noneMatch(name -> normalizeSheetName(name).equals(normalizeSheetName(templateName)))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Template " + templateName + " không tồn tại trong workbook đã phân tích.");
        }
        var profile = job.templateRegistry().profileFor(templateName);
        boolean usableCandidate = job.templateRegistry().pairsFor(expectedFamily).stream()
                .filter(TemplatePair::usable)
                .anyMatch(pair -> expectedType == DocumentType.LM
                        ? pair.lmSheetName().equalsIgnoreCase(templateName)
                        : pair.gmSheetName().equalsIgnoreCase(templateName));
        if (profile == null || profile.documentType() != expectedType
                || profile.materialFamily() != expectedFamily || !usableCandidate) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Template " + templateName + " không tương thích TemplateProfile/print settings "
                            + expectedType + "/" + expectedFamily + ".");
        }
    }

    /**
     * File Excel tải về giữ các sheet nguồn/phụ thuộc ở trạng thái ẩn để các hồ sơ
     * EXISTING_SHEET tiếp tục hoạt động như trước. Chỉ sheet người dùng chọn và
     * các sheet clone mới được hiển thị.
     */
    private WorkbookBuildResult createEditableWorkbook(
            JobContext job,
            Path target,
            List<ResolvedSelection> selections
    ) throws IOException {
        try (InputStream input = Files.newInputStream(job.sourcePath());
             Workbook workbook = WorkbookFactory.create(input)) {
            Map<String, List<FormulaSnapshot>> protectedFormulas = workbook instanceof HSSFWorkbook
                    ? captureFormulaSnapshots(workbook)
                    : Map.of();
            List<String> selectedNames = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (ResolvedSelection selection : selections) {
                for (ResolvedOutput output : selection.outputs()) {
                    if (output.generationMode() == GenerationMode.EXISTING_SHEET) {
                        Sheet sheet = workbook.getSheet(output.existingSheetName());
                        if (sheet == null) {
                            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                                    "Sheet hiện có " + output.existingSheetName()
                                            + " đã biến mất khỏi workbook.");
                        }
                        // EXISTING_SHEET is intentionally read-only: do not touch P1,
                        // formulas, values, styles or any other cell on the sheet.
                        selectedNames.add(sheet.getSheetName());
                        continue;
                    }
                    PlannedClone clone = output.plannedClone();
                    ParsedSheetName templateMetadata = sheetNameParser.parse(clone.templateSheet()).orElse(null);
                    WorkItemDto templateItem = templateMetadata == null ? null : job.workItem(templateMetadata.itemNumber());
                    TemplateCloneService.CloneResult result = templateCloneService.cloneTemplate(
                            workbook,
                            clone.templateSheet(),
                            clone.plannedSheetName(),
                            selection.item(),
                            templateItem,
                            job.project(),
                            clone.family(),
                            clone.documentType()
                    );
                    selectedNames.add(result.actualSheetName());
                    warnings.addAll(result.warnings());
                }
                warnings.addAll(selection.item().warnings());
            }

            List<String> uniqueSelected = new ArrayList<>(new LinkedHashSet<>(selectedNames));
            if (uniqueSelected.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Không có sheet hợp lệ được chọn.");
            }
            // HSSF stores print areas as workbook names tied to a sheet index.
            // Capture them before reordering and restore them afterwards so moving
            // MAIN/LM/GM to the front cannot silently drop the clone print area.
            Map<String, String> printAreasBySheet = capturePrintAreas(workbook);
            // Keep the requested per-item order (MAIN -> LM -> GM) at the
            // beginning of the output workbook so each sample pair stays grouped.
            for (int index = 0; index < uniqueSelected.size(); index++) {
                workbook.setSheetOrder(uniqueSelected.get(index), index);
            }
            restorePrintAreas(workbook, printAreasBySheet);
            Set<String> selectedLookup = new LinkedHashSet<>(uniqueSelected);
            int firstSelected = -1;
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                boolean selected = selectedLookup.contains(sheet.getSheetName());
                workbook.setSheetHidden(index, !selected);
                sheet.setSelected(selected);
                if (selected && firstSelected < 0) firstSelected = index;
            }
            workbook.setActiveSheet(firstSelected);
            workbook.setSelectedTab(firstSelected);
            restoreFormulaSnapshots(workbook, protectedFormulas);
            writeWorkbook(workbook, target);
            if (!Files.exists(target) || Files.size(target) == 0) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "File Excel kết quả rỗng.");
            }
            return new WorkbookBuildResult(List.copyOf(uniqueSelected), List.copyOf(new LinkedHashSet<>(warnings)));
        }
    }

    /**
     * HSSFWorkbook.cloneSheet() có thể ghi lại token sheet-index trong formula của
     * các sheet đã tồn tại khi workbook cũ có nhiều sheet ẩn/đổi thứ tự. Bảo vệ
     * formula và cached value trước khi clone, rồi khôi phục sau toàn bộ thao tác
     * clone/rename/reorder để EXISTING_SHEET giữ nguyên nội dung cuối cùng.
     */
    private Map<String, List<FormulaSnapshot>> captureFormulaSnapshots(Workbook workbook) {
        Map<String, List<FormulaSnapshot>> snapshots = new LinkedHashMap<>();
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            List<FormulaSnapshot> formulas = new ArrayList<>();
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.FORMULA) continue;
                    formulas.add(FormulaSnapshot.capture(cell));
                }
            }
            if (!formulas.isEmpty()) snapshots.put(sheet.getSheetName(), List.copyOf(formulas));
        }
        return Map.copyOf(snapshots);
    }

    private void restoreFormulaSnapshots(
            Workbook workbook,
            Map<String, List<FormulaSnapshot>> snapshots
    ) {
        snapshots.forEach((sheetName, formulas) -> {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Sheet hiện có " + sheetName + " bị mất trong lúc tạo output.");
            }
            for (FormulaSnapshot snapshot : formulas) snapshot.restore(sheet);
        });
    }

    /**
     * LibreOffice có thể in cả sheet ẩn của workbook .xls. Bản dành riêng cho PDF
     * đóng băng công thức trên sheet đã chọn rồi xóa sheet khác.
     */

    private Map<String, String> capturePrintAreas(Workbook workbook) {
        Map<String, String> areas = new LinkedHashMap<>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            String area = workbook.getPrintArea(index);
            if (area == null || area.isBlank()) continue;
            int separator = area.indexOf('!');
            areas.put(workbook.getSheetName(index), separator >= 0 ? area.substring(separator + 1) : area);
        }
        return areas;
    }

    private void restorePrintAreas(Workbook workbook, Map<String, String> areas) {
        areas.forEach((sheetName, area) -> {
            int index = workbook.getSheetIndex(sheetName);
            if (index >= 0) workbook.setPrintArea(index, area);
        });
    }

    private void createPrintWorkbook(Path source, Path target, Set<String> selectedSheets) throws IOException {
        try (InputStream input = Files.newInputStream(source);
             Workbook workbook = WorkbookFactory.create(input)) {
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

    private void verifyWorkbook(Path path, List<String> expectedSheets) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "File Excel kết quả không hợp lệ.");
        }
        try (InputStream input = Files.newInputStream(path);
             Workbook reopened = WorkbookFactory.create(input)) {
            for (String sheetName : expectedSheets) {
                if (reopened.getSheet(sheetName) == null) {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Workbook mở lại được nhưng thiếu sheet " + sheetName + ".");
                }
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Workbook kết quả không thể mở lại bằng Apache POI: " + exception.getMessage());
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
            // Một số công thức cũ có external link/#REF nhưng vẫn có cached value.
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

    private void writeWorkbook(Workbook workbook, Path target) throws IOException {
        try (OutputStream output = Files.newOutputStream(target)) {
            workbook.write(output);
        }
    }

    private String extensionOf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx") ? ".xlsx" : ".xls";
    }

    private String safeFileName(String value) {
        String safe = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.length() <= 150 ? safe : safe.substring(0, 150);
    }

    private String normalizeSheetName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }



    public record GenerationResult(GeneratedDocument document, String pdfMessage) {
    }

    private record FormulaSnapshot(
            int rowIndex,
            int columnIndex,
            String formula,
            CellType cachedType,
            String text,
            double number,
            boolean bool,
            byte error
    ) {
        private static FormulaSnapshot capture(Cell cell) {
            CellType cached = cell.getCachedFormulaResultType();
            String text = null;
            double number = 0;
            boolean bool = false;
            byte error = 0;
            try {
                switch (cached) {
                    case STRING -> text = cell.getStringCellValue();
                    case NUMERIC -> number = cell.getNumericCellValue();
                    case BOOLEAN -> bool = cell.getBooleanCellValue();
                    case ERROR -> error = cell.getErrorCellValue();
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                cached = CellType.BLANK;
            }
            return new FormulaSnapshot(cell.getRowIndex(), cell.getColumnIndex(),
                    cell.getCellFormula(), cached, text, number, bool, error);
        }

        private void restore(Sheet sheet) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) throw new IllegalStateException("Mất row formula HSSF " + rowIndex);
            Cell cell = row.getCell(columnIndex);
            if (cell == null || cell.getCellType() != CellType.FORMULA) {
                throw new IllegalStateException("Mất formula cell HSSF " + rowIndex + ":" + columnIndex);
            }
            try {
                cell.setCellFormula(formula);
                switch (cachedType) {
                    case STRING -> cell.setCellValue(text == null ? "" : text);
                    case NUMERIC -> cell.setCellValue(number);
                    case BOOLEAN -> cell.setCellValue(bool);
                    case ERROR -> cell.setCellErrorValue(error);
                    default -> { }
                }
            } catch (RuntimeException exception) {
                // Công thức cũ chứa #REF!/external link có thể không parse lại được.
                // Nếu cloneSheet không đổi chính công thức đó, giữ nguyên record gốc.
                String current;
                try {
                    current = cell.getCellFormula();
                } catch (RuntimeException ignored) {
                    current = "";
                }
                if (!formula.equals(current)) {
                    throw new IllegalStateException("Không thể bảo toàn formula HSSF tại "
                            + sheet.getSheetName() + "!" + cell.getAddress()
                            + ": " + formula, exception);
                }
            }
        }
    }

    private record EvaluatedValue(CellType type, String text, double number, boolean bool, byte error) {
        static EvaluatedValue blank() {
            return new EvaluatedValue(CellType.BLANK, null, 0, false, (byte) 0);
        }
    }

    private record PlannedClone(
            String plannedSheetName,
            String templateSheet,
            DocumentType documentType,
            MaterialFamily family
    ) {
    }

    private record ResolvedOutput(
            DocumentType documentType,
            GenerationMode generationMode,
            String existingSheetName,
            PlannedClone plannedClone
    ) {
        static ResolvedOutput existing(DocumentType type, String sheetName) {
            return new ResolvedOutput(type, GenerationMode.EXISTING_SHEET, sheetName, null);
        }

        static ResolvedOutput clone(PlannedClone clone) {
            return new ResolvedOutput(clone.documentType(), GenerationMode.CLONE_TEMPLATE, null, clone);
        }
    }

    private record ResolvedSelection(
            WorkItemDto item,
            List<ResolvedOutput> outputs
    ) {
    }

    private record WorkbookBuildResult(List<String> selectedSheets, List<String> warnings) {
    }
}
