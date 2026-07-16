# API Version 1

Base URL local: `http://localhost:8080`

## Trạng thái hệ thống

```http
GET /api/v1/system/status
```

## Phân tích workbook

```http
POST /api/v1/jobs/analyze
Content-Type: multipart/form-data
```

Form field: `file`

## Lấy các biểu mẫu liên quan

```http
GET /api/v1/jobs/{jobId}/work-items/{number}/outputs
```

## Sinh hồ sơ

```http
POST /api/v1/jobs/{jobId}/generate
Content-Type: application/json
```

```json
{
  "workItemNumber": 111,
  "selectedSheets": ["111", "1.LMV (111)", "1.GMV (111)"],
  "createPdf": true
}
```

## Tải Excel

```http
GET /api/v1/documents/{documentId}/excel
```

## Preview/tải PDF

```http
GET /api/v1/documents/{documentId}/pdf?disposition=inline
GET /api/v1/documents/{documentId}/pdf?disposition=attachment
```
