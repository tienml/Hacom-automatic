package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.FieldDecisionDto;
import com.hacom.bbnt.dto.ProjectSummary;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.DataCertainty;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.FieldAction;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.TemplateProfile;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateCloneService {
    private static final Pattern GENERIC_PERSON_PREFIX = Pattern.compile("(?iu)^Ông\\s*\\(bà\\):\\s*(.+)$");

    private final TemplateProfileService profileService;
    private final FieldDecisionService fieldDecisionService;
    private final GeneratedSheetValidator validator;

    public TemplateCloneService(
            TemplateProfileService profileService,
            FieldDecisionService fieldDecisionService,
            GeneratedSheetValidator validator
    ) {
        this.profileService = profileService;
        this.fieldDecisionService = fieldDecisionService;
        this.validator = validator;
    }

    public CloneResult cloneTemplate(
            Workbook workbook,
            String templateSheetName,
            String plannedSheetName,
            WorkItemDto targetItem,
            WorkItemDto templateItem,
            ProjectSummary project,
            MaterialFamily family,
            DocumentType documentType,
            Map<String, String> fieldOverrides
    ) {
        int templateIndex = workbook.getSheetIndex(templateSheetName);
        if (templateIndex < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không tìm thấy template " + templateSheetName + " trong workbook.");
        }
        Sheet template = workbook.getSheetAt(templateIndex);
        TemplateProfile profile = profileService.resolve(template, family, documentType);
        List<FieldDecisionDto> decisions = fieldDecisionService.decisions(targetItem, project, profile, documentType);
        int templateDrawingCount = drawingCount(template);
        String printArea = workbook.getPrintArea(templateIndex);
        CellRangeAddress repeatingRows = template.getRepeatingRows();
        CellRangeAddress repeatingColumns = template.getRepeatingColumns();

        Sheet clone = workbook.cloneSheet(templateIndex);
        int cloneIndex = workbook.getSheetIndex(clone);
        String actualName = uniqueSafeSheetName(workbook, plannedSheetName, cloneIndex);
        workbook.setSheetName(cloneIndex, actualName);
        clone = workbook.getSheetAt(cloneIndex);
        restorePrintSettings(workbook, template, clone, cloneIndex, printArea, repeatingRows, repeatingColumns);

        sanitizeTemplateData(clone, profile, decisions, templateItem);
        Set<String> certainCells = applyFieldDecisions(clone, decisions, fieldOverrides);
        int cloneDrawingCount = drawingCount(clone);
        boolean cloneHasPrintArea = workbook.getPrintArea(cloneIndex) != null
                && !workbook.getPrintArea(cloneIndex).isBlank();
        List<String> warnings = new ArrayList<>(profile.warnings());
        warnings.addAll(validator.validate(
                clone,
                targetItem,
                templateItem,
                profile,
                decisions,
                templateDrawingCount,
                cloneDrawingCount,
                printArea != null && !printArea.isBlank(),
                cloneHasPrintArea
        ));
        return new CloneResult(actualName, templateSheetName, documentType,
                List.copyOf(new LinkedHashSet<>(warnings)), decisions, certainCells);
    }

    private void sanitizeTemplateData(
            Sheet sheet,
            TemplateProfile profile,
            List<FieldDecisionDto> decisions,
            WorkItemDto templateItem
    ) {
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        Set<String> keepCells = new LinkedHashSet<>();
        decisions.stream().filter(decision -> decision.action() == FieldAction.KEEP_TEMPLATE_STRUCTURE)
                .forEach(decision -> keepCells.addAll(decision.targetCells()));
        Set<String> staleExactValues = collectStaleExactValues(templateItem);
        Set<String> extractedNames = collectTemplatePersonNames(sheet);

        // 1-2. Xóa formula/error không an toàn; project-level KEEP được đóng băng cached value.
        for (Row row : sheet) {
            for (Cell cell : row) {
                String address = cell.getAddress().formatAsString();
                if (cell.getCellType() == CellType.FORMULA) {
                    if (keepCells.contains(address)) flattenCachedFormula(cell);
                    else cell.setBlank();
                } else if (cell.getCellType() == CellType.ERROR) {
                    cell.setBlank();
                }
            }
        }

        // 3. Xóa mapped variable cells, trừ project-level được phép KEEP.
        for (String address : profile.variableCells()) {
            if (!keepCells.contains(address)) clearCell(sheet, address);
        }

        // 4. Xóa mọi mapped uncertain cells/ranges.
        for (String address : profile.uncertainCells()) clearCell(sheet, address);
        for (String range : profile.uncertainRanges()) clearRange(sheet, range);

        // 5-9. Xóa stale values, record numbers, dữ liệu LAS/người và lớp regex bảo vệ bổ sung.
        for (Row row : sheet) {
            for (Cell cell : row) {
                String address = cell.getAddress().formatAsString();
                if (keepCells.contains(address)) continue;
                String value = formatter.formatCellValue(cell);
                if (value.isBlank()) continue;
                String compact = TextNormalizer.compact(value);
                String upper = compact.toUpperCase(Locale.ROOT);
                String normalized = TextNormalizer.asciiLower(compact);

                if (TemplateDataPatterns.ERROR_TOKENS.stream().anyMatch(upper::contains)
                        || staleExactValues.stream().anyMatch(old -> old.equalsIgnoreCase(compact))
                        || TemplateDataPatterns.RECORD_NUMBER.matcher(compact).matches()
                        || TemplateDataPatterns.UNCERTAIN_VALUE.matcher(compact).find()) {
                    cell.setBlank();
                    continue;
                }
                Matcher personMatcher = GENERIC_PERSON_PREFIX.matcher(compact);
                if (personMatcher.matches() && !personMatcher.group(1).isBlank()) {
                    cell.setCellValue("Ông (bà):");
                    continue;
                }
                if (extractedNames.stream().anyMatch(name -> name.equalsIgnoreCase(compact))) {
                    cell.setBlank();
                    continue;
                }
                if (normalized.startsWith("dai dien phong las")
                        || normalized.startsWith("dai dien phong thi nghiem")) {
                    cell.setCellValue("Đại diện phòng thí nghiệm:");
                } else if (normalized.contains("phong thi nghiem las")
                        || normalized.contains("phong las")
                        || normalized.contains("las xd")
                        || normalized.contains("ma las")
                        || normalized.contains("nguoi nhan mau")
                        || normalized.contains("nguoi phu trach")
                        || normalized.contains("can bo phong thi nghiem")
                        || normalized.contains("ket thuc vao luc")
                        || normalized.contains("10h00 cung ngay")) {
                    cell.setBlank();
                }
            }
        }
    }

    /**
     * Áp field decisions vào sheet vừa clone.
     * Với các trường CERTAIN: luôn điền giá trị suy ra từ DM/project.
     * Với các trường UNCERTAIN/UNKNOWN (mặc định phải để trống): nếu người dùng đã tự nhập
     * giá trị ở bước "Chi tiết trường dữ liệu" (fieldOverrides, khoá theo fieldName) thì điền
     * giá trị đó thay vì xóa trắng; nếu không có override thì vẫn xóa trắng như cũ.
     * targetRanges (vùng dòng mẫu phụ) không nhận override tự do vì đây là cấu trúc nhiều ô,
     * không phải 1 ô đơn — luôn được làm sạch để giữ đúng bố cục template.
     */
    private Set<String> applyFieldDecisions(
            Sheet sheet,
            List<FieldDecisionDto> decisions,
            Map<String, String> fieldOverrides
    ) {
        Map<String, String> overrides = fieldOverrides == null ? Map.of() : fieldOverrides;
        Set<String> certain = new LinkedHashSet<>();
        for (FieldDecisionDto decision : decisions) {
            boolean needsClearOrOverride = decision.action() == FieldAction.CLEAR
                    || decision.certainty() == DataCertainty.UNCERTAIN
                    || decision.certainty() == DataCertainty.UNKNOWN;
            if (!needsClearOrOverride) continue;
            String override = overrides.get(decision.fieldName());
            if (override != null && !override.isBlank()) {
                for (String address : decision.targetCells()) {
                    setCellValue(sheet, address, override.trim());
                    certain.add(address);
                }
                decision.targetRanges().forEach(range -> clearRange(sheet, range));
            } else {
                decision.targetCells().forEach(address -> clearCell(sheet, address));
                decision.targetRanges().forEach(range -> clearRange(sheet, range));
            }
        }
        for (FieldDecisionDto decision : decisions) {
            if (decision.certainty() != DataCertainty.CERTAIN || decision.action() != FieldAction.POPULATE) continue;
            for (String address : decision.targetCells()) {
                setCellValue(sheet, address, decision.value());
                certain.add(address);
            }
        }
        return Set.copyOf(certain);
    }

    private void flattenCachedFormula(Cell cell) {
        try {
            CellType cachedType = cell.getCachedFormulaResultType();
            switch (cachedType) {
                case STRING -> {
                    String value = cell.getStringCellValue();
                    cell.setBlank();
                    cell.setCellValue(value);
                }
                case NUMERIC -> {
                    double value = cell.getNumericCellValue();
                    cell.setBlank();
                    cell.setCellValue(value);
                }
                case BOOLEAN -> {
                    boolean value = cell.getBooleanCellValue();
                    cell.setBlank();
                    cell.setCellValue(value);
                }
                default -> cell.setBlank();
            }
        } catch (RuntimeException exception) {
            cell.setBlank();
        }
    }

    private Set<String> collectTemplatePersonNames(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        Set<String> names = new HashSet<>();
        for (Row row : sheet) {
            for (Cell cell : row) {
                String value = TextNormalizer.compact(formatter.formatCellValue(cell));
                Matcher matcher = GENERIC_PERSON_PREFIX.matcher(value);
                if (matcher.matches() && !matcher.group(1).isBlank()) names.add(matcher.group(1).trim());
            }
        }
        return names;
    }

    private Set<String> collectStaleExactValues(WorkItemDto templateItem) {
        if (templateItem == null) return Set.of();
        Set<String> values = new LinkedHashSet<>();
        addCompact(values, templateItem.itemNumber());
        addCompact(values, templateItem.content());
        addCompact(values, templateItem.position());
        addCompact(values, templateItem.inspectionTime());
        addCompact(values, templateItem.recordNumber());
        addCompact(values, templateItem.sampleDate());
        addCompact(values, convertNumber(templateItem.recordNumber(), "LM"));
        addCompact(values, convertNumber(templateItem.recordNumber(), "GM"));
        addCompact(values, "Lấy mẫu " + stripQuality(templateItem.content()) + optionalLocation(templateItem.position()));
        addCompact(values, "Mẫu " + stripQuality(templateItem.content()) + optionalLocation(templateItem.position()));
        return Set.copyOf(values);
    }

    private String convertNumber(String source, String type) {
        if (source == null) return "";
        String[] parts = source.split("/", -1);
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].equalsIgnoreCase("NTCV") || parts[index].equalsIgnoreCase("YCNT")
                    || parts[index].equalsIgnoreCase("LM") || parts[index].equalsIgnoreCase("GM")) {
                parts[index] = type;
                return String.join("/", parts);
            }
        }
        return "";
    }

    private String stripQuality(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("(?iu)^chất\\s+lượng\\s+", "");
    }

    private String optionalLocation(String location) {
        return location == null || location.isBlank() ? "" : " (" + location.trim() + ")";
    }

    private void addCompact(Set<String> values, String value) {
        String compact = TextNormalizer.compact(value);
        if (!compact.isBlank()) values.add(compact);
    }

    private void restorePrintSettings(
            Workbook workbook,
            Sheet template,
            Sheet clone,
            int cloneIndex,
            String printArea,
            CellRangeAddress repeatingRows,
            CellRangeAddress repeatingColumns
    ) {
        copySheetPrintFlags(template, clone);
        copyPrintSetup(template.getPrintSetup(), clone.getPrintSetup());
        copyMargins(template, clone);
        copyHeader(template.getHeader(), clone.getHeader());
        copyFooter(template.getFooter(), clone.getFooter());
        copyBreaks(template, clone);

        if (printArea != null && !printArea.isBlank()) {
            int separator = printArea.indexOf('!');
            String area = separator >= 0 ? printArea.substring(separator + 1) : printArea;
            workbook.setPrintArea(cloneIndex, area);
        }
        if (repeatingRows != null) clone.setRepeatingRows(repeatingRows.copy());
        if (repeatingColumns != null) clone.setRepeatingColumns(repeatingColumns.copy());
    }

    private void copySheetPrintFlags(Sheet source, Sheet target) {
        target.setAutobreaks(source.getAutobreaks());
        target.setFitToPage(source.getFitToPage());
        target.setHorizontallyCenter(source.getHorizontallyCenter());
        target.setVerticallyCenter(source.getVerticallyCenter());
        target.setPrintGridlines(source.isPrintGridlines());
        target.setPrintRowAndColumnHeadings(source.isPrintRowAndColumnHeadings());
        target.setRightToLeft(source.isRightToLeft());
        target.setDisplayGridlines(source.isDisplayGridlines());
        target.setDisplayFormulas(source.isDisplayFormulas());
        target.setDisplayRowColHeadings(source.isDisplayRowColHeadings());
        target.setDisplayGuts(source.getDisplayGuts());
        target.setDisplayZeros(source.isDisplayZeros());
        target.setRowSumsBelow(source.getRowSumsBelow());
        target.setRowSumsRight(source.getRowSumsRight());
    }

    private void copyPrintSetup(PrintSetup source, PrintSetup target) {
        target.setPaperSize(source.getPaperSize());
        target.setScale(source.getScale());
        target.setPageStart(source.getPageStart());
        target.setFitWidth(source.getFitWidth());
        target.setFitHeight(source.getFitHeight());
        target.setLeftToRight(source.getLeftToRight());
        target.setLandscape(source.getLandscape());
        target.setValidSettings(source.getValidSettings());
        target.setNoColor(source.getNoColor());
        target.setDraft(source.getDraft());
        target.setNotes(source.getNotes());
        target.setNoOrientation(source.getNoOrientation());
        target.setUsePage(source.getUsePage());
        target.setHResolution(source.getHResolution());
        target.setVResolution(source.getVResolution());
        target.setHeaderMargin(source.getHeaderMargin());
        target.setFooterMargin(source.getFooterMargin());
        target.setCopies(source.getCopies());
    }

    private void copyMargins(Sheet source, Sheet target) {
        for (PageMargin margin : PageMargin.values()) target.setMargin(margin, source.getMargin(margin));
    }

    private void copyHeader(Header source, Header target) {
        target.setLeft(source.getLeft());
        target.setCenter(source.getCenter());
        target.setRight(source.getRight());
    }

    private void copyFooter(Footer source, Footer target) {
        target.setLeft(source.getLeft());
        target.setCenter(source.getCenter());
        target.setRight(source.getRight());
    }

    private void copyBreaks(Sheet source, Sheet target) {
        Set<Integer> sourceRowBreaks = new HashSet<>();
        for (int rowBreak : source.getRowBreaks()) sourceRowBreaks.add(rowBreak);
        for (int rowBreak : target.getRowBreaks()) if (!sourceRowBreaks.contains(rowBreak)) target.removeRowBreak(rowBreak);
        for (int rowBreak : sourceRowBreaks) if (!target.isRowBroken(rowBreak)) target.setRowBreak(rowBreak);

        Set<Integer> sourceColumnBreaks = new HashSet<>();
        for (int columnBreak : source.getColumnBreaks()) sourceColumnBreaks.add(columnBreak);
        for (int columnBreak : target.getColumnBreaks()) if (!sourceColumnBreaks.contains(columnBreak)) target.removeColumnBreak(columnBreak);
        for (int columnBreak : sourceColumnBreaks) if (!target.isColumnBroken(columnBreak)) target.setColumnBreak(columnBreak);
    }

    private String uniqueSafeSheetName(Workbook workbook, String planned, int cloneIndex) {
        String base = WorkbookUtil.createSafeSheetName(planned == null ? "Sheet" : planned);
        if (base.length() > 31) base = base.substring(0, 31);
        if (base.isBlank()) base = "Sheet";
        String candidate = base;
        int counter = 2;
        while (true) {
            int existing = workbook.getSheetIndex(candidate);
            if (existing < 0 || existing == cloneIndex) return candidate;
            String suffix = "_" + counter++;
            int maxBase = Math.max(1, 31 - suffix.length());
            candidate = base.substring(0, Math.min(base.length(), maxBase)) + suffix;
        }
    }

    private int drawingCount(Sheet sheet) {
        if (sheet instanceof XSSFSheet xssfSheet) {
            XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getShapes().size();
        }
        if (sheet instanceof HSSFSheet hssfSheet) {
            HSSFPatriarch drawing = hssfSheet.getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getChildren().size();
        }
        return 0;
    }

    private void clearRange(Sheet sheet, String rangeText) {
        CellRangeAddress range = CellRangeAddress.valueOf(rangeText);
        for (int rowIndex = range.getFirstRow(); rowIndex <= range.getLastRow(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (int column = range.getFirstColumn(); column <= range.getLastColumn(); column++) {
                Cell cell = row.getCell(column);
                if (cell != null) cell.setBlank();
            }
        }
    }

    private void setCellValue(Sheet sheet, String address, String value) {
        CellReference reference = new CellReference(address);
        Row row = sheet.getRow(reference.getRow());
        if (row == null) row = sheet.createRow(reference.getRow());
        Cell cell = row.getCell(reference.getCol());
        if (cell == null) cell = row.createCell(reference.getCol());
        cell.setCellValue(value == null ? "" : value);
    }

    private void clearCell(Sheet sheet, String address) {
        CellReference reference = new CellReference(address);
        Row row = sheet.getRow(reference.getRow());
        if (row == null) return;
        Cell cell = row.getCell(reference.getCol());
        if (cell != null) cell.setBlank();
    }

    public record CloneResult(
            String actualSheetName,
            String templateSheetName,
            DocumentType documentType,
            List<String> warnings,
            List<FieldDecisionDto> fieldDecisions,
            Set<String> certainCells
    ) {
    }
}
