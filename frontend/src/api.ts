import type {
  AnalyzeResponse,
  GenerateResponse,
  OutputSheet,
  SystemStatus,
} from './types'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')

async function readJson<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message ?? `Yêu cầu thất bại (${response.status})`)
  }
  return body as T
}

export async function getSystemStatus(): Promise<SystemStatus> {
  const response = await fetch(`${API_BASE_URL}/api/v1/system/status`)
  return readJson<SystemStatus>(response)
}

export async function analyzeWorkbook(file: File): Promise<AnalyzeResponse> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`${API_BASE_URL}/api/v1/jobs/analyze`, {
    method: 'POST',
    body: form,
  })
  return readJson<AnalyzeResponse>(response)
}

export async function loadOutputs(jobId: string, number: number): Promise<OutputSheet[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/jobs/${jobId}/work-items/${number}/outputs`)
  return readJson<OutputSheet[]>(response)
}

export async function generateDocument(
  jobId: string,
  workItemNumber: number,
  selectedSheets: string[],
  createPdf = true,
): Promise<GenerateResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/jobs/${jobId}/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ workItemNumber, selectedSheets, createPdf }),
  })
  return readJson<GenerateResponse>(response)
}

export function absoluteApiUrl(path: string | null): string | null {
  return path ? `${API_BASE_URL}${path}` : null
}
