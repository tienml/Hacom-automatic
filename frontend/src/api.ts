import type {
  AnalyzeResponse,
  GenerateResponse,
  GenerateSelection,
  MaterialFamily,
  OutputSheet,
  SystemStatus,
} from './types'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')

async function readJson<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message ?? `Yêu cầu thất bại (${response.status})`)
  return body as T
}

export async function getSystemStatus(): Promise<SystemStatus> {
  return readJson<SystemStatus>(await fetch(`${API_BASE_URL}/api/v1/system/status`))
}

export async function analyzeWorkbook(file: File): Promise<AnalyzeResponse> {
  const form = new FormData()
  form.append('file', file)
  return readJson<AnalyzeResponse>(await fetch(`${API_BASE_URL}/api/v1/jobs/analyze`, {
    method: 'POST',
    body: form,
  }))
}

export async function loadOutputs(
  jobId: string,
  itemNumber: string,
  materialFamily?: MaterialFamily,
  lmTemplateSheet?: string | null,
  gmTemplateSheet?: string | null,
): Promise<OutputSheet[]> {
  const params = new URLSearchParams()
  if (materialFamily && materialFamily !== 'UNKNOWN') params.set('materialFamily', materialFamily)
  if (lmTemplateSheet) params.set('lmTemplateSheet', lmTemplateSheet)
  if (gmTemplateSheet) params.set('gmTemplateSheet', gmTemplateSheet)
  const query = params.size ? `?${params.toString()}` : ''
  return readJson<OutputSheet[]>(await fetch(
    `${API_BASE_URL}/api/v1/jobs/${jobId}/work-items/${encodeURIComponent(itemNumber)}/outputs${query}`,
  ))
}

export async function generateDocument(
  jobId: string,
  selections: GenerateSelection[],
  createPdf = true,
): Promise<GenerateResponse> {
  return readJson<GenerateResponse>(await fetch(`${API_BASE_URL}/api/v1/jobs/${jobId}/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ selections, createPdf }),
  }))
}

export function absoluteApiUrl(path: string | null): string | null {
  return path ? `${API_BASE_URL}${path}` : null
}
