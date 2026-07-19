package com.hacom.bbnt.service;

import com.hacom.bbnt.dto.FieldDecisionDto;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.DataCertainty;
import com.hacom.bbnt.model.FieldAction;
import com.hacom.bbnt.model.TemplateProfile;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFShape;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFTextbox;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GeneratedSheetValidator {
    public List<String> validate(
            Sheet sheet,
            WorkItemDto targetItem,
            WorkItemDto templateItem,
            TemplateProfile profile,
            List<FieldDecisionDto> decisions,
            int templateDrawingCount,
            int cloneDrawingCount,
            boolean templateHadPrintArea,
            boolean cloneHasPrintArea
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        Set<String> certainCells = certainCells(decisions);
        Set<String> oldExactValues = staleExactValues(templateItem);
        String oldItemNumber = templateItem == null ? "" : TextNormalizer.compact(templateItem.itemNumber());

        for (Row row : sheet) {
            for (Cell cell : row) {
                String address = cell.getAddress().formatAsString();
                if (cell.getCellType() == CellType.FORMULA) {
                    errors.add(address + " còn công thức template: " + cell.getCellFormula());
                    continue;
                }
                if (cell.getCellType() == CellType.ERROR) {
                    errors.add(address + " còn ô lỗi Excel.");
                    continue;
                }
                String value = formatter.formatCellValue(cell);
                String compactValue = TextNormalizer.compact(value);
                String upper = value.toUpperCase(Locale.ROOT);
                for (String token : TemplateDataPatterns.ERROR_TOKENS) {
                    if (upper.contains(token)) errors.add(address + " còn token lỗi " + token + ".");
                }
                if (!certainCells.contains(address) && TemplateDataPatterns.UNCERTAIN_VALUE.matcher(value).find()) {
                    errors.add(address + " còn thông số template chưa chắc chắn: " + value);
                }
                if (!certainCells.contains(address)
                        && !compactValue.isBlank()
                        && oldExactValues.stream().anyMatch(old -> old.equalsIgnoreCase(compactValue))) {
                    errors.add(address + " còn dữ liệu biến đổi của template cũ: " + compactValue);
                }
                if (!certainCells.contains(address) && TemplateDataPatterns.RECORD_NUMBER.matcher(compactValue).matches()) {
                    String targetLm = documentNumber(targetItem, "LM");
                    String targetGm = documentNumber(targetItem, "GM");
                    if (!compactValue.equalsIgnoreCase(targetLm) && !compactValue.equalsIgnoreCase(targetGm)) {
                        errors.add(address + " còn số hồ sơ cũ/không được xác nhận: " + compactValue);
                    }
                }
                if (!oldItemNumber.isBlank() && !oldItemNumber.equalsIgnoreCase(targetItem.itemNumber())) {
                    if (upper.contains("/LM/" + oldItemNumber.toUpperCase(Locale.ROOT))
                            || upper.contains("/GM/" + oldItemNumber.toUpperCase(Locale.ROOT))) {
                        errors.add(address + " còn số LM/GM của template cũ.");
                    }
                }
            }
        }

        validateUncertainCells(sheet, profile, certainCells, formatter, errors);
        validateUncertainRanges(sheet, profile, certainCells, formatter, errors);
        validateHeaderFooter(sheet.getHeader(), "header", oldExactValues, errors);
        validateHeaderFooter(sheet.getFooter(), "footer", oldExactValues, errors);
        validateDrawingText(sheet, oldExactValues, errors, warnings);
        validateMergedRegions(sheet, errors);
        validateSheetName(sheet.getSheetName(), errors);

        if (templateDrawingCount != cloneDrawingCount) {
            errors.add("Drawing/logo clone không khớp template: template=" + templateDrawingCount
                    + ", clone=" + cloneDrawingCount + ".");
        }
        if (!templateHadPrintArea) {
            errors.add("Template nguồn không có print area; không đủ điều kiện tạo hồ sơ an toàn.");
        } else if (!cloneHasPrintArea) {
            errors.add("Template có print area nhưng sheet clone bị mất print area.");
        }
        if (cloneDrawingCount > 0) {
            warnings.add("Drawing count đã được đối chiếu; shape không hỗ trợ đọc text được ghi cảnh báo thay vì bỏ qua âm thầm.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Sheet mới " + sheet.getSheetName() + " không đạt validation: "
                            + String.join("; ", errors.stream().limit(20).toList())
            );
        }
        return List.copyOf(new LinkedHashSet<>(warnings));
    }

    private Set<String> certainCells(List<FieldDecisionDto> decisions) {
        Set<String> cells = new LinkedHashSet<>();
        if (decisions == null) return cells;
        decisions.stream()
                .filter(decision -> decision.certainty() == DataCertainty.CERTAIN)
                .filter(decision -> decision.action() == FieldAction.POPULATE)
                .forEach(decision -> cells.addAll(decision.targetCells()));
        return cells;
    }

    private void validateUncertainCells(
            Sheet sheet,
            TemplateProfile profile,
            Set<String> certainCells,
            DataFormatter formatter,
            List<String> errors
    ) {
        for (String address : profile.uncertainCells()) {
            if (certainCells.contains(address)) continue;
            CellReference reference = new CellReference(address);
            Row row = sheet.getRow(reference.getRow());
            Cell cell = row == null ? null : row.getCell(reference.getCol());
            String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
            if (!value.isBlank()) errors.add(address + " thuộc field UNCERTAIN nhưng chưa blank: " + value);
        }
    }

    private void validateUncertainRanges(
            Sheet sheet,
            TemplateProfile profile,
            Set<String> certainCells,
            DataFormatter formatter,
            List<String> errors
    ) {
        for (String rangeText : profile.uncertainRanges()) {
            CellRangeAddress range = CellRangeAddress.valueOf(rangeText);
            for (int rowIndex = range.getFirstRow(); rowIndex <= range.getLastRow(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                for (int column = range.getFirstColumn(); column <= range.getLastColumn(); column++) {
                    Cell cell = row.getCell(column);
                    if (cell == null || certainCells.contains(cell.getAddress().formatAsString())) continue;
                    String value = formatter.formatCellValue(cell).trim();
                    if (!value.isBlank()) {
                        errors.add(cell.getAddress() + " thuộc vùng UNCERTAIN nhưng chưa blank: " + value);
                    }
                }
            }
        }
    }

    private void validateHeaderFooter(Object source, String label, Set<String> staleValues, List<String> errors) {
        String text;
        if (source instanceof Header header) text = header.getLeft() + " " + header.getCenter() + " " + header.getRight();
        else if (source instanceof Footer footer) text = footer.getLeft() + " " + footer.getCenter() + " " + footer.getRight();
        else return;
        validateExternalText(text, label, staleValues, errors);
    }

    private void validateDrawingText(
            Sheet sheet,
            Set<String> staleValues,
            List<String> errors,
            List<String> warnings
    ) {
        if (sheet instanceof XSSFSheet xssfSheet) {
            XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
            if (drawing == null) return;
            int unsupported = 0;
            for (XSSFShape shape : drawing.getShapes()) {
                if (shape instanceof XSSFSimpleShape simpleShape) {
                    validateExternalText(simpleShape.getText(), "drawing/text box", staleValues, errors);
                } else {
                    unsupported++;
                }
            }
            if (unsupported > 0) warnings.add("Có " + unsupported + " XSSF drawing(s) không có API text để quét nội dung.");
            return;
        }
        if (sheet instanceof HSSFSheet hssfSheet) {
            HSSFPatriarch patriarch = hssfSheet.getDrawingPatriarch();
            if (patriarch == null) return;
            int unsupported = 0;
            for (HSSFShape shape : patriarch.getChildren()) {
                if (shape instanceof HSSFTextbox textbox) {
                    validateExternalText(textbox.getString() == null ? "" : textbox.getString().getString(),
                            "drawing/text box", staleValues, errors);
                } else {
                    unsupported++;
                }
            }
            if (unsupported > 0) warnings.add("Có " + unsupported + " HSSF drawing(s) không có API text để quét nội dung.");
        }
    }

    private void validateExternalText(String text, String label, Set<String> staleValues, List<String> errors) {
        String compact = TextNormalizer.compact(text);
        String upper = compact.toUpperCase(Locale.ROOT);
        for (String token : TemplateDataPatterns.ERROR_TOKENS) {
            if (upper.contains(token)) errors.add(label + " còn token lỗi " + token + ".");
        }
        if (TemplateDataPatterns.UNCERTAIN_VALUE.matcher(compact).find()) {
            errors.add(label + " còn thông số template chưa chắc chắn: " + compact);
        }
        for (String stale : staleValues) {
            if (stale.length() >= 4 && containsIgnoreCase(compact, stale)) {
                errors.add(label + " còn dữ liệu template cũ: " + stale);
                break;
            }
        }
    }

    private boolean containsIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private Set<String> staleExactValues(WorkItemDto templateItem) {
        if (templateItem == null) return Set.of();
        Set<String> values = new LinkedHashSet<>();
        add(values, templateItem.itemNumber());
        add(values, templateItem.content());
        add(values, templateItem.position());
        add(values, templateItem.inspectionTime());
        add(values, templateItem.recordNumber());
        add(values, templateItem.sampleDate());
        add(values, documentNumber(templateItem, "LM"));
        add(values, documentNumber(templateItem, "GM"));
        add(values, description("Lấy mẫu ", templateItem));
        add(values, description("Mẫu ", templateItem));
        return Set.copyOf(values);
    }

    private String description(String prefix, WorkItemDto item) {
        String content = item.content() == null ? "" : item.content().trim().replaceFirst("(?iu)^chất\\s+lượng\\s+", "");
        String location = item.position() == null || item.position().isBlank() ? "" : " (" + item.position().trim() + ")";
        return prefix + content + location;
    }

    private String documentNumber(WorkItemDto item, String type) {
        if (item == null || item.recordNumber() == null) return "";
        String[] parts = item.recordNumber().split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("NTCV") || parts[i].equalsIgnoreCase("YCNT")
                    || parts[i].equalsIgnoreCase("LM") || parts[i].equalsIgnoreCase("GM")) {
                parts[i] = type;
                return String.join("/", parts);
            }
        }
        return "";
    }

    private void add(Set<String> values, String value) {
        String compact = TextNormalizer.compact(value);
        if (!compact.isBlank()) values.add(compact);
    }

    private void validateSheetName(String name, List<String> errors) {
        try {
            WorkbookUtil.validateSheetName(name);
        } catch (IllegalArgumentException exception) {
            errors.add("Tên sheet không hợp lệ: " + exception.getMessage());
        }
    }

    private void validateMergedRegions(Sheet sheet, List<String> errors) {
        for (int left = 0; left < sheet.getNumMergedRegions(); left++) {
            CellRangeAddress a = sheet.getMergedRegion(left);
            for (int right = left + 1; right < sheet.getNumMergedRegions(); right++) {
                CellRangeAddress b = sheet.getMergedRegion(right);
                if (overlaps(a, b)) {
                    errors.add("Merged regions chồng lấn: " + a.formatAsString() + " và " + b.formatAsString());
                    return;
                }
            }
        }
    }

    private boolean overlaps(CellRangeAddress a, CellRangeAddress b) {
        return a.getFirstRow() <= b.getLastRow()
                && b.getFirstRow() <= a.getLastRow()
                && a.getFirstColumn() <= b.getLastColumn()
                && b.getFirstColumn() <= a.getLastColumn();
    }
}
