# Implementation Review V4

## Phạm vi

Bản V4 được sửa trực tiếp trên `Hacom-automatic-document-plan-v3`, không tạo project mới. Việc review được thực hiện trên code implementation của backend/frontend, sau đó chạy unit/integration test với workbook thật ở cả `.xlsx` và `.xls`.

## Lỗi implementation đã sửa

### 1. EXISTING_SHEET vẫn bị chạm ô P1

Ở bản trước, luồng export có thể ghi lại ô tham chiếu `P1` trên sheet hiện có. V4 loại bỏ hoàn toàn thao tác ghi ô đối với `EXISTING_SHEET`.

`DocumentGenerationService` hiện chỉ:

- xác minh sheet thuộc đúng item/document type;
- đưa sheet vào danh sách output;
- không sanitize;
- không gọi `setCellValue`, `setCellFormula`, `setBlank` hoặc tạo cell trên sheet existing.

Test so sánh trước–sau gồm:

- cell type/value/formula/cached formula value;
- cell style và font;
- row height/hidden/style;
- column width/hidden;
- merged regions;
- print area/page setup/header/footer;
- drawing count.

### 2. HSSF clone có thể đổi token formula trên sheet cũ

`HSSFWorkbook.cloneSheet()` có thể làm thay đổi token sheet-index của formula trong workbook `.xls` nhiều sheet. V4 chụp snapshot formula/cached value của các sheet cũ trước clone và khôi phục sau clone/reorder. Nếu không thể khôi phục chính xác, generation bị chặn thay vì âm thầm trả workbook sai.

### 3. Kế hoạch family của partial pair

Khi item đã có LM hoặc GM, family của sheet hiện có là nguồn ràng buộc cho sheet còn thiếu. V4:

- khóa family theo LM/GM hiện có;
- cảnh báo khi nội dung DM nhận diện khác family của sheet hiện có;
- chặn request tạo cặp chéo VUA/BETONG;
- frontend hiển thị family bị khóa thay vì dropdown có thể đổi sai.

### 4. Chống request giả mode/template

Backend không tin dữ liệu frontend. Mỗi output được đối chiếu với `mainPlan/lmPlan/gmPlan`:

- `EXISTING_SHEET` chỉ hợp lệ khi plan là `EXISTING` và đúng sheet;
- không được clone LM/GM nếu document type đó đã tồn tại;
- tên sheet clone phải đúng parser/family/item;
- template phải thuộc registry, usable và tương thích `TemplateProfile`;
- output được sắp xếp MAIN → LM → GM bất kể thứ tự request.

### 5. FieldDecision và sanitizer

`FieldDecision` là nguồn điều khiển chính:

- chỉ `CERTAIN + POPULATE` được ghi;
- `UNCERTAIN/UNKNOWN` hoặc `CLEAR` được `cell.setBlank()`;
- project-level không đọc được cũng không được giữ dữ liệu cụ thể từ template;
- mở rộng các field phải xóa: grade, strength class, kích thước, số tổ/số mẫu/số viên, tuổi mẫu, ghi chú, chỉ tiêu, tiêu chuẩn, mục đích, nơi lưu/gửi mẫu, ngày/giờ giao, LAS, người nhận/phụ trách và dòng mẫu phụ.

Regex trong `TemplateDataPatterns` chỉ là safety net sau mapping profile, không phải nguồn dữ liệu.

### 6. Validator

Validator kiểm tra:

- formula/error/token lỗi;
- dữ liệu cũ của template;
- record number cũ;
- uncertain cells/ranges còn giá trị;
- header/footer và text box trong phạm vi Apache POI hỗ trợ;
- merged region chồng lấn;
- tên sheet;
- drawing count;
- print area.

### 7. Print area và thứ tự output

V4 chụp/khôi phục print area theo tên sheet trước/sau reorder, đặc biệt cho HSSF. Output được nhóm MAIN → LM → GM. Các sheet phụ thuộc không chọn được giữ ẩn trong file Excel để không phá formula existing; bản dành cho PDF loại sheet không chọn.

## Nguyên tắc vẫn giữ

- Source workbook upload không bị sửa.
- Không tự cộng delivery date +7.
- Không tự điền mác, kích thước, số tổ, tuổi mẫu, tiêu chuẩn, LAS hoặc tên người.
- Chỉ sanitize sheet clone mới.
- Upload/search/filter/pagination/multi-select/preview/download/print/Gotenberg/LibreOffice/TTL/CORS/Docker Compose vẫn được giữ.
