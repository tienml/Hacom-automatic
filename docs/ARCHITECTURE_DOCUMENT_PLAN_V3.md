# Kiến trúc Document Plan V3

## Mục tiêu

V3 sửa lỗi kiến trúc của V2 bằng cách lập kế hoạch độc lập cho từng loại tài liệu `MAIN`, `LM`, `GM`. Một item không còn bị gán một `generationMode` duy nhất chỉ vì đã có một sheet bất kỳ.

## Mô hình chính

- `DocumentType`: `MAIN`, `LM`, `GM`.
- `OutputAvailability`: `EXISTING`, `GENERATABLE`, `MISSING_TEMPLATE`, `NOT_APPLICABLE`.
- `DocumentPlanDto`: availability, generation mode, sheet hiện có/tên dự kiến, family, source template, template thay thế, field decisions và warning.
- `WorkItemDto`: `mainPlan`, `lmPlan`, `gmPlan` cùng trạng thái tổng hợp `WorkItemSheetStatus`.
- `GenerateOutputSelection`: mỗi output gửi riêng `sheetName`, `documentType`, `generationMode`, `sourceTemplate`.

## Luồng backend

```text
Upload XLS/XLSX
→ ExcelAnalysisService xác định DM và mapping A–G
→ phân loại sheet MAIN/LM/GM riêng theo itemNumber
→ TemplateRegistryService lập toàn bộ template pair và recommended pair
→ WorkItemPlanningService tạo mainPlan/lmPlan/gmPlan
→ OutputSheetService hợp nhất output EXISTING và GENERATABLE
→ người dùng chọn bất kỳ tổ hợp MAIN/LM/GM
→ DocumentGenerationService xử lý từng output theo mode riêng
→ TemplateCloneService clone layout, sanitize, áp dụng FieldDecision
→ GeneratedSheetValidator kiểm tra
→ ghi, mở lại workbook, tùy chọn chuyển PDF
```

## FieldDecision là nguồn điều khiển

`FieldDecisionDto` chứa field, source, value, certainty, action, targetCells/targetRanges, documentType và reason. Chỉ `CERTAIN + POPULATE` được ghi. `UNCERTAIN` hoặc `UNKNOWN` được `CLEAR` bằng `Cell.setBlank()` tại vùng đã map.

## Template Profile

`TemplateProfileService` giữ mọi địa chỉ tương thích đã kiểm chứng (`P1`, `H8`, `D15`, `J7`, vùng bảng động...) tại một nơi duy nhất. Profile chỉ được dùng sau khi các label cấu trúc như tiêu đề LM/GM, số biên bản, vị trí, dự án, gói thầu và địa điểm được xác minh. Template không khớp profile bị từ chối thay vì ghi nhầm ô.

## An toàn dữ liệu

Sanitizer thực hiện theo thứ tự: xóa formula/error không an toàn; clear variable/uncertain fields và range; xóa stale exact values, số hồ sơ/ngày cũ, LAS/người cũ và dòng mẫu phụ; sau đó mới ghi dữ liệu chắc chắn. Existing sheet không bị sanitize.

## XLS/XLSX và PDF

Luồng dùng API `Workbook/Sheet/Cell` chung, có test riêng HSSF và XSSF. Print area được chụp theo tên sheet trước khi reorder rồi phục hồi, tránh HSSF mất vùng in. Workbook Excel giữ sheet nguồn/phụ thuộc ở trạng thái ẩn để không phá công thức existing; workbook dành cho PDF chỉ giữ các sheet đã chọn và đóng băng công thức.
