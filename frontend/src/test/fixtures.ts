import type { AnalyzeResponse, DocumentPlan, FieldDecision, OutputSheet, WorkItem } from '../types'

export const populateLocation: FieldDecision = {
  fieldName: 'location', source: 'DM.columnD', value: 'Tầng 2', certainty: 'CERTAIN', action: 'POPULATE',
  targetCells: ['D15'], targetRanges: [], documentType: 'LM', reason: 'Mapping DM đã xác minh.',
}
export const clearSize: FieldDecision = {
  fieldName: 'specimenSize', source: 'template', value: null, certainty: 'UNCERTAIN', action: 'CLEAR',
  targetCells: [], targetRanges: ['B30:M32'], documentType: 'LM', reason: 'Không có nguồn chắc chắn.',
}

export function plan(overrides: Partial<DocumentPlan> = {}): DocumentPlan {
  return {
    documentType: 'LM', availability: 'GENERATABLE', generationMode: 'CLONE_TEMPLATE', existingSheetName: null,
    plannedSheetName: '1.LMV (150)', materialFamily: 'VUA', sourceTemplate: '1.LMV (111)',
    availableSourceTemplates: ['1.LMV (111)', '1.LMV (114)'], fieldDecisions: [populateLocation, clearSize], warnings: [],
    ...overrides,
  }
}

export function workItem(overrides: Partial<WorkItem> = {}): WorkItem {
  return {
    itemNumber: '150', localOrder: '11', content: 'Chất lượng vữa xây', position: 'Tầng 2', majorCategory: 'HẠNG MỤC XÂY TƯỜNG',
    inspectionTime: '10:00 01/07/24', recordNumber: '1503/CB/NTCV/150', sampleDate: '02/07/24', excelRow: 150,
    hasOutputSheets: true, existingSheetNames: ['150'], hasMainSheet: true, hasLmSheet: false, hasGmSheet: false,
    hasCompleteSamplePair: false, hasPartialSamplePair: false, sheetStatus: 'MAIN_ONLY', generationMode: 'CLONE_TEMPLATE',
    materialFamily: 'VUA', detectionReason: 'Nội dung chứa vữa', templatePair: null, availableTemplatePairs: [],
    requiresTemplateSelection: false,
    mainPlan: plan({ documentType: 'MAIN', availability: 'EXISTING', generationMode: 'EXISTING_SHEET', existingSheetName: '150', plannedSheetName: null, sourceTemplate: null, availableSourceTemplates: [], fieldDecisions: [] }),
    lmPlan: plan(),
    gmPlan: plan({ documentType: 'GM', plannedSheetName: '1.GMV (150)', sourceTemplate: '1.GMV (111)', availableSourceTemplates: ['1.GMV (111)', '1.GMV (114)'], fieldDecisions: [{ ...populateLocation, documentType: 'GM' }, { ...clearSize, documentType: 'GM' }] }),
    fieldDecisions: [populateLocation, clearSize], autoFilledFields: ['location'], blankFields: ['specimenSize'], warnings: [],
    ...overrides,
  }
}

export function output(overrides: Partial<OutputSheet> = {}): OutputSheet {
  return {
    sheetName: '1.LMV (150)', displayName: 'Biên bản lấy mẫu', type: 'LM', documentType: 'LM', description: 'Tạo LM từ mẫu',
    available: true, generated: true, sourceTemplate: '1.LMV (111)', availableSourceTemplates: ['1.LMV (111)', '1.LMV (114)'],
    generationMode: 'CLONE_TEMPLATE', availability: 'GENERATABLE', materialFamily: 'VUA', fieldDecisions: [populateLocation, clearSize], warnings: [],
    ...overrides,
  }
}

export function analysis(items: WorkItem[]): AnalyzeResponse {
  return {
    jobId: 'job-1', fileName: 'BBNT.xlsx', dmSheetName: 'DM ', project: { projectName: 'Dự án', location: 'Hà Nội', packageName: 'Gói', contractor: 'Nhà thầu' },
    analysisWarnings: [], workItemCount: items.length, outputSheetCount: items.reduce((n, item) => n + item.existingSheetNames.length, 0),
    withSampleCount: items.filter((item) => item.sampleDate).length, withoutSampleCount: items.filter((item) => !item.sampleDate).length,
    existingSheetCount: items.filter((item) => item.hasOutputSheets).length, cloneTemplateCount: items.filter((item) => !item.hasCompleteSamplePair).length,
    mainOnlyCount: items.filter((item) => item.sheetStatus === 'MAIN_ONLY').length, completeSamplePairCount: items.filter((item) => item.hasCompleteSamplePair).length,
    partialSamplePairCount: items.filter((item) => item.hasPartialSamplePair).length, unknownMaterialCount: items.filter((item) => item.materialFamily === 'UNKNOWN').length,
    workItems: items, createdAt: '2026-07-20T00:00:00Z', expiresAt: '2026-07-20T01:00:00Z',
  }
}
