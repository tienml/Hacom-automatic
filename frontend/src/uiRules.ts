import type { FieldAction, MaterialFamily, WorkItem, WorkItemSheetStatus } from './types'

export function familyLabel(family: MaterialFamily) {
  return family === 'VUA' ? 'Vữa' : family === 'BETONG' ? 'Bê tông' : 'Chưa xác định'
}

export function sheetStatusLabel(status: WorkItemSheetStatus) {
  return ({
    NO_SHEETS: 'Không có sheet',
    MAIN_ONLY: 'Chỉ có sheet chính',
    MISSING_LM: 'Thiếu LM',
    MISSING_GM: 'Thiếu GM',
    MISSING_LM_GM: 'Thiếu LM/GM',
    COMPLETE_SAMPLE_PAIR: 'Đã có đủ LM/GM',
    UNKNOWN_MATERIAL: 'Chưa xác định vật liệu',
  } as const)[status]
}

export function fieldLabel(field: string) {
  return ({
    itemNumber: 'Số danh mục', location: 'Vị trí', sampleDate: 'Ngày lấy mẫu',
    lmNumber: 'Số LM', gmNumber: 'Số GM', lmDescription: 'Mô tả LM',
    gmDescription: 'Mô tả GM', sequenceNumber: 'STT dòng mẫu', projectName: 'Tên dự án',
    packageName: 'Gói thầu', projectLocation: 'Địa điểm dự án', contractor: 'Nhà thầu',
    workContent: 'Đối tượng nghiệm thu', acceptanceDateTime: 'Ngày giờ nghiệm thu',
    acceptanceNumber: 'Số biên bản (NTCV)', requestNumber: 'Số phiếu yêu cầu (YCNT)',
    grade: 'Mác vật liệu', strengthClass: 'Cấp độ bền', specimenSize: 'Kích thước mẫu',
    sampleGroupCount: 'Số tổ mẫu', sampleCount: 'Số mẫu', samplesPerGroup: 'Số mẫu mỗi tổ',
    testAge: 'Tuổi mẫu', note: 'Ghi chú', testCriteria: 'Chỉ tiêu thí nghiệm',
    standard: 'Tiêu chuẩn', testPurpose: 'Mục đích thí nghiệm', storageLocation: 'Nơi lưu mẫu',
    deliveryLocation: 'Nơi gửi mẫu', deliveryDate: 'Ngày giao mẫu', deliveryTime: 'Giờ giao mẫu',
    laboratoryName: 'Phòng thí nghiệm', laboratoryCode: 'Mã LAS', receiver: 'Người nhận mẫu',
    laboratoryManager: 'Người phụ trách', additionalSampleRows: 'Dòng mẫu bổ sung',
  } as Record<string, string>)[field] ?? field
}

export function actionLabel(action: FieldAction) {
  return action === 'POPULATE' ? 'Điền' : action === 'CLEAR' ? 'Để trống' : 'Giữ cấu trúc'
}

export function canSelectWorkItem(_item: WorkItem) {
  return true
}

export function requiresManualFamily(item: WorkItem, selectedFamily?: MaterialFamily) {
  return !item.hasCompleteSamplePair && (selectedFamily ?? item.materialFamily) === 'UNKNOWN'
}

export function countSelectedSheets(selectedSheetsByItem: Record<string, string[]>) {
  return Object.values(selectedSheetsByItem).reduce((total, sheets) => total + sheets.length, 0)
}

export function hasIncompleteSelection(
  items: WorkItem[],
  selectedSheetsByItem: Record<string, string[]>,
) {
  return items.some((item) => (selectedSheetsByItem[item.itemNumber] ?? []).length === 0)
}
