# Kiến trúc Safe Template

## Mục tiêu

Luồng mới mở rộng source hiện có, không tạo project khác. Mỗi dòng DM được lập kế hoạch độc lập theo hai chế độ:

- `EXISTING_SHEET`: dùng sheet đã tồn tại và giữ hành vi cũ.
- `CLONE_TEMPLATE`: clone cặp LM/GM phù hợp, xóa dữ liệu biến đổi rồi chỉ điền trường `CERTAIN`.

Nguyên tắc xuyên suốt: **điền ít nhưng đúng**.

## Thành phần backend

- `ExcelAnalysisService`: tìm đúng sheet DM, phát hiện mapping cột, đọc giá trị hiển thị bằng `DataFormatter`/`FormulaEvaluator`, lập danh sách công việc.
- `SheetNameParser`: nhận diện sheet chính và LMV/GMV/LMBT/GMBT, giữ tên gốc, lấy item number trong ngoặc.
- `MaterialClassificationService`: nhận diện Vữa/Bê tông bằng nội dung có dấu hoặc không dấu; trả `UNKNOWN` khi không chắc chắn.
- `TemplateRegistryService`: xây registry theo `MaterialFamily` và `DocumentType`; không phụ thuộc cứng vào 111/141.
- `WorkItemPlanningService`: tạo `FieldDecision`, danh sách trường điền/trống và warning.
- `TemplateCloneService`: clone, khôi phục page setup, sanitize, populate trường chắc chắn và gọi validator.
- `GeneratedSheetValidator`: chặn công thức/ô lỗi, dữ liệu template cũ, thông số không chắc chắn, merged region chồng lấn và mất drawing/logo.
- `DocumentGenerationService`: hỗ trợ mixed selection, giữ output Excel riêng, tạo print workbook riêng và chuyển PDF.

## Quy trình CLONE_TEMPLATE

```text
DM row
→ classify material
→ resolve LM/GM templates
→ clone sheet
→ restore print settings
→ sanitizeTemplateData
→ populateCertainFields
→ validate generated sheet
→ write workbook
→ reopen with Apache POI
→ optional print workbook → PDF
```

`sanitizeTemplateData` chạy trước `populateCertainFields`. Formula và cached result không được dùng làm dữ liệu cho sheet mới.

## Nguồn dữ liệu

### Tự động điền

- item number;
- nội dung công việc;
- vị trí;
- ngày lấy mẫu nếu DM có giá trị hợp lệ;
- số LM/GM khi số NTCV parse được theo segment `/`;
- mô tả LM/GM;
- dữ liệu cấp dự án đã đọc từ workbook.

### Làm trống nếu chưa có nguồn chắc chắn

- mác/cấp độ bền tại vùng thông số mẫu;
- kích thước mẫu;
- số tổ, số mẫu, số mẫu mỗi tổ;
- tuổi mẫu;
- ghi chú, chỉ tiêu, tiêu chuẩn;
- ngày/giờ giao mẫu;
- LAS, người nhận, người phụ trách;
- các dòng mẫu phụ và mọi dữ liệu cụ thể chỉ có trong template.

## Tương thích XLS/XLSX

Logic dùng API chung `Workbook`, `Sheet`, `Row`, `Cell`, không cast workbook khi xử lý nghiệp vụ. Test tự động tạo `HSSFWorkbook` để kiểm tra `.xls`; kiểm thử workbook thật dùng `.xlsx`/`XSSFWorkbook` và xác nhận drawing/logo còn tồn tại.

Apache POI không tự clone đầy đủ page setup của XSSF. Vì vậy source sao chép thủ công:

- paper size, scale, fit width/height, orientation;
- margins, header/footer;
- print area, repeating rows/columns;
- page breaks và các cờ in/hiển thị liên quan.

Nếu một drawing/shape đặc thù không được POI hỗ trợ clone, validator sẽ báo lỗi thay vì âm thầm làm mất toàn bộ drawing. Việc so sánh pixel-perfect với Microsoft Excel vẫn cần thực hiện khi workbook có shape/OLE rất phức tạp.

## Không sửa workbook nguồn

Mọi workbook cần sửa được mở qua `InputStream`, không mở package theo chế độ read-write trực tiếp từ đường dẫn nguồn. Test tích hợp so sánh toàn bộ byte file upload trước và sau xuất Excel/PDF.
