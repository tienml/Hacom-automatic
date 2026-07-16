# HaCom BBNT Automation — Version 1

Bản V1 bám trực tiếp vào workbook BBNT hiện tại của khách hàng:

```text
Upload BBNT
→ Đọc sheet DM
→ Chọn số danh mục
→ Chọn các sheet đầu ra liên quan
→ Tạo Excel
→ Preview PDF
→ Tải Excel/PDF và in
```

## 1. Phạm vi đã hoàn thiện

- Giao diện React + TypeScript theo bộ nhận diện HaCom Holdings.
- Kéo thả/upload `.xls` và `.xlsx`, tối đa 50 MB.
- Tự nhận diện sheet `DM`, kể cả tên có khoảng trắng như `DM `.
- Đọc thông tin dự án ở phần đầu sheet DM.
- Đọc các cột A–G: số DM, thứ tự, nội dung, vị trí, thời gian, số biên bản, ngày lấy mẫu.
- Tìm sheet đầu ra theo số danh mục:
  - tên đúng số, ví dụ `111`;
  - tên có số trong ngoặc, ví dụ `1.LMV (111)`, `1.GMV (111)`.
- Tìm kiếm, lọc vị trí, lọc có/không lấy mẫu, phân trang.
- Chọn một công việc và các biểu mẫu cần xuất.
- Ghi lại số tham chiếu vào ô `P1` của từng sheet đã chọn.
- Tạo Excel kết quả mà không sửa file gốc.
- Tạo riêng workbook in đã đóng băng công thức và chỉ giữ các sheet được chọn, tránh PDF chứa toàn bộ workbook.
- Preview PDF trong trình duyệt, tải Excel/PDF và mở PDF để in.
- Không đăng nhập, không database, không lịch sử; file tạm tự xóa sau 60 phút.
- PDF hỗ trợ ba chế độ: Gotenberg, LibreOffice cài trên máy, hoặc tắt PDF.

## 2. Tech stack

### Frontend

- React 19
- TypeScript
- Vite
- CSS thuần, không phụ thuộc UI framework
- Deploy được lên Vercel

### Backend

- Java 21
- Spring Boot 3.5
- Apache POI 5.5.1
- File tạm trong thư mục hệ điều hành

### PDF

- Khuyến nghị: Gotenberg 8 LibreOffice bằng Docker.
- Không muốn dùng Docker: cài LibreOffice và đặt `PDF_MODE=libreoffice`.
- Chưa có bộ chuyển PDF: đặt `PDF_MODE=disabled`; hệ thống vẫn tạo Excel.

## 3. Bạn có bắt buộc phải cài Docker không?

**Không bắt buộc.** Có ba cách chạy:

### Cách A — Khuyến nghị, dễ nhất: dùng Docker cho toàn bộ hệ thống

Cần cài:

- Docker Desktop.

Chạy tại thư mục gốc:

```bash
docker compose up --build
```

Mở:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Gotenberg: chỉ bind local tại `http://localhost:3000`

Dừng:

```bash
docker compose down
```

### Cách B — Backend và frontend chạy local, chỉ Gotenberg chạy Docker

Cần cài:

- Java 21 và Maven;
- Node.js 20+;
- Docker Desktop.

Terminal 1:

```bash
docker run --rm -p 127.0.0.1:3000:3000 gotenberg/gotenberg:8-libreoffice
```

Terminal 2:

```bash
cd backend
mvn spring-boot:run
```

Terminal 3:

```bash
cd frontend
npm install
copy .env.example .env
npm run dev
```

Trên macOS/Linux, thay `copy` bằng:

```bash
cp .env.example .env
```

### Cách C — Không dùng Docker

Cần cài:

- Java 21 và Maven;
- Node.js 20+;
- LibreOffice.

Windows PowerShell:

```powershell
cd backend
$env:PDF_MODE="libreoffice"
$env:LIBREOFFICE_COMMAND="C:\Program Files\LibreOffice\program\soffice.exe"
mvn spring-boot:run
```

Nếu `soffice` đã có trong `PATH`:

```powershell
$env:PDF_MODE="libreoffice"
mvn spring-boot:run
```

