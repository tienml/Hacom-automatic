# Báo cáo chứng minh item main-only

## Workbook thực tế

- File kiểm thử: `BBNT CB chốt Thanh Toán Đ2.xlsx`.
- Sheet DM chính xác: `DM `.
- Item được test được tìm động từ workbook; kết quả hiện tại là **110**.
- Dòng Excel DM: **133**.
- Dữ liệu DM A–G: `[110, 2, 'Trát tường, cột trụ, cạnh cửa đi, cửa sổ, thang bộ chiều dày 1,5mm, vữa XM mác 75', 'Trong nhà Tầng 1', datetime.datetime(2024, 5, 26, 16, 0), '1503/CB/NTCV/110', None]`.

## Trước khi sinh

| Sheet | Có trong source |
|---|---|
| `110` | True |
| `1.LMV (110)` | False |
| `1.GMV (110)` | False |

Kế hoạch tài liệu: MAIN = `EXISTING_SHEET`; LM = `CLONE_TEMPLATE`; GM = `CLONE_TEMPLATE`.

## Sau khi sinh

| Sheet | Có trong output | Trạng thái |
|---|---|---|
| `110` | True | MAIN hiện có |
| `1.LMV (110)` | True | LM clone |
| `1.GMV (110)` | True | GM clone |

Ba sheet đầu output theo đúng thứ tự: `['110', '1.LMV (110)', '1.GMV (110)']`.

- LM number: `1503/CB/LM/110`.
- GM number: `1503/CB/GM/110`.
- Vị trí LM/GM: `Trong nhà Tầng 1` / `Trong nhà Tầng 1`.
- Mô tả LM: `Lấy mẫu Trát tường, cột trụ, cạnh cửa đi, cửa sổ, thang bộ chiều dày 1,5mm, vữa XM mác 75 (Trong nhà Tầng 1)`.
- Mô tả GM: `Mẫu Trát tường, cột trụ, cạnh cửa đi, cửa sổ, thang bộ chiều dày 1,5mm, vữa XM mác 75 (Trong nhà Tầng 1)`.
- Print area LM: `'1.LMV (110)'!$A$1:$O$45`.
- Print area GM: `'1.GMV (110)'!$A$1:$O$44`.

Test còn xác nhận source workbook không đổi byte; sheet clone không còn formula/error, `#REF!`, `Ngoài nhà`, kích thước mẫu cũ; drawing count và print settings bằng template nguồn; workbook mở lại được bằng Apache POI.
