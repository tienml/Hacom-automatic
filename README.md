# HaCom BBNT Automation — Safe Existing Sheet V4

Bản này mở rộng trực tiếp source hiện có để hỗ trợ cả dòng DM đã có sheet và dòng chưa có sheet:

```text
Upload BBNT
→ đọc và xác minh DM
→ chọn nhiều dòng
→ mainPlan/lmPlan/gmPlan độc lập cho từng dòng
→ xem trường sẽ điền/trống
→ xuất Excel
→ tạo PDF/preview/download/print
```

## Chức năng chính

- Upload `.xls`/`.xlsx`, tối đa 50 MB.
- Tìm sheet DM kể cả tên có khoảng trắng cuối như `DM `; có fallback nhận diện theo tiêu đề.
- Đọc giá trị hiển thị bằng Apache POI `DataFormatter` và `FormulaEvaluator`.
- Không giả định cứng dòng bắt đầu/kết thúc của DM; phát hiện used data thực tế.
- Nhận diện sheet số và LMV/GMV/LMBT/GMBT bằng parser tên sheet.
- Hai mode áp dụng **theo từng output MAIN/LM/GM**:
  - `EXISTING_SHEET`: giữ nguyên sheet hiện có, không sanitize.
  - `CLONE_TEMPLATE`: clone layout, xóa dữ liệu/công thức không chắc chắn, chỉ điền `CERTAIN + POPULATE`.
- Nhận diện `VUA`, `BETONG`, `UNKNOWN`; UNKNOWN chỉ bắt buộc chọn family khi người dùng cần sinh LM/GM, còn MAIN hiện có vẫn xuất độc lập.
- Hỗ trợ mixed selection: nhiều dòng, nhiều chế độ và nhiều họ vật liệu trong một lần xuất.
- Giữ style, merge, border, row height, column width, drawing/logo, print area và page setup của template trong phạm vi Apache POI hỗ trợ.
- Validator chặn `#REF!`, formula cũ, số/vị trí/nội dung template cũ và thông số mẫu chưa chắc chắn.
- Excel kết quả được mở lại bằng Apache POI trước khi trả về.
- PDF dùng Gotenberg hoặc LibreOffice; print workbook chỉ chứa sheet được chọn.
- Workbook upload không bị sửa; file tạm tự xóa theo TTL.

## Tech stack và phiên bản

### Backend

- Java 21
- Spring Boot 3.5.16
- Maven
- Apache POI 5.5.1

### Frontend

- Node.js `>=20.19 <23`
- React 19.2
- TypeScript 7
- Vite 8
- Vitest

### PDF

- Gotenberg 8 LibreOffice qua Docker; hoặc
- LibreOffice/`soffice` cài cục bộ; hoặc
- `PDF_MODE=disabled` để chỉ xuất Excel.

## Chạy bằng Docker

```bash
docker compose up --build
```

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Gotenberg: `http://127.0.0.1:3000`

Dừng:

```bash
docker compose down
```

## Chạy local

### Backend

```bash
cd backend
mvn spring-boot:run
```

Chỉ xuất Excel:

```bash
PDF_MODE=disabled mvn spring-boot:run
```

Dùng LibreOffice cục bộ:

```bash
PDF_MODE=libreoffice LIBREOFFICE_COMMAND=soffice mvn spring-boot:run
```

Windows PowerShell khi LibreOffice không nằm trong `PATH`:

```powershell
$env:PDF_MODE="libreoffice"
$env:LIBREOFFICE_COMMAND="C:\Program Files\LibreOffice\program\soffice.exe"
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm ci
cp .env.example .env   # Windows: Copy-Item .env.example .env
npm run dev
```

## Biến môi trường

### Backend

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `PORT` | `8080` | Cổng backend |
| `APP_ALLOWED_ORIGINS` | `http://localhost:5173` | Danh sách origin, phân tách bằng dấu phẩy |
| `APP_STORAGE_ROOT` | thư mục temp hệ điều hành | Nơi giữ job/document tạm |
| `APP_STORAGE_TTL_MINUTES` | `60` | Thời gian giữ file tạm |
| `APP_STORAGE_CLEANUP_DELAY_MS` | `600000` | Chu kỳ dọn file |
| `PDF_MODE` | `auto` | `auto`, `gotenberg`, `libreoffice`, `disabled` |
| `GOTENBERG_BASE_URL` | `http://localhost:3000` | Địa chỉ Gotenberg |
| `LIBREOFFICE_COMMAND` | `soffice` | Lệnh/đường dẫn LibreOffice |
| `PDF_TIMEOUT_SECONDS` | `120` | Timeout PDF |

### Frontend

```env
VITE_API_BASE_URL=http://localhost:8080
```

## Build và test

```bash
cd backend
mvn test
mvn package
```

Kiểm thử workbook thật, không PDF:

```bash
mvn -Dbbnt.test.workbook=/duong-dan/BBNT.xlsx -Dbbnt.test.pdf=false test
```

Kiểm thử workbook thật với LibreOffice:

```bash
mvn -Dbbnt.test.workbook=/duong-dan/BBNT.xlsx -Dbbnt.test.pdf=true -Dtest=RealWorkbookIntegrationTest test
```

Frontend:

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
```

## Luồng người dùng

1. Upload workbook BBNT.
2. Xem thống kê và mở danh mục DM.
3. Tìm/lọc, chọn cả dòng “Có sheet” và “Chưa có sheet”.
4. Với dòng chưa có sheet, hệ thống nhận diện Vữa/Bê tông; UNKNOWN yêu cầu chọn tay.
5. Chọn LM/GM và xem trường “Sẽ tự động điền”, “Sẽ để trống”, warning.
6. Tạo file xem trước.
7. Preview PDF nếu engine sẵn sàng; tải Excel/PDF hoặc in.

## Tài liệu

- [Implementation review V4](docs/IMPLEMENTATION_REVIEW_V4.md)
- [Báo cáo test V4](docs/TEST_REPORT_V4.md)
- [Danh sách file thay đổi V4](docs/CHANGED_FILES_V4.md)
- [API V3](docs/API.md)
- [Kiến trúc Document Plan V3](docs/ARCHITECTURE_DOCUMENT_PLAN_V3.md)
- [Chứng minh main-only](docs/MAIN_ONLY_PROOF.md)
- [Báo cáo test V3](docs/TEST_REPORT_V3.md)
- [Danh sách file thay đổi V3](docs/CHANGED_FILES_V3.md)
- [Luồng chức năng](docs/FUNCTION_FLOW.md)
- [Kiến trúc Safe Template](docs/SAFE_TEMPLATE_ARCHITECTURE.md)
- [Báo cáo kiểm chứng workbook](docs/WORKBOOK_ANALYSIS.md)
- [Báo cáo test](docs/TEST_REPORT.md)

## Giới hạn còn lại

- Các trường thí nghiệm chưa có nguồn chắc chắn vẫn cần nhập tay hoặc bổ sung cấu hình dự án/nghiệp vụ.
- `deliveryDate` không tự cộng 7 ngày; chỉ tính khi sau này có cấu hình `deliveryOffsetDays` đã xác nhận.
- Template registry hiện chọn template hợp lệ theo workbook; giao diện chưa có màn hình quản trị registry lâu dài ngoài lựa chọn family/template của job hiện tại.
- Shape/OLE đặc thù của Excel có thể nằm ngoài khả năng clone đầy đủ của Apache POI; drawing/logo cơ bản được validator kiểm tra.
- Gotenberg cần được chạy riêng để kiểm thử engine đó; LibreOffice cục bộ là fallback độc lập.
