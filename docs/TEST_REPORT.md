# Báo cáo kiểm thử Version 1

Ngày kiểm thử: 16/07/2026

## Build

- Frontend: `npm run build` — thành công.
- Backend: `mvn clean test package` — thành công.
- Java: 21.

## Unit/integration test

- Tìm được sheet `DM ` có khoảng trắng.
- Đọc thông tin dự án.
- Đọc dòng công việc số 111.
- Nhận diện `111` và `1.LMV (111)`.
- Tạo workbook kết quả.
- Ghi số tham chiếu 111 vào `P1`.
- Ẩn sheet không được chọn trong file Excel tải về.

## Kiểm thử bằng workbook BBNT khách hàng

Kết quả phân tích:

- Sheet DM: `DM `.
- Số công việc đọc được: 194.
- Số sheet đầu ra nhận diện: 134.
- Có ngày lấy mẫu: 51.
- Không có ngày lấy mẫu: 143.

Các trường hợp đã sinh thành công:

- DM 111 → `111`, `1.LMV (111)`, `1.GMV (111)`.
- DM 112 → `112`.
- DM 114 → `114`, `1.LMV (114)`, `1.GMV (114)`.

PDF DM 111:

- 5 trang A4.
- Khoảng 451 KB.
- Nội dung trang đầu lấy đúng số `1503/CB/YCNT/111`, nội dung công việc và vị trí từ DM.

Bản cũ chỉ ẩn sheet khiến LibreOffice xuất 330 trang. Bản hiện tại tạo workbook in riêng, đóng băng công thức và xóa sheet không chọn nên PDF chỉ còn đúng nhóm biểu mẫu đã chọn.
