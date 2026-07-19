# Báo cáo build và kiểm thử V3

Ngày chạy: 19/07/2026 (UTC). Môi trường: Java 21.0.10, Maven 3.9.11, Node 22.16.0, npm 10.9.2, LibreOffice 25.2.3.2.

## Backend

### Toàn bộ unit/integration test mặc định

```bash
mvn test
```

Kết quả: **16 test run; 15 pass; 0 fail; 0 error; 1 skip**. Ca skip là `RealWorkbookIntegrationTest` khi chưa truyền `bbnt.test.workbook`.

Phạm vi test gồm per-document plan đủ/thiếu từng LM/GM, main-only, UNKNOWN, mixed selection trong/cross item, chỉ LM, chỉ GM, đổi template, FieldDecision clear/populate, stale data, dữ liệu không chắc chắn, project-level data, HSSF/XSSF, style/border/merge/row/column/header/footer/drawing/print area, workbook reopen và template không tương thích.

### Package

```bash
mvn -DskipTests package
```

Kết quả: **BUILD SUCCESS**; tạo Spring Boot JAR `bbnt-automation-1.0.0.jar` (~43 MB).

### Workbook thật, không PDF

```bash
mvn -Dbbnt.test.workbook=/mnt/data/hacom_work/bbnt-real.xlsx \
    -Dbbnt.test.pdf=false \
    -Dbbnt.test.output=/mnt/data/HaCom-main-only-output.xlsx \
    -Dtest=RealWorkbookIntegrationTest test
```

Kết quả: **1/1 pass**. Phân tích 198 item, mapping A/B được xác minh, tìm động full pair/main-only/no-sheet Vữa/no-sheet Bê tông/UNKNOWN, và sinh item main-only thành MAIN + LM + GM.

### Workbook thật với LibreOffice PDF

```bash
mvn -Dbbnt.test.workbook=/mnt/data/hacom_work/bbnt-real.xlsx \
    -Dbbnt.test.pdf=true \
    -Dtest=RealWorkbookIntegrationTest test
```

Kết quả: **1/1 pass**; file PDF tồn tại, không rỗng và có signature `%PDF`.

### Gotenberg

Không chạy được Docker/Gotenberg vì môi trường không có lệnh `docker`. Integration và Docker Compose được giữ nguyên, nhưng không tuyên bố Gotenberg runtime đã được test.

## Frontend

```bash
npm test -- --run
npm run lint
npm run build
```

Kết quả: **4 test files, 12/12 test pass**; TypeScript type-check pass; Vite production build pass.

Frontend test render page thật, main-only badge/chọn dòng, output MAIN existing + LM/GM generated, UNKNOWN, đổi family/template, preview FieldDecision, mixed selection, request generate theo từng output, warning và missing-template blocking.

## Ghi chú

Apache POI phát warning nội bộ rằng XSSF `cloneSheet` chưa tự clone page setup. Code V3 chủ động phục hồi page setup/print area và các assertion tương ứng đã pass ở cả fixture và workbook thật.
