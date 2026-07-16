# Luồng chức năng Version 1

## Luồng người dùng

```text
1. Mở trang Tải file BBNT
2. Chọn hoặc kéo thả workbook .xls/.xlsx
3. Bấm Phân tích file
4. Xem kết quả: sheet DM, số công việc, số biểu mẫu, tên dự án
5. Bấm Đọc danh mục công việc
6. Tìm kiếm/lọc và chọn một số DM, ví dụ 111
7. Bấm Tiếp tục chọn biểu mẫu
8. Hệ thống tìm các sheet liên quan: 111, 1.LMV (111), 1.GMV (111)
9. Người dùng giữ/bỏ chọn từng biểu mẫu
10. Bấm Tạo Excel & Preview PDF
11. Backend tạo Excel kết quả
12. Backend tạo workbook in tạm, đóng băng công thức và xóa các sheet không chọn
13. Gotenberg hoặc LibreOffice chuyển workbook in sang PDF
14. Người dùng preview, tải Excel, tải PDF hoặc mở để in
```

## Luồng backend

```text
Multipart upload
→ validate extension/size
→ save temporary source
→ WorkbookFactory mở .xls/.xlsx
→ tìm DM bằng trim + ignoreCase
→ DataFormatter + FormulaEvaluator đọc A:G
→ map số DM với tên sheet đầu ra
→ lưu JobContext trong bộ nhớ

Generate
→ validate selectedSheets thuộc số DM
→ copy workbook
→ ghi P1 = số DM trên sheet đã chọn
→ ẩn sheet không chọn cho Excel tải về
→ tạo print workbook riêng
→ evaluate/cached formula thành giá trị tĩnh
→ xóa sheet không chọn
→ convert PDF
→ lưu kết quả tạm theo documentId
```
