export type ProjectSummary = {
  projectName: string | null
  location: string | null
  packageName: string | null
  contractor: string | null
}

export type WorkItem = {
  number: number
  localOrder: string
  content: string
  position: string
  inspectionTime: string
  recordNumber: string
  sampleDate: string | null
  excelRow: number
  hasOutputSheets: boolean
}

export type AnalyzeResponse = {
  jobId: string
  fileName: string
  dmSheetName: string
  project: ProjectSummary
  workItemCount: number
  outputSheetCount: number
  withSampleCount: number
  withoutSampleCount: number
  workItems: WorkItem[]
  createdAt: string
  expiresAt: string
}

export type OutputSheet = {
  sheetName: string
  displayName: string
  type: string
  description: string
  available: boolean
}

export type GenerateResponse = {
  documentId: string
  workItemNumber: number
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
