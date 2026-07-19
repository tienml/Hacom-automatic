# Luồng chức năng Safe Template V2

## Phân tích

```text
multipart upload
→ validate extension/size
→ copy source vào thư mục job
→ mở qua InputStream (không ghi ngược file nguồn)
→ tìm DM exact/trim/header detection
→ detect column mapping và used data
→ DataFormatter + FormulaEvaluator đọc displayed value
→ parse sheet chính/LMV/GMV/LMBT/GMBT
→ xây TemplateRegistry
→ classify VUA/BETONG/UNKNOWN
→ lập FieldDecision và warning
→ lưu JobContext tạm
```

## Chọn biểu mẫu

```text
EXISTING_SHEET
→ chỉ cho chọn sheet thuộc đúng item

CLONE_TEMPLATE
→ resolve family
→ UNKNOWN bắt buộc chọn VUA/BETONG
→ resolve LM/GM template theo registry hoặc lựa chọn người dùng
→ trả tên sheet dự kiến và source template
```

## Sinh Excel

```text
mở bản source qua InputStream
→ xử lý từng selection
   ├─ EXISTING_SHEET: giữ sheet và cập nhật reference P1 như luồng cũ
   └─ CLONE_TEMPLATE:
      clone
      → đổi tên an toàn/không trùng
      → khôi phục page setup/print area
      → sanitize formula, error, người/tổ chức và thông số chưa chắc chắn
      → populate CERTAIN fields
      → validate toàn sheet
→ hiển thị sheet được chọn, ẩn sheet phụ thuộc/template
→ write output
→ reopen bằng Apache POI để kiểm tra integrity
```

## Sinh PDF

```text
mở output Excel qua InputStream
→ copy thành print workbook riêng
→ flatten formula trên sheet được chọn
→ xóa sheet không chọn khỏi print workbook
→ LibreOffice/Gotenberg convert
→ kiểm tra file PDF không rỗng
```

Bản Excel tải về và workbook upload không bị thay đổi bởi bước PDF.

## Mô tả LM/GM

```text
normalized = trim(workContent)
normalized = bỏ duy nhất tiền tố "Chất lượng" ở đầu, không phân biệt hoa thường
LM = "Lấy mẫu " + normalized + optional(" (" + location + ")")
GM = "Mẫu " + normalized + optional(" (" + location + ")")
```

## Số hồ sơ

```text
1503/CB/NTCV/159
→ split theo "/"
→ thay đúng segment NTCV bằng LM hoặc GM
→ 1503/CB/LM/159 / 1503/CB/GM/159
```

Không nhận diện được cấu trúc thì để trống và trả warning; không thay theo index ký tự.
