# API Document Plan V3

Base URL local: `http://localhost:8080`.

## Phân tích workbook

```http
POST /api/v1/jobs/analyze
Content-Type: multipart/form-data
```

Mỗi `workItem` có `mainPlan`, `lmPlan`, `gmPlan`; các cờ `hasMainSheet`, `hasLmSheet`, `hasGmSheet`, `hasCompleteSamplePair`, `hasPartialSamplePair`; `sheetStatus`; template pairs và field decisions.

Ví dụ main-only:

```json
{
  "itemNumber": "110",
  "sheetStatus": "MAIN_ONLY",
  "hasMainSheet": true,
  "hasLmSheet": false,
  "hasGmSheet": false,
  "mainPlan": {
    "documentType": "MAIN",
    "availability": "EXISTING",
    "generationMode": "EXISTING_SHEET",
    "existingSheetName": "110"
  },
  "lmPlan": {
    "documentType": "LM",
    "availability": "GENERATABLE",
    "generationMode": "CLONE_TEMPLATE",
    "plannedSheetName": "1.LMV (110)",
    "sourceTemplate": "1.LMV (111)"
  },
  "gmPlan": {
    "documentType": "GM",
    "availability": "GENERATABLE",
    "generationMode": "CLONE_TEMPLATE",
    "plannedSheetName": "1.GMV (110)",
    "sourceTemplate": "1.GMV (111)"
  }
}
```

## Lấy outputs của item

```http
GET /api/v1/jobs/{jobId}/work-items/{itemNumber}/outputs
GET /api/v1/jobs/{jobId}/work-items/{itemNumber}/outputs?materialFamily=VUA
GET /api/v1/jobs/{jobId}/work-items/{itemNumber}/outputs?materialFamily=BETONG&lmTemplateSheet=...&gmTemplateSheet=...
```

Endpoint trả hợp nhất MAIN/LM/GM. Mỗi output có `documentType`, `generationMode`, `availability`, `sourceTemplate`, `availableSourceTemplates`, `fieldDecisions`, `warnings`.

Với UNKNOWN, MAIN hiện có vẫn được trả; chỉ LM/GM cần chọn family mới xuất hiện.

## Sinh hồ sơ

```http
POST /api/v1/jobs/{jobId}/generate
Content-Type: application/json
```

```json
{
  "selections": [
    {
      "itemNumber": "110",
      "materialFamily": "VUA",
      "outputs": [
        {
          "sheetName": "110",
          "documentType": "MAIN",
          "generationMode": "EXISTING_SHEET"
        },
        {
          "sheetName": "1.LMV (110)",
          "documentType": "LM",
          "generationMode": "CLONE_TEMPLATE",
          "sourceTemplate": "1.LMV (111)"
        },
        {
          "sheetName": "1.GMV (110)",
          "documentType": "GM",
          "generationMode": "CLONE_TEMPLATE",
          "sourceTemplate": "1.GMV (111)"
        }
      ]
    }
  ],
  "createPdf": true
}
```

Người dùng có thể gửi chỉ MAIN, chỉ LM, chỉ GM hoặc bất kỳ tổ hợp hợp lệ. Backend xác thực mode/template cho từng output độc lập.

## Tải file

```http
GET /api/v1/documents/{documentId}/excel
GET /api/v1/documents/{documentId}/pdf?disposition=inline
GET /api/v1/documents/{documentId}/pdf?disposition=attachment
```
