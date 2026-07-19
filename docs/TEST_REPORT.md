# Báo cáo kiểm thử Safe Template V2

Ngày chạy: 19/07/2026

## Môi trường thực tế

- Java: 21.0.10
- Maven: 3.9.11
- Node: 22.16
- npm: 10.9
- LibreOffice/soffice: có trong môi trường test
- Gotenberg Docker: không chạy trong phiên kiểm thử này

## Backend — full suite + workbook thật

Lệnh:

```bash
mvn -Dbbnt.test.workbook=/mnt/data/hacom_work/bbnt-test.xlsx \
    -Dbbnt.test.pdf=false test
```

Kết quả:

- `BUILD SUCCESS`
- 11 test chạy
- 0 failure
- 0 error
- 0 skipped

Bao phủ:

- `.xls` bằng `HSSFWorkbook` và `.xlsx` workbook thật;
- DM có khoảng trắng cuối;
- `EXISTING_SHEET` không clone;
- clone vữa và bê tông;
- UNKNOWN yêu cầu chọn;
- xóa formula/value nhưng giữ style;
- không còn `#REF!`, vị trí cũ, specimen size, B15/M75 template ngoài trường CERTAIN;
- đổi số NTCV → LM/GM theo segment;
- parser/tên sheet/trùng tên/giới hạn Excel;
- mixed selection;
- workbook output mở lại được;
- drawing/logo và page setup được giữ.

## Workbook thực tế

File kiểm thử: `BBNT CB chốt Thanh Toán Đ2.xlsx` (không đưa vào source).

Xác minh:

- sheet DM chính xác: `DM `;
- used range: `A1:M236`;
- 198 dòng công việc;
- 85 dòng có sheet;
- 113 dòng chưa có sheet;
- 48 sheet LMV/GMV/LMBT/GMBT;
- 133 ô/công thức lỗi được phát hiện trong workbook cũ.

Ca clone thực tế:

- DM 20, family Vữa;
- tạo `1.LMV (20)` và `1.GMV (20)`;
- nội dung/vị trí lấy từ DM;
- không còn formula hoặc cell error;
- không còn `Ngoài nhà`, `#REF!`, `40x40x160`, `150x150x150` từ template;
- drawing/logo còn tồn tại;
- paper size, orientation, scale/fit, margins, header/footer, breaks và print area khớp template;
- workbook upload giữ nguyên byte trước/sau generation.

## PDF bằng LibreOffice

Lệnh:

```bash
mvn -Dbbnt.test.workbook=/mnt/data/hacom_work/bbnt-test.xlsx \
    -Dbbnt.test.pdf=true \
    -Dtest=RealWorkbookIntegrationTest test
```

Kết quả:

- `BUILD SUCCESS`
- 1 test tích hợp pass
- PDF tồn tại, không rỗng và có signature `%PDF`

Gotenberg không được chạy trong môi trường này; code tích hợp và Docker Compose được giữ nguyên, nhưng không tuyên bố runtime Gotenberg đã test.

## Frontend

```bash
npm test
npm run lint
npm run build
```

Kết quả:

- Vitest: 1 file, 4 test pass;
- TypeScript lint/type-check: pass;
- Vite production build: pass.

Frontend tests bao phủ:

- dòng chưa có sheet vẫn selectable;
- UNKNOWN bắt buộc chọn family;
- mixed selection/incomplete selection;
- số lượng sheet và nhãn vật liệu.

## Cảnh báo không phải lỗi

Apache POI ghi warning `Cloning sheets with page setup is not yet supported` khi gọi `XSSFWorkbook.cloneSheet`. Source đã khôi phục page setup thủ công và test so sánh trực tiếp template/clone đã pass.
