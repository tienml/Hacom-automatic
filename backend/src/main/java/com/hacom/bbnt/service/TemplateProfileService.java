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
        if (type == DocumentType.MAIN) return resolveMain(sheet);
        if (type != DocumentType.LM && type != DocumentType.GM) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Chỉ có thể tạo profile cho biểu mẫu MAIN, LM hoặc GM.");
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

    /*
     * MAIN sheets (e.g. "141", "159") hold two forms stacked in one sheet: "PHIẾU YÊU CẦU
     * NGHIỆM THU CÔNG VIỆC XÂY DỰNG" followed by "BIÊN BẢN ... NGHIỆM THU CÔNG VIỆC XÂY DỰNG".
     * Addresses below were verified identical across 4 independent MAIN sheets in the current
     * workbook (141, 159, 185, 120); validateRowMarker still guards against silent drift.
     */
    private static final String MAIN_YCNT_TITLE_NEEDLE = "phieu yeu cau nghiem thu";
    private static final String MAIN_NTCV_TITLE_NEEDLE = "bien ban so";
    private static final String MAIN_REQUEST_NUMBER_CELL = "G9";
    private static final String MAIN_ACCEPTANCE_NUMBER_CELL = "H44";
    private static final String MAIN_CONTENT_CELL_FORM1 = "B17";
    private static final String MAIN_CONTENT_CELL_FORM2 = "B52";
    private static final String MAIN_LOCATION_CELL_FORM1 = "D19";
    private static final String MAIN_LOCATION_CELL_FORM2 = "D54";
    private static final String MAIN_DATE_CELL_FORM1 = "J7";
    private static final String MAIN_DATE_CELL_FORM1B = "F29";
    private static final String MAIN_DATE_CELL_FORM2 = "I43";
    private static final String MAIN_PROJECT_NAME_CELL_FORM2 = "E47";
    private static final String MAIN_PACKAGE_NAME_CELL_FORM2 = "E48";
    private static final String MAIN_PROJECT_LOCATION_CELL_FORM2 = "E49";
    private static final String MAIN_CONTRACTOR_CELL_FORM2 = "A42";

    private TemplateProfile resolveMain(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        List<String> warnings = new ArrayList<>();
        List<String> markers = new ArrayList<>();

        int titleRow = findRowContaining(sheet, MAIN_YCNT_TITLE_NEEDLE, 0, Math.min(sheet.getLastRowNum(), 15), formatter);
        if (titleRow < 0) throw incompatible(sheet, "không tìm thấy tiêu đề " + MAIN_YCNT_TITLE_NEEDLE);
        markers.add(MAIN_YCNT_TITLE_NEEDLE + "@" + (titleRow + 1));
        validateRowMarker(sheet, titleRow + 1, List.of("so:"), "số phiếu yêu cầu", formatter);
        validateRowMarker(sheet, titleRow + 3, List.of("du an"), "dự án (phiếu)", formatter);
        validateRowMarker(sheet, titleRow + 7, List.of("doi tuong nghiem thu"), "đối tượng nghiệm thu (phiếu)", formatter);
        validateRowMarker(sheet, titleRow + 11, List.of("vi tri"), "vị trí (phiếu)", formatter);

        int recordRow = findRowContaining(sheet, MAIN_NTCV_TITLE_NEEDLE, titleRow + 20,
                Math.min(sheet.getLastRowNum(), titleRow + 60), formatter);
        if (recordRow < 0) throw incompatible(sheet, "không tìm thấy tiêu đề " + MAIN_NTCV_TITLE_NEEDLE);
        markers.add(MAIN_NTCV_TITLE_NEEDLE + "@" + (recordRow + 1));
        validateRowMarker(sheet, recordRow + 3, List.of("du an"), "dự án (biên bản)", formatter);
        validateRowMarker(sheet, recordRow + 7, List.of("doi tuong nghiem thu"), "đối tượng nghiệm thu (biên bản)", formatter);
        validateRowMarker(sheet, recordRow + 10, List.of("vi tri"), "vị trí (biên bản)", formatter);

        Map<String, List<String>> targets = new LinkedHashMap<>();
        targets.put("itemNumber", List.of(ITEM_NUMBER_CELL));
        targets.put("workContent", List.of(MAIN_CONTENT_CELL_FORM1, MAIN_CONTENT_CELL_FORM2));
        targets.put("location", List.of(MAIN_LOCATION_CELL_FORM1, MAIN_LOCATION_CELL_FORM2));
        targets.put("acceptanceDateTime", List.of(MAIN_DATE_CELL_FORM1, MAIN_DATE_CELL_FORM1B, MAIN_DATE_CELL_FORM2));
        targets.put("acceptanceNumber", List.of(MAIN_ACCEPTANCE_NUMBER_CELL));
        targets.put("requestNumber", List.of(MAIN_REQUEST_NUMBER_CELL));
        targets.put("projectName", List.of(PROJECT_NAME_CELLS.get(0), MAIN_PROJECT_NAME_CELL_FORM2));
        targets.put("packageName", List.of(PACKAGE_NAME_CELLS.get(0), MAIN_PACKAGE_NAME_CELL_FORM2));
        targets.put("projectLocation", List.of(PROJECT_LOCATION_CELLS.get(0), MAIN_PROJECT_LOCATION_CELL_FORM2));
        targets.put("contractor", List.of(CONTRACTOR_CELLS.get(0), MAIN_CONTRACTOR_CELL_FORM2));

        Set<String> variableCells = new LinkedHashSet<>();
        targets.values().forEach(variableCells::addAll);

        return new TemplateProfile(
                MaterialFamily.UNKNOWN,
                DocumentType.MAIN,
                sheet.getSheetName(),
                targets,
                List.copyOf(variableCells),
                List.of(),
                List.of(),
                -1,
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
