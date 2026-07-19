package com.hacom.bbnt;

import com.hacom.bbnt.dto.GenerateOutputSelection;
import com.hacom.bbnt.dto.GenerateRequest;
import com.hacom.bbnt.dto.GenerateSelection;
import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.GenerationMode;
import com.hacom.bbnt.model.MaterialFamily;
import com.hacom.bbnt.model.OutputAvailability;
import com.hacom.bbnt.model.WorkItemSheetStatus;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFPatriarch;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelWorkflowTest {
    @TempDir
    Path tempDirectory;

    @Test
    void plansDocumentsIndependentlyForAllExistingCombinations() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));

        var full = context.workItem("111");
        assertThat(full.mainPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(full.lmPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(full.gmPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(full.sheetStatus()).isEqualTo(WorkItemSheetStatus.COMPLETE_SAMPLE_PAIR);

        var mainOnly = context.workItem("150");
        assertThat(mainOnly.localOrder()).isEqualTo("4");
        assertThat(mainOnly.mainPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(mainOnly.lmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(mainOnly.gmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(mainOnly.sheetStatus()).isEqualTo(WorkItemSheetStatus.MAIN_ONLY);

        var missingGm = context.workItem("151");
        assertThat(missingGm.mainPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(missingGm.lmPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(missingGm.gmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(missingGm.sheetStatus()).isEqualTo(WorkItemSheetStatus.MISSING_GM);

        var missingLm = context.workItem("152");
        assertThat(missingLm.mainPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(missingLm.lmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(missingLm.gmPlan().availability()).isEqualTo(OutputAvailability.EXISTING);
        assertThat(missingLm.sheetStatus()).isEqualTo(WorkItemSheetStatus.MISSING_LM);

        var noSheets = context.workItem("200");
        assertThat(noSheets.mainPlan().availability()).isEqualTo(OutputAvailability.NOT_APPLICABLE);
        assertThat(noSheets.lmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(noSheets.gmPlan().availability()).isEqualTo(OutputAvailability.GENERATABLE);
        assertThat(noSheets.sheetStatus()).isEqualTo(WorkItemSheetStatus.NO_SHEETS);
    }

    @Test
    void outputsForMainOnlyMergeMainExistingWithLmAndGmPlans() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLS), "sample.xls"));

        var outputs = services.outputs().outputs(context.id(), "150", null);
        assertThat(outputs).extracting(output -> output.sheetName())
                .containsExactly("150", "1.LMV (150)", "1.GMV (150)");
        assertThat(outputs).extracting(output -> output.generationMode())
                .containsExactly(GenerationMode.EXISTING_SHEET, GenerationMode.CLONE_TEMPLATE, GenerationMode.CLONE_TEMPLATE);
        assertThat(outputs.get(1).sourceTemplate()).isEqualTo("1.LMV (111)");
        assertThat(outputs.get(2).sourceTemplate()).isEqualTo("1.GMV (111)");
        assertThat(outputs.get(1).availableSourceTemplates()).contains("1.LMV (111)", "1.LMV (114)");
    }

    @Test
    void unknownMaterialKeepsExistingMainButRequiresFamilyForGeneratedDocuments() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));
        var unknown = context.workItem("202");
        assertThat(unknown.materialFamily()).isEqualTo(MaterialFamily.UNKNOWN);
        assertThat(unknown.requiresTemplateSelection()).isTrue();
        assertThat(services.outputs().outputs(context.id(), "202", null)).isEmpty();
        assertThat(services.outputs().outputs(context.id(), "202", MaterialFamily.VUA))
                .extracting(output -> output.sheetName())
                .containsExactly("1.LMV (202)", "1.GMV (202)");
    }

    @Test
    void supportsOnlyLmOnlyGmAndAlternateTemplateSelection() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));

        var lmOnly = services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                cloneOutput("1.LMV (200)", DocumentType.LM, "1.LMV (114)"))).document();
        assertThat(lmOnly.selectedSheets()).containsExactly("1.LMV (200)");
        try (Workbook workbook = WorkbookFactory.create(lmOnly.excelPath().toFile())) {
            assertThat(workbook.getSheet("1.LMV (200)")).isNotNull();
            assertThat(workbook.getSheetIndex("1.LMV (200)")).isZero();
        }

        var gmOnly = services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                cloneOutput("1.GMV (200)", DocumentType.GM, "1.GMV (114)"))).document();
        assertThat(gmOnly.selectedSheets()).containsExactly("1.GMV (200)");
    }

    @Test
    void mixedSelectionWithinMainOnlyItemExportsMainLmGmInOrder() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));
        byte[] sourceBefore = Files.readAllBytes(context.sourcePath());

        var generated = services.generation().generate(context.id(), request("150", MaterialFamily.VUA,
                existingOutput("150", DocumentType.MAIN),
                cloneOutput("1.LMV (150)", DocumentType.LM, "1.LMV (111)"),
                cloneOutput("1.GMV (150)", DocumentType.GM, "1.GMV (111)"))).document();

        assertThat(generated.selectedSheets()).containsExactly("150", "1.LMV (150)", "1.GMV (150)");
        assertThat(Files.readAllBytes(context.sourcePath())).isEqualTo(sourceBefore);
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            assertThat(workbook.getSheetName(0)).isEqualTo("150");
            assertThat(workbook.getSheetName(1)).isEqualTo("1.LMV (150)");
            assertThat(workbook.getSheetName(2)).isEqualTo("1.GMV (150)");
            assertThat(workbook.isSheetHidden(0)).isFalse();
            assertThat(workbook.isSheetHidden(1)).isFalse();
            assertThat(workbook.isSheetHidden(2)).isFalse();
        }
    }

    @Test
    void existingSheetIsCellForCellReadOnlyAndDoesNotForceRecalculation() throws Exception {
        byte[] workbookBytes = mutateWorkbook(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), workbook -> {
            Sheet main = workbook.getSheet("150");
            main.getRow(0).getCell(15).setCellValue("P1-DO-NOT-TOUCH");
            main.getRow(1).createCell(1).setCellFormula("1+1");
        });
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(workbookBytes, "sample.xlsx"));

        byte[] sourceBefore = Files.readAllBytes(context.sourcePath());
        SheetSnapshot before;
        try (var input = Files.newInputStream(context.sourcePath());
             Workbook source = WorkbookFactory.create(input)) {
            before = snapshot(source, source.getSheet("150"));
        }

        var generated = services.generation().generate(context.id(), request("150", MaterialFamily.VUA,
                existingOutput("150", DocumentType.MAIN))).document();

        assertThat(Files.readAllBytes(context.sourcePath())).isEqualTo(sourceBefore);
        try (Workbook result = WorkbookFactory.create(generated.excelPath().toFile())) {
            assertThat(snapshot(result, result.getSheet("150"))).isEqualTo(before);
            assertThat(text(result.getSheet("150").getRow(0).getCell(15))).isEqualTo("P1-DO-NOT-TOUCH");
            assertThat(result.getSheet("150").getRow(1).getCell(1).getCellFormula()).isEqualTo("1+1");
        }
    }

    @Test
    void hssfCloneDoesNotRewriteFormulaOnExistingMainSheet() throws Exception {
        byte[] workbookBytes = mutateWorkbook(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLS), workbook -> {
            Sheet main = workbook.getSheet("150");
            Row row = main.getRow(6);
            if (row == null) row = main.createRow(6);
            Cell formula = row.getCell(9);
            if (formula == null) formula = row.createCell(9);
            formula.setCellFormula("'DM '!A1");
        });
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(workbookBytes, "formula-protection.xls"));
        byte[] sourceBefore = Files.readAllBytes(context.sourcePath());

        var generated = services.generation().generate(context.id(), request("150", MaterialFamily.VUA,
                existingOutput("150", DocumentType.MAIN),
                cloneOutput("1.LMV (150)", DocumentType.LM, "1.LMV (111)"),
                cloneOutput("1.GMV (150)", DocumentType.GM, "1.GMV (111)"))).document();

        assertThat(Files.readAllBytes(context.sourcePath())).isEqualTo(sourceBefore);
        try (Workbook result = WorkbookFactory.create(generated.excelPath().toFile())) {
            Cell formula = result.getSheet("150").getRow(6).getCell(9);
            assertThat(formula.getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(formula.getCellFormula()).isEqualTo("'DM '!A1");
        }
    }

    @Test
    void partialPairsAreAlwaysWrittenInMainLmGmOrderRegardlessOfRequestOrder() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));

        var missingGm = services.generation().generate(context.id(), request("151", MaterialFamily.VUA,
                cloneOutput("1.GMV (151)", DocumentType.GM, "1.GMV (111)"),
                existingOutput("151", DocumentType.MAIN),
                existingOutput("1.LMV (151)", DocumentType.LM))).document();
        assertThat(missingGm.selectedSheets()).containsExactly("151", "1.LMV (151)", "1.GMV (151)");

        var missingLm = services.generation().generate(context.id(), request("152", MaterialFamily.VUA,
                existingOutput("1.GMV (152)", DocumentType.GM),
                cloneOutput("1.LMV (152)", DocumentType.LM, "1.LMV (111)"),
                existingOutput("152", DocumentType.MAIN))).document();
        assertThat(missingLm.selectedSheets()).containsExactly("152", "1.LMV (152)", "1.GMV (152)");
    }

    @Test
    void rejectsClientModeTamperingAndCrossFamilyPartialPairs() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));

        assertThatThrownBy(() -> services.generation().generate(context.id(), request("111", MaterialFamily.VUA,
                cloneOutput("1.LMV (111)", DocumentType.LM, "1.LMV (114)"))))
                .hasMessageContaining("đã có sheet LM");
        assertThatThrownBy(() -> services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                existingOutput("1.LMV (200)", DocumentType.LM))))
                .hasMessageContaining("không có LM ở chế độ EXISTING_SHEET");
        assertThatThrownBy(() -> services.outputs().outputs(context.id(), "151", MaterialFamily.BETONG))
                .hasMessageContaining("đã có LM/GM thuộc VUA");
        assertThatThrownBy(() -> services.generation().generate(context.id(), request("151", MaterialFamily.BETONG,
                cloneOutput("1.GMBT (151)", DocumentType.GM, "1.GMBT (141)"))))
                .hasMessageContaining("đã có LM/GM thuộc VUA");
    }

    @Test
    void missingProjectDataIsClearedInsteadOfKeptFromTemplate() throws Exception {
        byte[] workbookBytes = mutateWorkbook(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), workbook -> {
            Sheet dm = workbook.getSheet("DM ");
            for (int rowIndex = 2; rowIndex <= 5; rowIndex++) {
                Row row = dm.getRow(rowIndex);
                if (row != null && row.getCell(1) != null) row.getCell(1).setBlank();
            }
        });
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(workbookBytes, "sample.xlsx"));
        var outputs = services.outputs().outputs(context.id(), "200", MaterialFamily.VUA);
        assertThat(outputs.get(0).fieldDecisions())
                .filteredOn(decision -> List.of("projectName", "packageName", "projectLocation", "contractor")
                        .contains(decision.fieldName()))
                .allSatisfy(decision -> {
                    assertThat(decision.action().name()).isEqualTo("CLEAR");
                    assertThat(decision.value()).isNull();
                });

        var generated = services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                cloneOutput("1.LMV (200)", DocumentType.LM, "1.LMV (111)"))).document();
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            assertBlank(workbook.getSheet("1.LMV (200)"), "A5", "E11", "E12", "E13");
        }
    }

    @Test
    void safetyNetClearsGenericGradesCountsAgesAndStandardsOutsideKnownExamples() throws Exception {
        byte[] workbookBytes = mutateWorkbook(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), workbook -> {
            Sheet lm = workbook.getSheet("1.LMV (111)");
            lm.getRow(40).createCell(10).setCellValue("M200");
            lm.getRow(41).createCell(10).setCellValue("12 viên");
            lm.getRow(42).createCell(10).setCellValue("R90");
            lm.getRow(43).createCell(10).setCellValue("QCVN 16:2023");
        });
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(workbookBytes, "sample.xlsx"));
        var generated = services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                cloneOutput("1.LMV (200)", DocumentType.LM, "1.LMV (111)"))).document();
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            String text = allText(workbook.getSheet("1.LMV (200)"));
            assertThat(text).doesNotContain("M200", "12 viên", "R90", "QCVN 16:2023");
        }
    }

    @Test
    void mixedSelectionAcrossItemsSupportsExistingAndGeneratedOutputs() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLS), "sample.xls"));

        var request = new GenerateRequest(List.of(
                new GenerateSelection("111", List.of(existingOutput("111", DocumentType.MAIN)), null),
                new GenerateSelection("200", List.of(
                        cloneOutput("1.LMV (200)", DocumentType.LM, "1.LMV (111)"),
                        cloneOutput("1.GMV (200)", DocumentType.GM, "1.GMV (111)")), MaterialFamily.VUA),
                new GenerateSelection("201", List.of(cloneOutput("1.LMBT (201)", DocumentType.LM, "1.LMBT (141)")), MaterialFamily.BETONG)
        ), false);
        var generated = services.generation().generate(context.id(), request).document();
        assertThat(generated.workItemNumbers()).containsExactly("111", "200", "201");
        assertThat(generated.selectedSheets()).containsExactly("111", "1.LMV (200)", "1.GMV (200)", "1.LMBT (201)");
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            assertThat(workbook.getSheet("111")).isNotNull();
            assertThat(workbook.getSheet("1.LMV (200)")).isNotNull();
            assertThat(workbook.getSheet("1.GMV (200)")).isNotNull();
            assertThat(workbook.getSheet("1.LMBT (201)")).isNotNull();
        }
    }

    @Test
    void fieldDecisionsActuallyClearTemplateDataAndKeepCertainValues() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));
        var outputs = services.outputs().outputs(context.id(), "200", MaterialFamily.VUA);
        assertThat(outputs.get(0).fieldDecisions())
                .anySatisfy(decision -> {
                    assertThat(decision.fieldName()).isEqualTo("location");
                    assertThat(decision.value()).isEqualTo("Bể nước ngầm");
                    assertThat(decision.targetCells()).contains("D15");
                })
                .anySatisfy(decision -> {
                    assertThat(decision.fieldName()).isEqualTo("specimenSize");
                    assertThat(decision.action().name()).isEqualTo("CLEAR");
                    assertThat(decision.targetCells()).isNotEmpty();
                });

        var generated = services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                cloneOutput("1.LMV (200)", DocumentType.LM, "1.LMV (111)"),
                cloneOutput("1.GMV (200)", DocumentType.GM, "1.GMV (111)"))).document();
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            Sheet lm = workbook.getSheet("1.LMV (200)");
            Sheet gm = workbook.getSheet("1.GMV (200)");
            assertThat(text(lm.getRow(14).getCell(3))).isEqualTo("Bể nước ngầm");
            assertThat(text(lm.getRow(30).getCell(2)))
                    .isEqualTo("Lấy mẫu vữa trát mặt bậc thang, chiều dày 15mm, vữa xi măng mác 100 (Bể nước ngầm)");
            assertThat(text(gm.getRow(32).getCell(2)))
                    .isEqualTo("Mẫu vữa trát mặt bậc thang, chiều dày 15mm, vữa xi măng mác 100 (Bể nước ngầm)");
            assertBlank(lm, "G31", "J31", "M31", "C32", "G32", "C33");
            assertBlank(gm, "J7", "H33", "J33");
            assertThat(allText(lm)).doesNotContain("Ngoài nhà", "#REF!", "40x40x160", "M75", "R7", "TCVN 9999", "Nguyễn Văn Template", "LAS XD 999");
            assertThat(allText(gm)).doesNotContain("Ngoài nhà", "#REF!", "M75", "R7", "TCVN 9999", "Nguyễn Văn Template", "LAS XD 999");
            assertNoFormulaOrError(lm);
            assertNoFormulaOrError(gm);
            assertThat(lm.getRow(30).getCell(2).getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
            assertThat(text(lm.getRow(10).getCell(4))).isEqualTo("Dự án thử nghiệm an toàn");
            assertThat(text(lm.getRow(11).getCell(4))).isEqualTo("Gói thầu mẫu");
            assertThat(text(lm.getRow(12).getCell(4))).isEqualTo("Hà Nội");
            assertThat(text(lm.getRow(4).getCell(0))).isEqualTo("HaCom thử nghiệm");
        }
    }

    @Test
    void concreteCloneDoesNotCopyB15OrSpecimenSize() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));
        var generated = services.generation().generate(context.id(), request("201", MaterialFamily.BETONG,
                cloneOutput("1.LMBT (201)", DocumentType.LM, "1.LMBT (141)"),
                cloneOutput("1.GMBT (201)", DocumentType.GM, "1.GMBT (141)"))).document();
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            Sheet lm = workbook.getSheet("1.LMBT (201)");
            assertThat(text(lm.getRow(30).getCell(2))).contains("bê tông lót tam cấp").contains("B7.5");
            assertThat(allText(lm)).doesNotContain("150x150x150", "B15 - R28");
            assertBlank(lm, "G31", "J31", "M31");
        }
    }

    @Test
    void xlsAndXlsxPreserveLayoutPrintSettingsDrawingAndReopen() throws Exception {
        verifyFormat(TestWorkbookFactory.Format.XLS, "sample.xls");
        verifyFormat(TestWorkbookFactory.Format.XLSX, "sample.xlsx");
    }

    @Test
    void rejectsWrongFamilyTemplateInsteadOfWritingWrongCells() throws Exception {
        TestServices services = TestServices.create(tempDirectory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(TestWorkbookFactory.Format.XLSX), "sample.xlsx"));
        assertThatThrownBy(() -> services.generation().generate(context.id(), request("200", MaterialFamily.VUA,
                cloneOutput("1.LMV (200)", DocumentType.LM, "1.LMBT (141)"))))
                .hasMessageContaining("không đúng loại");
    }

    private void verifyFormat(TestWorkbookFactory.Format format, String fileName) throws Exception {
        Path directory = tempDirectory.resolve(format.name().toLowerCase(Locale.ROOT));
        Files.createDirectories(directory);
        TestServices services = TestServices.create(directory, false);
        var context = services.analysis().analyze(upload(TestWorkbookFactory.workbook(format), fileName));
        var generated = services.generation().generate(context.id(), request("150", MaterialFamily.VUA,
                cloneOutput("1.LMV (150)", DocumentType.LM, "1.LMV (111)"))).document();
        assertThat(generated.excelPath()).exists().isNotEmptyFile();
        try (Workbook workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            Sheet source = workbook.getSheet("1.LMV (111)");
            Sheet clone = workbook.getSheet("1.LMV (150)");
            assertThat(clone).isNotNull();
            assertThat(clone.getNumMergedRegions()).isEqualTo(source.getNumMergedRegions());
            assertThat(clone.getColumnWidth(2)).isEqualTo(source.getColumnWidth(2));
            assertThat(clone.getRow(30).getHeight()).isEqualTo(source.getRow(30).getHeight());
            assertThat(clone.getHeader().getCenter()).isEqualTo(source.getHeader().getCenter());
            assertThat(clone.getFooter().getRight()).isEqualTo(source.getFooter().getRight());
            assertThat(clone.getPrintSetup().getPaperSize()).isEqualTo(source.getPrintSetup().getPaperSize());
            assertThat(clone.getPrintSetup().getFitWidth()).isEqualTo(source.getPrintSetup().getFitWidth());
            for (PageMargin margin : PageMargin.values()) assertThat(clone.getMargin(margin)).isEqualTo(source.getMargin(margin));
            assertThat(normalizedPrintArea(workbook, clone)).isEqualTo(normalizedPrintArea(workbook, source));
            assertThat(drawingCount(clone)).isEqualTo(drawingCount(source)).isGreaterThan(0);
        }
        try (Workbook ignored = WorkbookFactory.create(generated.excelPath().toFile())) {
            // reopen is the assertion
        }
    }

    private GenerateRequest request(String item, MaterialFamily family, GenerateOutputSelection... outputs) {
        return new GenerateRequest(List.of(new GenerateSelection(item, List.of(outputs), family)), false);
    }

    private GenerateOutputSelection existingOutput(String sheetName, DocumentType type) {
        return new GenerateOutputSelection(sheetName, type, GenerationMode.EXISTING_SHEET, null);
    }

    private GenerateOutputSelection cloneOutput(String sheetName, DocumentType type, String template) {
        return new GenerateOutputSelection(sheetName, type, GenerationMode.CLONE_TEMPLATE, template);
    }

    private MockMultipartFile upload(byte[] bytes, String name) {
        String contentType = name.endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/vnd.ms-excel";
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private byte[] mutateWorkbook(byte[] source, Consumer<Workbook> mutation) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            mutation.accept(workbook);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private SheetSnapshot snapshot(Workbook workbook, Sheet sheet) {
        List<String> cells = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        int maxColumn = 0;
        for (Row row : sheet) {
            rows.add(row.getRowNum() + "|" + row.getHeight() + "|" + row.getZeroHeight());
            for (Cell cell : row) {
                maxColumn = Math.max(maxColumn, cell.getColumnIndex());
                String raw = switch (cell.getCellType()) {
                    case FORMULA -> "F:" + cell.getCellFormula();
                    case STRING -> "S:" + cell.getStringCellValue();
                    case NUMERIC -> "N:" + cell.getNumericCellValue();
                    case BOOLEAN -> "B:" + cell.getBooleanCellValue();
                    case ERROR -> "E:" + cell.getErrorCellValue();
                    case BLANK -> "BLANK";
                    default -> cell.getCellType().name();
                };
                cells.add(cell.getAddress() + "|" + raw + "|style=" + cell.getCellStyle().getIndex());
            }
        }
        List<String> columns = new ArrayList<>();
        for (int column = 0; column <= maxColumn; column++) {
            columns.add(column + "|" + sheet.getColumnWidth(column) + "|" + sheet.isColumnHidden(column));
        }
        List<String> merged = new ArrayList<>();
        for (int index = 0; index < sheet.getNumMergedRegions(); index++) {
            merged.add(sheet.getMergedRegion(index).formatAsString());
        }
        return new SheetSnapshot(
                List.copyOf(cells), List.copyOf(rows), List.copyOf(columns), List.copyOf(merged),
                normalizedPrintArea(workbook, sheet), sheet.getHeader().getLeft(), sheet.getHeader().getCenter(),
                sheet.getHeader().getRight(), sheet.getFooter().getLeft(), sheet.getFooter().getCenter(),
                sheet.getFooter().getRight(), drawingCount(sheet)
        );
    }

    private void assertBlank(Sheet sheet, String... addresses) {
        for (String address : addresses) {
            var reference = new org.apache.poi.ss.util.CellReference(address);
            Row row = sheet.getRow(reference.getRow());
            Cell cell = row == null ? null : row.getCell(reference.getCol());
            assertThat(cell == null || cell.getCellType() == CellType.BLANK || text(cell).isBlank())
                    .as(sheet.getSheetName() + "!" + address).isTrue();
        }
    }

    private void assertNoFormulaOrError(Sheet sheet) {
        for (Row row : sheet) for (Cell cell : row) {
            assertThat(cell.getCellType()).as(sheet.getSheetName() + "!" + cell.getAddress())
                    .isNotIn(CellType.FORMULA, CellType.ERROR);
        }
    }

    private String allText(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
        StringBuilder value = new StringBuilder();
        for (Row row : sheet) for (Cell cell : row) value.append(formatter.formatCellValue(cell)).append('\n');
        return value.toString();
    }

    private String text(Cell cell) {
        return cell == null ? "" : new DataFormatter(Locale.forLanguageTag("vi-VN")).formatCellValue(cell);
    }

    private String normalizedPrintArea(Workbook workbook, Sheet sheet) {
        String area = workbook.getPrintArea(workbook.getSheetIndex(sheet));
        if (area == null) return null;
        int separator = area.indexOf('!');
        return separator < 0 ? area : area.substring(separator + 1);
    }

    private int drawingCount(Sheet sheet) {
        if (sheet instanceof XSSFSheet xssf) {
            XSSFDrawing drawing = xssf.getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getShapes().size();
        }
        if (sheet instanceof HSSFSheet hssf) {
            HSSFPatriarch drawing = hssf.getDrawingPatriarch();
            return drawing == null ? 0 : drawing.getChildren().size();
        }
        return 0;
    }

    private record SheetSnapshot(
            List<String> cells,
            List<String> rows,
            List<String> columns,
            List<String> mergedRegions,
            String printArea,
            String headerLeft,
            String headerCenter,
            String headerRight,
            String footerLeft,
            String footerCenter,
            String footerRight,
            int drawingCount
    ) {
    }
}
