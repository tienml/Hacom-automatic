import { useEffect, useMemo, useState } from 'react'
import { AppLayout } from './components/AppLayout'
import { analyzeWorkbook, absoluteApiUrl, generateDocument, getSystemStatus, loadOutputs } from './api'
import { UploadPage } from './pages/UploadPage'
import { WorkItemsPage } from './pages/WorkItemsPage'
import { TemplatesPage } from './pages/TemplatesPage'
import { PreviewPage } from './pages/PreviewPage'
import type {
  AnalyzeResponse,
  GenerateResponse,
  OutputSheet,
  PageKey,
  SystemStatus,
  WorkItem,
} from './types'
import { AlertIcon, CheckIcon, XIcon } from './icons'

function App() {
  const [page, setPage] = useState<PageKey>('upload')
  const [analysis, setAnalysis] = useState<AnalyzeResponse | null>(null)
  const [selectedItem, setSelectedItem] = useState<WorkItem | null>(null)
  const [outputs, setOutputs] = useState<OutputSheet[]>([])
  const [selectedSheets, setSelectedSheets] = useState<string[]>([])
  const [result, setResult] = useState<GenerateResponse | null>(null)
  const [status, setStatus] = useState<SystemStatus | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    getSystemStatus().then(setStatus).catch(() => {
      setStatus({
        application: 'HaCom BBNT Automation',
        version: '1.0.0',
        configuredPdfMode: 'unknown',
        activePdfEngine: 'none',
        pdfAvailable: false,
        message: 'Không đọc được trạng thái backend PDF.',
      })
    })
  }, [])

  const excelUrl = useMemo(() => absoluteApiUrl(result?.excelDownloadUrl ?? null), [result])
  const pdfPreviewUrl = useMemo(() => absoluteApiUrl(result?.pdfPreviewUrl ?? null), [result])
  const pdfDownloadUrl = useMemo(() => absoluteApiUrl(result?.pdfDownloadUrl ?? null), [result])

  async function handleAnalyze(file: File) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const response = await analyzeWorkbook(file)
      setAnalysis(response)
      setSelectedItem(null)
      setOutputs([])
      setSelectedSheets([])
      setResult(null)
      setNotice(`Đã phân tích ${response.workItemCount} công việc từ sheet ${response.dmSheetName}.`)
    } catch (exception) {
      setError(messageOf(exception, 'Không thể phân tích file BBNT.'))
    } finally {
      setBusy(false)
    }
  }

  async function openTemplates() {
    if (!analysis || !selectedItem) return
    setBusy(true)
    setError(null)
    try {
      const response = await loadOutputs(analysis.jobId, selectedItem.number)
      if (response.length === 0) throw new Error(`Danh mục ${selectedItem.number} chưa có sheet đầu ra.`)
      setOutputs(response)
      setSelectedSheets(response.filter((output) => output.available).map((output) => output.sheetName))
      setPage('templates')
    } catch (exception) {
      setError(messageOf(exception, 'Không thể tải danh sách biểu mẫu.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleGenerate() {
    if (!analysis || !selectedItem || selectedSheets.length === 0) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const response = await generateDocument(
        analysis.jobId,
        selectedItem.number,
        selectedSheets,
        true,
      )
      setResult(response)
      setPage('preview')
      setNotice(response.pdfAvailable
        ? 'Đã tạo Excel và PDF thành công.'
        : 'Đã tạo Excel. PDF chưa tạo được, xem thông báo tại trang preview.')
      getSystemStatus().then(setStatus).catch(() => undefined)
    } catch (exception) {
      setError(messageOf(exception, 'Không thể tạo hồ sơ.'))
    } finally {
      setBusy(false)
    }
  }

  function resetAll() {
    setPage('upload')
    setAnalysis(null)
    setSelectedItem(null)
    setOutputs([])
    setSelectedSheets([])
    setResult(null)
    setError(null)
    setNotice(null)
  }

  function resetAnalysisOnly() {
    setAnalysis(null)
    setSelectedItem(null)
    setOutputs([])
    setSelectedSheets([])
    setResult(null)
    setPage('upload')
    setError(null)
    setNotice(null)
  }

  function navigate(nextPage: PageKey) {
    if (!canNavigate(nextPage)) return
    setPage(nextPage)
    setError(null)
  }

  function canNavigate(target: PageKey) {
    if (target === 'upload') return true
    if (target === 'work-items') return Boolean(analysis)
    if (target === 'templates') return Boolean(analysis && selectedItem && outputs.length)
    return Boolean(analysis && selectedItem && result)
  }

  function toggleSheet(sheet: string) {
    setSelectedSheets((current) => current.includes(sheet)
      ? current.filter((value) => value !== sheet)
      : [...current, sheet])
  }

  function selectAllSheets() {
    const all = outputs.filter((output) => output.available).map((output) => output.sheetName)
    setSelectedSheets((current) => current.length === all.length ? [] : all)
  }

  return (
    <AppLayout page={page} onNavigate={navigate} canNavigate={canNavigate} status={status}>
      {busy && <div className="global-progress"><span /> Đang xử lý file, vui lòng chờ…</div>}
      {error && <Toast type="error" message={error} onClose={() => setError(null)} />}
      {notice && <Toast type="success" message={notice} onClose={() => setNotice(null)} />}

      {page === 'upload' && (
        <UploadPage
          analysis={analysis}
          status={status}
          busy={busy}
          onAnalyze={handleAnalyze}
          onContinue={() => setPage('work-items')}
          onResetAnalysis={resetAnalysisOnly}
        />
      )}

      {page === 'work-items' && analysis && (
        <WorkItemsPage
          analysis={analysis}
          selectedItem={selectedItem}
          onSelect={setSelectedItem}
          onContinue={openTemplates}
          busy={busy}
        />
      )}

      {page === 'templates' && selectedItem && (
        <TemplatesPage
          item={selectedItem}
          outputs={outputs}
          selectedSheets={selectedSheets}
          onToggle={toggleSheet}
          onSelectAll={selectAllSheets}
          onBack={() => setPage('work-items')}
          onGenerate={handleGenerate}
          status={status}
          busy={busy}
        />
      )}

      {page === 'preview' && analysis && selectedItem && result && excelUrl && (
        <PreviewPage
          analysis={analysis}
          item={selectedItem}
          result={result}
          excelUrl={excelUrl}
          pdfPreviewUrl={pdfPreviewUrl}
          pdfDownloadUrl={pdfDownloadUrl}
          onRegenerate={() => setPage('templates')}
        />
      )}

      {page !== 'upload' && (
        <button className="floating-reset" type="button" onClick={resetAll} title="Tải file BBNT khác">
          Tải file khác
        </button>
      )}
    </AppLayout>
  )
}

function Toast({ type, message, onClose }: { type: 'error' | 'success'; message: string; onClose: () => void }) {
  return (
    <div className={`toast ${type}`} role="status">
      <span className="toast-icon">{type === 'error' ? <AlertIcon size={19} /> : <CheckIcon size={19} />}</span>
      <span>{message}</span>
      <button type="button" onClick={onClose} aria-label="Đóng thông báo"><XIcon size={17} /></button>
    </div>
  )
}

function messageOf(exception: unknown, fallback: string) {
  return exception instanceof Error && exception.message ? exception.message : fallback
}

export default App
