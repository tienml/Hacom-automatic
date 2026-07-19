package com.hacom.bbnt;

import com.hacom.bbnt.dto.GenerateOutputSelection;
import com.hacom.bbnt.dto.GenerateRequest;
import com.hacom.bbnt.dto.GenerateSelection;
import com.hacom.bbnt.dto.OutputSheetDto;
import com.hacom.bbnt.dto.WorkItemDto;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RealWorkbookIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void analyzesRealWorkbookAndProvesMainOnlyCanExportMainLmGm() throws Exception {
        Path workbookPath = configuredWorkbook();
        boolean createPdf = Boolean.getBoolean("bbnt.test.pdf");
        TestServices services = TestServices.create(temporaryDirectory, createPdf);
        var context = services.analysis().analyze(new MockMultipartFile(
                "file",
                workbookPath.getFileName().toString(),
                workbookContentType(workbookPath),
                Files.readAllBytes(workbookPath)
        ));
        byte[] sourceBefore = Files.readAllBytes(context.sourcePath());

        assertThat(context.dmSheetName()).isEqualTo("DM ");
        assertThat(context.workItems()).hasSize(198);
        assertThat(context.workItem("1").localOrder()).isEqualTo("1");
        assertThat(context.analysisWarnings()).noneMatch(value -> value.contains("fallback cột B"));
        assertThat(context.templateRegistry().pairsFor(MaterialFamily.VUA)).isNotEmpty();
        assertThat(context.templateRegistry().pairsFor(MaterialFamily.BETONG)).isNotEmpty();
        assertThat(context.templateRegistry().pairFor(MaterialFamily.VUA).usable()).isTrue();
        assertThat(context.templateRegistry().pairFor(MaterialFamily.BETONG).usable()).isTrue();

        WorkItemDto fullPair = context.workItems().stream()
                .filter(WorkItemDto::hasMainSheet)
                .filter(WorkItemDto::hasCompleteSamplePair)
                .findFirst().orElseThrow();
        WorkItemDto mainOnly = context.workItems().stream()
                .filter(WorkItemDto::hasMainSheet)
                .filter(item -> !item.hasLmSheet() && !item.hasGmSheet())
                .filter(item -> item.materialFamily() != MaterialFamily.UNKNOWN)
                .filter(item -> item.lmPlan().availability() == OutputAvailability.GENERATABLE)
                .filter(item -> item.gmPlan().availability() == OutputAvailability.GENERATABLE)
                .findFirst().orElseThrow();
        WorkItemDto noSheetMortar = context.workItems().stream()
                .filter(item -> !item.hasMainSheet() && !item.hasLmSheet() && !item.hasGmSheet())
                .filter(item -> item.materialFamily() == MaterialFamily.VUA)
                .findFirst().orElseThrow();
        WorkItemDto noSheetConcrete = context.workItems().stream()
                .filter(item -> !item.hasMainSheet() && !item.hasLmSheet() && !item.hasGmSheet())
                .filter(item -> item.materialFamily() == MaterialFamily.BETONG)
                .findFirst().orElseThrow();
        WorkItemDto unknown = context.workItems().stream()
                .filter(item -> item.materialFamily() == MaterialFamily.UNKNOWN)
                .findFirst().orElseThrow();

        assertThat(fullPair.mainPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(fullPair.lmPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(fullPair.gmPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(noSheetMortar.lmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(noSheetConcrete.lmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(unknown.requiresTemplateSelection()).isTrue();

        SheetSnapshot mainBefore = snapshotSheet(context.sourcePath(), mainOnly.mainPlan().existingSheetName());

        List<OutputSheetDto> outputs = services.outputs().outputs(context.id(), mainOnly.itemNumber(), mainOnly.materialFamily());
        assertThat(outputs).extracting(OutputSheetDto::documentType)
                .containsExactly(DocumentType.MAIN, DocumentType.LM, DocumentType.GM);
        assertThat(outputs).extracting(OutputSheetDto::generationMode)
                .containsExactly(GenerationMode.EXISTING_SHEET, GenerationMode.CLONE_TEMPLATE, GenerationMode.CLONE_TEMPLATE);
        assertThat(outputs).allSatisfy(output -> assertThat(output.available()).isTrue());

        List<GenerateOutputSelection> selected = outputs.stream().map(output -> new GenerateOutputSelection(
                output.sheetName(), output.documentType(), output.generationMode(), output.sourceTemplate()
        )).toList();
        var generated = services.generation().generate(context.id(), new GenerateRequest(
                List.of(new GenerateSelection(mainOnly.itemNumber(), selected, mainOnly.materialFamily())),
                createPdf
        )).document();

        assertThat(generated.excelPath()).exists().isNotEmptyFile();
        assertThat(generated.selectedSheets()).containsExactly(
                mainOnly.mainPlan().existingSheetName(),
                mainOnly.lmPlan().plannedSheetName(),
                mainOnly.gmPlan().plannedSheetName()
        );
        assertThat(Files.readAllBytes(context.sourcePath())).isEqualTo(sourceBefore);

        String configuredOutput = System.getProperty("bbnt.test.output", "").trim();
        if (!configuredOutput.isBlank()) {
            Path target = Path.of(configuredOutput);
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.copy(generated.excelPath(), target, StandardCopyOption.REPLACE_EXISTING);
        }

        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            Sheet main = workbook.getSheet(mainOnly.mainPlan().existingSheetName());
            Sheet lm = workbook.getSheet(mainOnly.lmPlan().plannedSheetName());
            Sheet gm = workbook.getSheet(mainOnly.gmPlan().plannedSheetName());
            assertThat(main).isNotNull();
            assertSheetSnapshotEquals(mainBefore, snapshotSheet(workbook, main));
            assertThat(lm).isNotNull();
            assertThat(gm).isNotNull();
            assertThat(workbook.getSheetName(0)).isEqualTo(main.getSheetName());
            assertThat(workbook.getSheetName(1)).isEqualTo(lm.getSheetName());
            assertThat(workbook.getSheetName(2)).isEqualTo(gm.getSheetName());
            assertThat(allText(lm)).contains(mainOnly.position()).doesNotContain("#REF!", "Ngoài nhà", "40x40x160", "150x150x150");
            assertThat(allText(gm)).contains(mainOnly.position()).doesNotContain("#REF!", "Ngoài nhà", "40x40x160", "150x150x150");
            assertNoFormulaOrError(lm);
            assertNoFormulaOrError(gm);
            assertThat(drawingCount(lm)).isEqualTo(drawingCount(workbook.getSheet(outputs.get(1).sourceTemplate())));
            assertThat(drawingCount(gm)).isEqualTo(drawingCount(workbook.getSheet(outputs.get(2).sourceTemplate())));
            assertPrintSettingsEqual(workbook, workbook.getSheet(outputs.get(1).sourceTemplate()), lm);
            assertPrintSettingsEqual(workbook, workbook.getSheet(outputs.get(2).sourceTemplate()), gm);
        }

        if (createPdf) {
            assertThat(generated.pdfPath()).exists().isNotEmptyFile();
            byte[] signature = Files.readAllBytes(generated.pdfPath());
            assertThat(signature).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        }
    }


    private String workbookContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xls")
                ? "application/vnd.ms-excel"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private SheetSnapshot snapshotSheet(Path workbookPath, String sheetName) throws Exception {
        try (var input = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(input)) {
            return snapshotSheet(workbook, workbook.getSheet(sheetName));
        }
    }

    private SheetSnapshot snapshotSheet(Workbook workbook, Sheet sheet) {
        assertThat(sheet).as("Existing sheet must exist").isNotNull();
        List<String> cells = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        int maximumColumn = 0;
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                rows.add(rowIndex + "|missing");
                continue;
            }
            rows.add(rowIndex + "|height=" + row.getHeight() + "|hidden=" + row.getZeroHeight()
                    + "|style=" + styleFingerprint(workbook, row.getRowStyle()));
            int lastCell = Math.max(0, row.getLastCellNum());
            maximumColumn = Math.max(maximumColumn, lastCell);
            for (int columnIndex = 0; columnIndex < lastCell; columnIndex++) {
                Cell cell = row.getCell(columnIndex);
                if (cell == null) {
                    cells.add(rowIndex + ":" + columnIndex + "|missing");
                    continue;
                }
                String value = switch (cell.getCellType()) {
                    case STRING -> cell.getRichStringCellValue().getString();
                    case NUMERIC -> Double.toString(cell.getNumericCellValue());
                    case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                    case FORMULA -> cell.getCellFormula() + "|cached=" + cachedFormulaValue(cell);
                    case ERROR -> Byte.toString(cell.getErrorCellValue());
                    case BLANK, _NONE -> "";
                };
                cells.add(rowIndex + ":" + columnIndex + "|type=" + cell.getCellType()
                        + "|value=" + value + "|style=" + styleFingerprint(workbook, cell.getCellStyle()));
            }
        }
        for (int columnIndex = 0; columnIndex < maximumColumn; columnIndex++) {
            columns.add(columnIndex + "|width=" + sheet.getColumnWidth(columnIndex)
                    + "|hidden=" + sheet.isColumnHidden(columnIndex));
        }
        List<String> merged = new ArrayList<>();
        for (int index = 0; index < sheet.getNumMergedRegions(); index++) {
            merged.add(sheet.getMergedRegion(index).formatAsString());
        }
        PrintSetup print = sheet.getPrintSetup();
        String printArea = normalizePrintArea(workbook.getPrintArea(workbook.getSheetIndex(sheet)));
        String printSettings = "area=" + printArea
                + "|paper=" + print.getPaperSize()
                + "|landscape=" + print.getLandscape()
                + "|fitWidth=" + print.getFitWidth()
                + "|fitHeight=" + print.getFitHeight()
                + "|fitToPage=" + sheet.getFitToPage()
                + "|header=" + sheet.getHeader().getLeft() + "\u001f" + sheet.getHeader().getCenter() + "\u001f" + sheet.getHeader().getRight()
                + "|footer=" + sheet.getFooter().getLeft() + "\u001f" + sheet.getFooter().getCenter() + "\u001f" + sheet.getFooter().getRight();
        return new SheetSnapshot(cells, rows, columns, merged, printSettings, drawingCount(sheet));
    }

    private String cachedFormulaValue(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> "S:" + cell.getStringCellValue();
                case NUMERIC -> "N:" + cell.getNumericCellValue();
                case BOOLEAN -> "B:" + cell.getBooleanCellValue();
                case ERROR -> "E:" + cell.getErrorCellValue();
                case BLANK, _NONE, FORMULA -> "";
            };
        } catch (RuntimeException exception) {
            return "unreadable";
        }
    }

    private void assertSheetSnapshotEquals(SheetSnapshot expected, SheetSnapshot actual) {
        assertListEquals("cells", expected.cells(), actual.cells());
        assertListEquals("rows", expected.rows(), actual.rows());
        assertListEquals("columns", expected.columns(), actual.columns());
        assertListEquals("merged regions", expected.mergedRegions(), actual.mergedRegions());
        assertThat(actual.printSettings()).as("existing sheet print settings").isEqualTo(expected.printSettings());
        assertThat(actual.drawingCount()).as("existing sheet drawing count").isEqualTo(expected.drawingCount());
    }

    private void assertListEquals(String label, List<String> expected, List<String> actual) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(label + " size changed: expected " + expected.size() + " but was " + actual.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).equals(actual.get(index))) {
                throw new AssertionError(label + " changed at index " + index
                        + "\nexpected: " + expected.get(index)
                        + "\nactual:   " + actual.get(index));
            }
        }
    }

    private String styleFingerprint(Workbook workbook, CellStyle style) {
        if (style == null) return "none";
        Font font = workbook.getFontAt(style.getFontIndexAsInt());
        return "fmt=" + style.getDataFormatString()
                + ",align=" + style.getAlignment() + "/" + style.getVerticalAlignment()
                + ",wrap=" + style.getWrapText()
                + ",rotation=" + style.getRotation()
                + ",indent=" + style.getIndention()
                + ",fill=" + style.getFillPattern() + "/" + style.getFillForegroundColor() + "/" + style.getFillBackgroundColor()
                + ",border=" + style.getBorderTop() + "/" + style.getBorderRight() + "/" + style.getBorderBottom() + "/" + style.getBorderLeft()
                + ",borderColor=" + style.getTopBorderColor() + "/" + style.getRightBorderColor() + "/" + style.getBottomBorderColor() + "/" + style.getLeftBorderColor()
                + ",locked=" + style.getLocked() + ",hidden=" + style.getHidden()
                + ",font=" + font.getFontName() + "/" + font.getFontHeight() + "/" + font.getBold()
                + "/" + font.getItalic() + "/" + font.getUnderline() + "/" + font.getStrikeout()
                + "/" + font.getColor() + "/" + font.getTypeOffset();
    }

    private record SheetSnapshot(
            List<String> cells,
            List<String> rows,
            List<String> columns,
            List<String> mergedRegions,
            String printSettings,
            int drawingCount
    ) {
    }

    private Path configuredWorkbook() {
        String configured = System.getProperty("bbnt.test.workbook", "").trim();
        Assumptions.assumeTrue(!configured.isBlank(),
                "Set -Dbbnt.test.workbook=/path/to/BBNT.xlsx to run the actual-workbook test");
        Path path = Path.of(configured);
        Assumptions.assumeTrue(Files.isRegularFile(path), "Workbook fixture does not exist: " + path);
        return path;
    }

    private void assertNoFormulaOrError(Sheet sheet) {
        for (Row row : sheet) for (Cell cell : row) {
            assertThat(cell.getCellType()).as(sheet.getSheetName() + "!" + cell.getAddress())
                    .isNotIn(CellType.FORMULA, CellType.ERROR);
        }
    }

    private String allText(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        StringBuilder text = new StringBuilder();
        for (Row row : sheet) for (Cell cell : row) text.append(formatter.formatCellValue(cell)).append('\n');
        return text.toString();
    }

    private void assertPrintSettingsEqual(Workbook workbook, Sheet source, Sheet clone) {
        assertThat(source).isNotNull();
        PrintSetup expected = source.getPrintSetup();
        PrintSetup actual = clone.getPrintSetup();
        assertThat(actual.getPaperSize()).isEqualTo(expected.getPaperSize());
        assertThat(actual.getFitWidth()).isEqualTo(expected.getFitWidth());
        assertThat(actual.getFitHeight()).isEqualTo(expected.getFitHeight());
        assertThat(actual.getLandscape()).isEqualTo(expected.getLandscape());
        assertThat(clone.getFitToPage()).isEqualTo(source.getFitToPage());
        for (PageMargin margin : PageMargin.values()) assertThat(clone.getMargin(margin)).isEqualTo(source.getMargin(margin));
        assertThat(clone.getHeader().getCenter()).isEqualTo(source.getHeader().getCenter());
        assertThat(clone.getFooter().getRight()).isEqualTo(source.getFooter().getRight());
        assertThat(normalizePrintArea(workbook.getPrintArea(workbook.getSheetIndex(clone))))
                .isEqualTo(normalizePrintArea(workbook.getPrintArea(workbook.getSheetIndex(source))));
    }

    private String normalizePrintArea(String printArea) {
        if (printArea == null) return null;
        int separator = printArea.indexOf('!');
        return separator >= 0 ? printArea.substring(separator + 1) : printArea;
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
}
