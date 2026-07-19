package com.hacom.bbnt.service;

import com.hacom.bbnt.error.ApiException;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.TemplateProfile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TemplateProfileService {
    /*
     * These addresses are the compatibility fallback verified against the
     * current BBNT workbook. They are deliberately centralized in this profile
     * resolver and are never referenced directly by TemplateCloneService.
     */
    private static final String ITEM_NUMBER_CELL = "P1";
    private static final String DOCUMENT_NUMBER_CELL = "H8";
    private static final String LOCATION_CELL = "D15";
    private static final String HEADER_DATE_CELL = "J7";
    private static final List<String> PROJECT_NAME_CELLS = List.of("E11");
    private static final List<String> PACKAGE_NAME_CELLS = List.of("E12");
    private static final List<String> PROJECT_LOCATION_CELLS = List.of("E13");
    private static final List<String> CONTRACTOR_CELLS = List.of("A5");

    public TemplateProfile resolve(Sheet sheet, MaterialFamily family, DocumentType type) {
        if (type != DocumentType.LM && type != DocumentType.GM) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Chỉ có thể tạo profile cho biểu mẫu LM hoặc GM.");
        }
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        List<String> warnings = new ArrayList<>();
        List<String> markers = new ArrayList<>();

        String titleNeedle = type == DocumentType.LM ? "lay mau thi nghiem" : "giao nhan mau thi nghiem";
        int titleRow = findRowContaining(sheet, titleNeedle, 0, Math.min(sheet.getLastRowNum(), 30), formatter);
        if (titleRow < 0) throw incompatible(sheet, "không tìm thấy tiêu đề " + titleNeedle);
        markers.add(titleNeedle + "@" + (titleRow + 1));

        String tableNeedle = type == DocumentType.LM ? "chung loai" : "vat lieu";
        int headerRow = findRowContaining(sheet, tableNeedle, 0, Math.min(sheet.getLastRowNum(), 120), formatter);
        if (headerRow < 0) throw incompatible(sheet, "không tìm thấy tiêu đề bảng " + tableNeedle);
        int dataRow = headerRow + 2;
        markers.add(tableNeedle + "@" + (headerRow + 1));

        validateRowMarker(sheet, 7, List.of("bien ban so", "so:"), "số biên bản", formatter);
        validateRowMarker(sheet, 14, List.of("vi tri"), "vị trí", formatter);
        validateRowMarker(sheet, 10, List.of("du an"), "dự án", formatter);
        validateRowMarker(sheet, 11, List.of("goi thau"), "gói thầu", formatter);
        validateRowMarker(sheet, 12, List.of("dia diem"), "địa điểm", formatter);

        Map<String, List<String>> targets = new LinkedHashMap<>();
        targets.put("itemNumber", List.of(ITEM_NUMBER_CELL));
        targets.put("documentNumber", List.of(DOCUMENT_NUMBER_CELL));
        targets.put(type == DocumentType.LM ? "lmNumber" : "gmNumber", List.of(DOCUMENT_NUMBER_CELL));
        targets.put("location", List.of(LOCATION_CELL));
        targets.put("projectName", PROJECT_NAME_CELLS);
        targets.put("packageName", PACKAGE_NAME_CELLS);
        targets.put("projectLocation", PROJECT_LOCATION_CELLS);
        targets.put("contractor", CONTRACTOR_CELLS);
        targets.put(type == DocumentType.LM ? "lmDescription" : "gmDescription", List.of(cellAddress(dataRow, 2)));
        targets.put("sequenceNumber", List.of(cellAddress(dataRow, 1)));

        List<String> uncertainRanges = new ArrayList<>();
        if (type == DocumentType.LM) {
            targets.put("sampleDate", List.of(HEADER_DATE_CELL));
            // Current LM layout: G=specimen size, J=group count, M=grade/note.
            targets.put("specimenSize", List.of(cellAddress(dataRow, 6)));
            targets.put("sampleGroupCount", List.of(cellAddress(dataRow, 9)));
            targets.put("sampleCount", List.of(cellAddress(dataRow, 9)));
            targets.put("samplesPerGroup", List.of(cellAddress(dataRow, 12)));
            targets.put("grade", List.of(cellAddress(dataRow, 12)));
            targets.put("strengthClass", List.of(cellAddress(dataRow, 12)));
            targets.put("testAge", List.of(cellAddress(dataRow, 12)));
            targets.put("note", List.of(cellAddress(dataRow, 12)));
            targets.put("testCriteria", List.of(cellAddress(dataRow, 12)));
            targets.put("standard", List.of(cellAddress(dataRow, 12)));

            addLabelTailTargets(sheet, targets, "storageLocation", "noi luu mau doi chung",
                    dataRow, dataRow + 20, 5, formatter);
            addLabelTailTargets(sheet, targets, "testPurpose", "muc dich lay mau",
                    dataRow, dataRow + 25, 4, formatter);
            addLabelTailTargets(sheet, targets, "deliveryLocation", "noi gui mau thi nghiem",
                    dataRow, dataRow + 25, 5, formatter);
            // Rows after the first sample row are template-specific additional rows.
            uncertainRanges.add(range(dataRow + 1, 1, dataRow + 2, 12));
        } else {
            // J7 in old GM sheets is commonly sampleDate+7; it is never certain by default.
            targets.put("deliveryDate", List.of(HEADER_DATE_CELL));
            targets.put("sampleGroupCount", List.of(cellAddress(dataRow, 7)));
            targets.put("sampleCount", List.of(cellAddress(dataRow, 9)));
            targets.put("samplesPerGroup", List.of(cellAddress(dataRow, 9)));
            targets.put("lmNumber", List.of(cellAddress(dataRow, 11)));
            targets.put("sampleDate", List.of(cellAddress(dataRow, 13)));
            // Only the actual data row is variable. Do not clear the structural
            // labels in the following rows (e.g. "Cơ quan yêu cầu thí nghiệm").
            targets.put("grade", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
            targets.put("strengthClass", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
            targets.put("specimenSize", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
            targets.put("testAge", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
            targets.put("note", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
            targets.put("testCriteria", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
            targets.put("standard", List.of(cellAddress(dataRow, 7), cellAddress(dataRow, 9)));
        }

        // Fields which have no stable single-cell mapping still participate in
        // FieldDecision and are sanitized by label/person scans.
        targets.putIfAbsent("deliveryTime", List.of());
        // Person/LAS values are often embedded in structural labels or drawings.
        // They are sanitized by content-aware scans instead of blindly clearing
        // the whole labelled cell.
        targets.putIfAbsent("laboratoryName", List.of());
        targets.putIfAbsent("laboratoryCode", List.of());
        targets.putIfAbsent("receiver", List.of());
        targets.putIfAbsent("laboratoryManager", List.of());

        Set<String> variableCells = new LinkedHashSet<>();
        targets.values().forEach(variableCells::addAll);
        Set<String> uncertainCells = new LinkedHashSet<>();
        for (String field : FieldDecisionService.UNCERTAIN_FIELDS) uncertainCells.addAll(targets.getOrDefault(field, List.of()));

        if (dataRow > sheet.getLastRowNum()) {
            warnings.add("Dòng dữ liệu dự kiến nằm ngoài used range hiện tại; ô sẽ được tạo khi clone.");
        }
        if (targets.getOrDefault("laboratoryName", List.of()).isEmpty()) {
            warnings.add("Không xác định được ô tên phòng thí nghiệm theo nhãn; sanitizer sẽ dùng quét nội dung bổ sung.");
        }

        return new TemplateProfile(
                family,
                type,
                sheet.getSheetName(),
                targets,
                List.copyOf(variableCells),
                List.copyOf(uncertainCells),
                List.copyOf(uncertainRanges),
                dataRow,
                markers,
                warnings
        );
    }

    private void addLabelTailTargets(
            Sheet sheet,
            Map<String, List<String>> targets,
            String field,
            String needle,
            int start,
            int end,
            int firstColumn,
            DataFormatter formatter
    ) {
        int row = findRowContaining(sheet, needle, start, Math.min(sheet.getLastRowNum(), end), formatter);
        if (row < 0) return;
        List<String> cells = new ArrayList<>();
        int maxColumn = Math.min(Math.max(sheet.getRow(row) == null ? 0 : sheet.getRow(row).getLastCellNum(), 0), 26);
        for (int column = firstColumn; column < Math.max(firstColumn + 1, maxColumn); column++) {
            cells.add(cellAddress(row, column));
        }
        targets.put(field, List.copyOf(cells));
    }

    private void validateRowMarker(
            Sheet sheet,
            int rowIndex,
            List<String> needles,
            String label,
            DataFormatter formatter
    ) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) throw incompatible(sheet, "không có dòng nhãn " + label + " tại hàng " + (rowIndex + 1));
        String text = rowText(row, formatter);
        boolean found = needles.stream().anyMatch(text::contains);
        if (!found) throw incompatible(sheet, "profile không khớp nhãn " + label + " tại hàng " + (rowIndex + 1));
    }

    private int findRowContaining(Sheet sheet, String needle, int start, int end, DataFormatter formatter) {
        String normalizedNeedle = TextNormalizer.asciiLower(needle);
        for (int rowIndex = Math.max(0, start); rowIndex <= Math.min(sheet.getLastRowNum(), end); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            if (rowText(row, formatter).contains(normalizedNeedle)) return rowIndex;
        }
        return -1;
    }

    private String rowText(Row row, DataFormatter formatter) {
        StringBuilder value = new StringBuilder();
        int maxColumn = Math.min(Math.max(row.getLastCellNum(), 0), 40);
        for (int column = 0; column < maxColumn; column++) {
            Cell cell = row.getCell(column);
            String text = TextNormalizer.asciiLower(formatter.formatCellValue(cell));
            if (!text.isBlank()) value.append(' ').append(text);
        }
        return value.toString();
    }

    private String cellAddress(int zeroBasedRow, int zeroBasedColumn) {
        return new CellReference(zeroBasedRow, zeroBasedColumn).formatAsString();
    }

    private String range(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        return new CellReference(firstRow, firstColumn).formatAsString()
                + ":" + new CellReference(lastRow, lastColumn).formatAsString();
    }

    private ApiException incompatible(Sheet sheet, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Template " + sheet.getSheetName() + " không tương thích profile: " + detail + ".");
    }
}