Frontend:

```powershell
cd frontend
npm install
Copy-Item .env.example .env
npm run dev
```

### Chạy khi chưa cần PDF

Windows PowerShell:

```powershell
cd backend
$env:PDF_MODE="disabled"
mvn spring-boot:run
```

Khi đó luồng upload, đọc DM, chọn biểu mẫu và tải Excel vẫn hoạt động; trang preview sẽ thông báo PDF chưa sẵn sàng.

## 4. Cấu trúc thư mục

```text
hacom-bbnt-automation-v1/
├── backend/
│   ├── src/main/java/com/hacom/bbnt/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── error/
│   │   ├── model/
│   │   └── service/
│   ├── src/test/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── public/
│   │   ├── hacom-logo-horizontal.png
│   │   └── hacom-logo-vertical.png
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── App.tsx
│   │   ├── api.ts
│   │   ├── icons.tsx
│   │   ├── styles.css
│   │   └── types.ts
│   ├── Dockerfile
│   ├── nginx.conf
│   └── vercel.json
├── docs/
│   ├── API.md
│   ├── FUNCTION_FLOW.md
│   └── ui-reference/
└── docker-compose.yml
```

## 5. Biến môi trường backend

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `8080` | Cổng backend |
| `APP_ALLOWED_ORIGINS` | `http://localhost:5173` | Origin được phép gọi API, phân tách bằng dấu phẩy |
| `APP_STORAGE_TTL_MINUTES` | `60` | Thời gian giữ file tạm |
| `PDF_MODE` | `auto` | `auto`, `gotenberg`, `libreoffice`, `disabled` |
| `GOTENBERG_BASE_URL` | `http://localhost:3000` | Địa chỉ Gotenberg |
| `LIBREOFFICE_COMMAND` | `soffice` | Lệnh/đường dẫn chạy LibreOffice |
| `PDF_TIMEOUT_SECONDS` | `120` | Timeout chuyển PDF |

`PDF_MODE=auto` sẽ thử Gotenberg trước, sau đó thử LibreOffice cài trên máy.

## 6. Biến môi trường frontend

Tạo `frontend/.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Khi deploy Vercel, đặt `VITE_API_BASE_URL` bằng domain backend thật.

## 7. Kiểm thử

### Frontend

```bash
cd frontend
npm install
npm run build
```

### Backend

```bash
cd backend
mvn test
mvn package
```

Các test hiện có kiểm tra:

- nhận diện sheet `DM `;
- đọc thông tin dự án và dòng số 111;
- nhận diện sheet `111` và `1.LMV (111)`;
- sinh workbook và ghi số tham chiếu tại `P1`;
- giữ sheet được chọn ở trạng thái hiển thị.

Đã kiểm thử thủ công với file BBNT khách hàng cho các số `111`, `112`, `114`; PDF danh mục 111 chỉ còn 5 trang thay vì xuất toàn bộ workbook.

## 8. Deploy thử nghiệm

### Frontend trên Vercel

- Root directory: `frontend`
- Build command: `npm run build`
- Output directory: `dist`
- Environment variable:

```env
VITE_API_BASE_URL=https://domain-backend-cua-ban
```

### Backend và PDF

Không đặt Spring Boot/Gotenberg lên Vercel Functions. Chọn một nền tảng chạy container như:

- Google Cloud Run;
- Render Docker;
- Railway;
- VPS.

Backend cần:

```env
APP_ALLOWED_ORIGINS=https://domain-vercel-cua-ban
PDF_MODE=gotenberg
GOTENBERG_BASE_URL=http://dia-chi-gotenberg:3000
```

## 9. Giới hạn V1

- Chỉ sinh từ các sheet đã tồn tại trong workbook BBNT.
- Chưa sửa nội dung DM trên giao diện.
- Chưa tạo ngân hàng mẫu chung.
- Chưa sinh DOC/DOCX.
- Chưa đăng nhập, database, phân quyền và lịch sử.
- File `.xls` phức tạp vẫn phải so sánh PDF với bản in từ Microsoft Excel trước khi nghiệm thu.
