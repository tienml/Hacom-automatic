# Test Report V4

Ngày chạy: 2026-07-19 (UTC)

## Môi trường

- Java: 21.0.10
- Maven: 3.9.11
- Node: 22.16.0
- npm: 10.9.2
- LibreOffice/soffice: có sẵn
- Docker: không có trong môi trường, nên không chạy Gotenberg runtime

## Backend mặc định

```bash
cd backend
mvn test
```

Kết quả:

- Tests run: 22
- Passed: 21
- Failed: 0
- Errors: 0
- Skipped: 1 (`RealWorkbookIntegrationTest` chỉ chạy khi truyền workbook thật)
- BUILD SUCCESS

Trong đó `ExcelWorkflowTest`: 16/16 pass.

## Backend package

```bash
mvn package
```

Kết quả:

- Tests run: 22
- Passed: 21
- Failed: 0
- Errors: 0
- Skipped: 1
- Spring Boot JAR được tạo thành công
- BUILD SUCCESS

## Workbook thật `.xlsx`

```bash
mvn \
  -Dbbnt.test.workbook=/mnt/data/hacom_v4_work/fixtures/bbnt-real.xlsx \
  -Dbbnt.test.pdf=false \
  -Dbbnt.test.output=/mnt/data/HaCom-v4-main-only-output.xlsx \
  -Dtest=RealWorkbookIntegrationTest test
```

Kết quả: 1/1 pass.

Đã xác minh:

- sheet DM chính xác là `DM `;
- 198 work item;
- item main-only được tìm động;
- MAIN existing + LM clone + GM clone;
- sheet existing giữ nguyên cell/formula/style/merge/print/drawing;
- source upload không đổi byte;
- clone không còn formula/error/#REF!/Ngoài nhà/kích thước template;
- drawing count và print settings khớp template;
- workbook mở lại được.

## Workbook thật `.xls`

```bash
mvn \
  -Dbbnt.test.workbook=/mnt/data/hacom_v4_work/fixtures/bbnt-real.xls \
  -Dbbnt.test.pdf=false \
  -Dbbnt.test.output=/mnt/data/HaCom-v4-main-only-output.xls \
  -Dtest=RealWorkbookIntegrationTest test
```

Kết quả: 1/1 pass với các kiểm tra tương đương `.xlsx`, bao gồm bảo toàn formula/cached value HSSF.

## PDF LibreOffice

Đã chạy riêng `RealWorkbookIntegrationTest` với `-Dbbnt.test.pdf=true` cho cả `.xlsx` và `.xls`.

- `.xlsx`: 1/1 pass
- `.xls`: 1/1 pass
- PDF tồn tại, không rỗng và có signature `%PDF`

Gotenberg runtime không được tuyên bố đã test vì môi trường không có Docker.

## Frontend

```bash
cd frontend
npm ci
npm test -- --run
npm run lint
npm run build
```

Kết quả:

- npm ci: pass
- Vitest: 13/13 pass, 4 test files
- TypeScript project check: pass
- Vite production build: pass

Ca mới xác minh partial pair khóa family theo LM/GM hiện có.
