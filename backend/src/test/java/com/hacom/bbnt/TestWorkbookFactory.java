package com.hacom.bbnt;

import com.hacom.bbnt.model.DocumentType;
import com.hacom.bbnt.model.MaterialFamily;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

final class TestWorkbookFactory {
    enum Format { XLS, XLSX }

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="
    );

    private TestWorkbookFactory() {
    }

    static byte[] workbook(Format format) throws Exception {
        try (Workbook workbook = format == Format.XLS ? new HSSFWorkbook() : new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            createDm(workbook);

            createMain(workbook, "111");
            createTemplate(workbook, "1.LMV (111)", MaterialFamily.VUA, DocumentType.LM, "111", "Ngoài nhà", "vữa trát tường");
            createTemplate(workbook, "1.GMV (111)", MaterialFamily.VUA, DocumentType.GM, "111", "Ngoài nhà", "vữa trát tường");

            createMain(workbook, "114");
            createTemplate(workbook, "1.LMV (114)", MaterialFamily.VUA, DocumentType.LM, "114", "Tầng mẫu 114", "vữa mẫu 114");
            createTemplate(workbook, "1.GMV (114)", MaterialFamily.VUA, DocumentType.GM, "114", "Tầng mẫu 114", "vữa mẫu 114");

            createMain(workbook, "141");
            createTemplate(workbook, "1.LMBT (141)", MaterialFamily.BETONG, DocumentType.LM, "141", "Tam cấp mẫu", "bê tông mẫu");
            createTemplate(workbook, "1.GMBT (141)", MaterialFamily.BETONG, DocumentType.GM, "141", "Tam cấp mẫu", "bê tông mẫu");

            createMain(workbook, "150"); // MAIN only
            createMain(workbook, "151");
            createTemplate(workbook, "1.LMV (151)", MaterialFamily.VUA, DocumentType.LM, "151", "Vị trí cũ 151", "vữa cũ 151");
            createMain(workbook, "152");
            createTemplate(workbook, "1.GMV (152)", MaterialFamily.VUA, DocumentType.GM, "152", "Vị trí cũ 152", "vữa cũ 152");

            Sheet helper = workbook.createSheet("Thông tin phụ");
            helper.createRow(0).createCell(0).setCellValue("Không phải sheet đầu ra");
            workbook.setSheetHidden(workbook.getSheetIndex(helper), true);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void createDm(Workbook workbook) {
        Sheet dm = workbook.createSheet("DM ");
        dm.createRow(2).createCell(1).setCellValue("Dự án: Dự án thử nghiệm an toàn");
        dm.createRow(3).createCell(1).setCellValue("Địa điểm: Hà Nội");
        dm.createRow(4).createCell(1).setCellValue("Gói thầu: Gói thầu mẫu");
        dm.createRow(5).createCell(1).setCellValue("Nhà thầu: HaCom thử nghiệm");
        Row header = dm.createRow(7);
        header.createCell(1).setCellValue("STT");
        header.createCell(2).setCellValue("NỘI DUNG");
        header.createCell(3).setCellValue("VỊ TRÍ");
        header.createCell(4).setCellValue("NGHIỆM THU CÔNG VIỆC");
        header.createCell(6).setCellValue("NGÀY LẤY MẪU");
        Row secondHeader = dm.createRow(8);
        secondHeader.createCell(4).setCellValue("THỜI GIAN");
        secondHeader.createCell(5).setCellValue("SỐ BIÊN BẢN");

        int row = 10;
        addDmRow(dm, row++, "111", "1", "Chất lượng vữa trát tường", "Ngoài nhà", "1503/CB/NTCV/111", "01/06/2024");
        addDmRow(dm, row++, "114", "2", "Chất lượng vữa trát tường mẫu 114", "Tầng mẫu 114", "1503/CB/NTCV/114", "02/06/2024");
        addDmRow(dm, row++, "141", "3", "Chất lượng bê tông mẫu B15", "Tam cấp mẫu", "1503/CB/NTCV/141", "03/06/2024");
        addDmRow(dm, row++, "150", "4", "Chất lượng vữa xây tường", "Tầng 2", "1503/CB/NTCV/150", "04/06/2024");
        addDmRow(dm, row++, "151", "5", "Chất lượng vữa trát cột", "Tầng 3", "1503/CB/NTCV/151", "05/06/2024");
        addDmRow(dm, row++, "152", "6", "Chất lượng vữa láng nền", "Tầng tum", "1503/CB/NTCV/152", "06/06/2024");
        addDmRow(dm, row++, "200", "7", "Chất lượng vữa trát mặt bậc thang, chiều dày 15mm, vữa xi măng mác 100", "Bể nước ngầm", "1503/CB/NTCV/200", "07/06/2024");
        addDmRow(dm, row++, "201", "8", "Chất lượng bê tông lót tam cấp, cấp độ bền B7.5", "Tam cấp số 1", "1503/CB/NTCV/201", "08/06/2024");
        addDmRow(dm, row, "202", "9", "Chất lượng xây tường gạch", "Tầng 4", "1503/CB/NTCV/202", "09/06/2024");
    }

    private static void addDmRow(
            Sheet dm,
            int rowIndex,
            String number,
            String localOrder,
            String content,
            String location,
            String record,
            String sampleDate
    ) {
        Row row = dm.createRow(rowIndex);
        row.createCell(0).setCellValue(number);
        row.createCell(1).setCellValue(localOrder);
        row.createCell(2).setCellValue(content);
        row.createCell(3).setCellValue(location);
        row.createCell(4).setCellValue("16:30 10/06/2024");
        row.createCell(5).setCellValue(record);
        row.createCell(6).setCellValue(sampleDate);
    }

    private static void createMain(Workbook workbook, String itemNumber) {
        Sheet sheet = workbook.createSheet(itemNumber);
        sheet.createRow(0).createCell(15).setCellValue(itemNumber);
        sheet.createRow(1).createCell(0).setCellValue("HỒ SƠ CHÍNH " + itemNumber);
        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, 15, 0, 30);
    }

    private static void createTemplate(
            Workbook workbook,
            String name,
            MaterialFamily family,
            DocumentType type,
            String templateItem,
            String staleLocation,
            String staleContent
    ) {
        Sheet sheet = workbook.createSheet(name);
        for (int rowIndex = 0; rowIndex < 60; rowIndex++) sheet.createRow(rowIndex);
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 2));
        }
        sheet.setColumnWidth(2, 7200);
        sheet.setColumnWidth(6, 4200);
        sheet.getRow(30).setHeightInPoints(35);
        sheet.getHeader().setCenter("&BHaCom BBNT");
        sheet.getFooter().setRight("Trang &P/&N");
        sheet.setMargin(org.apache.poi.ss.usermodel.PageMargin.LEFT, 0.4);
        sheet.setMargin(org.apache.poi.ss.usermodel.PageMargin.RIGHT, 0.4);
        sheet.setFitToPage(true);
        PrintSetup setup = sheet.getPrintSetup();
        setup.setLandscape(false);
        setup.setFitWidth((short) 1);
        setup.setFitHeight((short) 0);
        setup.setPaperSize(PrintSetup.A4_PAPERSIZE);

        cell(sheet, 0, 15).setCellValue(templateItem);
        cell(sheet, 4, 0).setCellValue("NHÀ THẦU TEMPLATE");
        cell(sheet, 6, 9).setCellFormula("1/0");
        cell(sheet, 7, 6).setCellValue("BIÊN BẢN SỐ:");
        cell(sheet, 7, 7).setCellValue("1503/CB/" + type.name() + "/" + templateItem);
        cell(sheet, 8, 0).setCellValue(type == DocumentType.LM
                ? "LẤY MẪU THÍ NGHIỆM TẠI HIỆN TRƯỜNG"
                : "BIÊN BẢN GIAO NHẬN MẪU THÍ NGHIỆM");
        cell(sheet, 10, 1).setCellValue("Dự án");
        cell(sheet, 10, 4).setCellValue("DỰ ÁN TEMPLATE");
        cell(sheet, 11, 1).setCellValue("Gói thầu");
        cell(sheet, 11, 4).setCellValue("GÓI THẦU TEMPLATE");
        cell(sheet, 12, 1).setCellValue("Địa điểm");
        cell(sheet, 12, 4).setCellValue("ĐỊA ĐIỂM TEMPLATE");
        cell(sheet, 14, 1).setCellValue("Vị Trí:");
        cell(sheet, 14, 3).setCellValue(staleLocation);
        cell(sheet, 18, 1).setCellValue("Ông (bà): Nguyễn Văn Template");
        cell(sheet, 18, 8).setCellValue("Cán bộ phòng thí nghiệm");
        cell(sheet, 19, 1).setCellValue("Phòng LAS XD 999");
        cell(sheet, 20, 1).setCellValue("Người nhận mẫu: Trần Văn Cũ");
        cell(sheet, 21, 1).setCellValue("Người phụ trách: Lê Văn Cũ");

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setAlignment(HorizontalAlignment.LEFT);
        dataStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        dataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        if (type == DocumentType.LM) {
            cell(sheet, 28, 1).setCellValue("STT");
            cell(sheet, 28, 2).setCellValue("CHỦNG LOẠI");
            int dataRow = 30;
            cell(sheet, dataRow, 1).setCellValue("9");
            Cell description = cell(sheet, dataRow, 2);
            description.setCellValue("Lấy mẫu " + staleContent + " (" + staleLocation + ")");
            description.setCellStyle(dataStyle);
            cell(sheet, dataRow, 6).setCellValue(family == MaterialFamily.BETONG ? "150x150x150" : "40x40x160");
            cell(sheet, dataRow, 9).setCellValue("03 tổ");
            cell(sheet, dataRow, 12).setCellValue(family == MaterialFamily.BETONG ? "B15 - R28" : "M75 - R7 - Mỗi tổ 3 mẫu");
            cell(sheet, 31, 2).setCellValue("Dòng mẫu phụ cũ");
            cell(sheet, 31, 6).setCellValue("TCVN 9999");
            cell(sheet, 32, 2).setCellValue("Dòng mẫu phụ thứ hai");
            cell(sheet, 34, 1).setCellValue("Nơi lưu mẫu đối chứng:");
            cell(sheet, 34, 5).setCellValue("Kho mẫu cũ");
            cell(sheet, 35, 1).setCellValue("Mục đích lấy mẫu:");
            cell(sheet, 35, 4).setCellValue("Chỉ tiêu thí nghiệm cũ TCVN 123");
            cell(sheet, 36, 1).setCellValue("Nơi gửi mẫu thí nghiệm:");
            cell(sheet, 36, 5).setCellValue("LAS XD 999");
        } else {
            cell(sheet, 30, 1).setCellValue("STT");
            cell(sheet, 30, 2).setCellValue("VẬT LIỆU");
            int dataRow = 32;
            cell(sheet, dataRow, 1).setCellValue("9");
            Cell description = cell(sheet, dataRow, 2);
            description.setCellValue("Mẫu " + staleContent + " (" + staleLocation + ")");
            description.setCellStyle(dataStyle);
            cell(sheet, dataRow, 7).setCellValue(family == MaterialFamily.BETONG ? "B15 150x150x150" : "M75 R7");
            cell(sheet, dataRow, 9).setCellValue("09 mẫu");
            cell(sheet, dataRow, 11).setCellValue("1503/CB/LM/" + templateItem);
            cell(sheet, dataRow, 13).setCellValue("01/01/2024");
            cell(sheet, 33, 1).setCellValue("Cơ quan yêu cầu thí nghiệm");
            cell(sheet, 34, 1).setCellValue("Tiêu chuẩn áp dụng: TCVN 9999");
            cell(sheet, 35, 1).setCellValue("Ghi chú cấu trúc");
        }

        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, 14, 0, 45);
        addLogo(workbook, sheet);
    }

    private static Cell cell(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(columnIndex);
        return cell == null ? row.createCell(columnIndex) : cell;
    }

    private static void addLogo(Workbook workbook, Sheet sheet) {
        int pictureIndex = workbook.addPicture(TINY_PNG, Workbook.PICTURE_TYPE_PNG);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setCol1(12);
        anchor.setRow1(1);
        anchor.setCol2(14);
        anchor.setRow2(4);
        drawing.createPicture(anchor, pictureIndex);
    }
}
