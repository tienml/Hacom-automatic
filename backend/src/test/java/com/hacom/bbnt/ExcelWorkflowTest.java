package com.hacom.bbnt;

import com.hacom.bbnt.dto.GenerateRequest;
import com.hacom.bbnt.service.DocumentGenerationService;
import com.hacom.bbnt.service.ExcelAnalysisService;
import com.hacom.bbnt.service.PdfConversionService;
import com.hacom.bbnt.service.TemporaryStore;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelWorkflowTest {

    @TempDir
    Path tempDirectory;

    @Test
    void analyzesDmAndCreatesExcelForSelectedSheet() throws Exception {
        TemporaryStore store = new TemporaryStore(tempDirectory.toString(), 60);
        ExcelAnalysisService analysisService = new ExcelAnalysisService(store);

        byte[] source = sampleWorkbook();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.xls",
                "application/vnd.ms-excel",
                source
        );

        var context = analysisService.analyze(file);
        assertThat(context.dmSheetName()).isEqualTo("DM ");
        assertThat(context.project().projectName()).isEqualTo("Dự án thử nghiệm");
        assertThat(context.workItems()).hasSize(1);
        assertThat(context.workItems().getFirst().number()).isEqualTo(111);
        assertThat(context.outputSheets().get(111)).containsExactly("111", "1.LMV (111)");

        PdfConversionService pdfService = new PdfConversionService(
                RestClient.builder(),
                "disabled",
                "http://localhost:3000",
                "soffice",
                30
        );
        DocumentGenerationService generationService = new DocumentGenerationService(store, pdfService);
        var generated = generationService.generate(
                context.id(),
                new GenerateRequest(111, List.of("111", "1.LMV (111)"), false)
        ).document();

        assertThat(generated.excelPath()).exists();
        try (var workbook = WorkbookFactory.create(generated.excelPath().toFile())) {
            assertThat(workbook.getSheet("111").getRow(0).getCell(15).getNumericCellValue()).isEqualTo(111);
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("DM "))).isTrue();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("111"))).isFalse();
        }
    }

    private byte[] sampleWorkbook() throws Exception {
        try (var workbook = new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var dm = workbook.createSheet("DM ");
            dm.createRow(2).createCell(1).setCellValue("Dự án: Dự án thử nghiệm");
            dm.createRow(3).createCell(1).setCellValue("Địa điểm: Hà Nội");
            dm.createRow(4).createCell(1).setCellValue("Gói thầu: Gói thầu mẫu");
            dm.createRow(5).createCell(1).setCellValue("Nhà thầu: HaCom");
            var row = dm.createRow(11);
            row.createCell(0).setCellValue(111);
            row.createCell(1).setCellValue(1);
            row.createCell(2).setCellValue("Chất lượng vữa trát tường");
            row.createCell(3).setCellValue("Trong nhà tầng 1");
            row.createCell(4).setCellValue("16:30 09/06/24");
            row.createCell(5).setCellValue("1503/CB/NTCV/111");
            row.createCell(6).setCellValue("01/06/24");

            workbook.createSheet("111").createRow(0).createCell(15).setCellValue(111);
            workbook.createSheet("1.LMV (111)").createRow(0).createCell(15).setCellValue(111);
            workbook.createSheet("Khác");
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
