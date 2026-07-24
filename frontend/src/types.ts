export type GenerationMode = 'EXISTING_SHEET' | 'CLONE_TEMPLATE'
export type MaterialFamily = 'VUA' | 'BETONG' | 'UNKNOWN'
export type DocumentType = 'MAIN' | 'LM' | 'GM' | 'UNKNOWN'
export type OutputAvailability = 'EXISTING' | 'GENERATABLE' | 'MISSING_TEMPLATE' | 'NOT_APPLICABLE'
export type WorkItemSheetStatus = 'NO_SHEETS' | 'MAIN_ONLY' | 'MISSING_LM' | 'MISSING_GM' | 'MISSING_LM_GM' | 'COMPLETE_SAMPLE_PAIR' | 'UNKNOWN_MATERIAL'
export type DataCertainty = 'CERTAIN' | 'UNCERTAIN' | 'UNKNOWN'
export type FieldAction = 'POPULATE' | 'CLEAR' | 'KEEP_TEMPLATE_STRUCTURE'

export type ProjectSummary = {
  projectName: string | null
  location: string | null
  packageName: string | null
  contractor: string | null
}

export type FieldDecision = {
  fieldName: string
  source: string
  value: string | null
  certainty: DataCertainty
  action: FieldAction
  targetCells: string[]
  targetRanges: string[]
  documentType: DocumentType
  reason: string
}

export type TemplatePair = {
  lm: string
  gm: string
  reason: string
  recommended: boolean
  profileCompatible: boolean
  mergedRegionCount: number
  drawingCount: number
  hasPrintArea: boolean
  validationWarnings: string[]
}

export type DocumentPlan = {
  documentType: DocumentType
  availability: OutputAvailability
  generationMode: GenerationMode | null
  existingSheetName: string | null
  plannedSheetName: string | null
  materialFamily: MaterialFamily
  sourceTemplate: string | null
  availableSourceTemplates: string[]
  fieldDecisions: FieldDecision[]
  warnings: string[]
}

export type WorkItem = {
  itemNumber: string
  localOrder: string
  content: string
  position: string
  majorCategory: string | null
  inspectionTime: string
  recordNumber: string
  sampleDate: string | null
  excelRow: number
  hasOutputSheets: boolean
  existingSheetNames: string[]
  hasMainSheet: boolean
  hasLmSheet: boolean
  hasGmSheet: boolean
  hasCompleteSamplePair: boolean
  hasPartialSamplePair: boolean
  sheetStatus: WorkItemSheetStatus
  generationMode: GenerationMode
  materialFamily: MaterialFamily
  detectionReason: string
  templatePair: TemplatePair | null
  availableTemplatePairs: TemplatePair[]
  requiresTemplateSelection: boolean
  mainPlan: DocumentPlan
  lmPlan: DocumentPlan
  gmPlan: DocumentPlan
  fieldDecisions: FieldDecision[]
  autoFilledFields: string[]
  blankFields: string[]
  warnings: string[]
}

export type AnalyzeResponse = {
  jobId: string
  fileName: string
  dmSheetName: string
  project: ProjectSummary
  analysisWarnings: string[]
  workItemCount: number
  outputSheetCount: number
  withSampleCount: number
  withoutSampleCount: number
  existingSheetCount: number
  cloneTemplateCount: number
  mainOnlyCount: number
  completeSamplePairCount: number
  partialSamplePairCount: number
  unknownMaterialCount: number
  workItems: WorkItem[]
  createdAt: string
  expiresAt: string
}

export type OutputSheet = {
  sheetName: string
  displayName: string
  type: string
  documentType: DocumentType
  description: string
  available: boolean
  generated: boolean
  sourceTemplate: string | null
  availableSourceTemplates: string[]
  generationMode: GenerationMode | null
  availability: OutputAvailability
  materialFamily: MaterialFamily
  fieldDecisions: FieldDecision[]
  warnings: string[]
}

export type GenerateOutputSelection = {
  sheetName: string
  documentType: DocumentType
  generationMode: GenerationMode
  sourceTemplate?: string | null
  fieldOverrides?: Record<string, string>
}

export type GenerateSelection = {
  itemNumber: string
  outputs: GenerateOutputSelection[]
  materialFamily?: MaterialFamily
}

export type GenerateResponse = {
  documentId: string
  workItemNumbers: string[]
  selectedSheets: string[]
  excelDownloadUrl: string
  pdfPreviewUrl: string | null
  pdfDownloadUrl: string | null
  pdfAvailable: boolean
  pdfMessage: string | null
  excelFileName: string
  pdfFileName: string | null
  excelSize: number
  pdfSize: number
  warnings: string[]
  createdAt: string
  expiresAt: string
}

export type SystemStatus = {
  application: string
  version: string
  configuredPdfMode: string
  activePdfEngine: string
  pdfAvailable: boolean
  message: string
}

export type PageKey = 'upload' | 'work-items' | 'templates' | 'preview'
