import { useEffect, useMemo, useRef, useState } from 'react'
import { AppLayout } from './components/AppLayout'
import { analyzeWorkbook, absoluteApiUrl, generateDocument, getSystemStatus, loadOutputs } from './api'
import { UploadPage } from './pages/UploadPage'
import { WorkItemsPage } from './pages/WorkItemsPage'
import { TemplatesPage } from './pages/TemplatesPage'
import { PreviewPage } from './pages/PreviewPage'
import type {
  AnalyzeResponse,
  GenerateResponse,
  GenerateSelection,
  MaterialFamily,
  OutputSheet,
  PageKey,
  SystemStatus,
  WorkItem,
} from './types'
import { AlertIcon, CheckIcon, XIcon } from './icons'

function App() {
  const [page, setPage] = useState<PageKey>('upload')
  const [analysis, setAnalysis] = useState<AnalyzeResponse | null>(null)
  const [selectedItems, setSelectedItems] = useState<WorkItem[]>([])
  const [outputsByItem, setOutputsByItem] = useState<Record<string, OutputSheet[]>>({})
  const [selectedSheetsByItem, setSelectedSheetsByItem] = useState<Record<string, string[]>>({})
  const [familyByItem, setFamilyByItem] = useState<Record<string, MaterialFamily>>({})
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
      clearSelectionState()
      setNotice(`Đã phân tích ${response.workItemCount} công việc từ sheet ${response.dmSheetName}.`)
    } catch (exception) {
      setError(messageOf(exception, 'Không thể phân tích file BBNT.'))
    } finally {
      setBusy(false)
    }
  }

  function toggleItem(item: WorkItem) {
    // Chỉ cho phép chọn 1 danh mục công việc tại một thời điểm (chọn kiểu radio).
    setSelectedItems((current) => current.some((value) => value.itemNumber === item.itemNumber)
      ? []
      : [item])
  }

  async function openTemplates() {
    if (!analysis || selectedItems.length === 0) return
    setBusy(true)
    setError(null)
    try {
      const families: Record<string, MaterialFamily> = {}
      const outputEntries = await Promise.all(selectedItems.map(async (item) => {
        const family = familyByItem[item.itemNumber] ?? item.materialFamily
        families[item.itemNumber] = family
        return [item.itemNumber, await loadOutputs(analysis.jobId, item.itemNumber, family)] as const
      }))
      const nextOutputs = Object.fromEntries(outputEntries)
      const nextSelected = Object.fromEntries(outputEntries.map(([itemNumber, outputs]) => [
        itemNumber,
        outputs.filter((output) => output.available).map((output) => output.sheetName),
      ]))
      setFamilyByItem((current) => ({ ...current, ...families }))
      setOutputsByItem(nextOutputs)
      setSelectedSheetsByItem(nextSelected)
      setPage('templates')
    } catch (exception) {
      setError(messageOf(exception, 'Không thể tải danh sách biểu mẫu.'))
    } finally {
      setBusy(false)
    }
  }

  async function refreshOutputs(
    itemNumber: string,
    family: MaterialFamily,
    lmTemplateSheet?: string | null,
    gmTemplateSheet?: string | null,
  ) {
    if (!analysis) return
    setBusy(true)
    setError(null)
    try {
      const outputs = await loadOutputs(analysis.jobId, itemNumber, family, lmTemplateSheet, gmTemplateSheet)
      setFamilyByItem((current) => ({ ...current, [itemNumber]: family }))
      setOutputsByItem((current) => ({ ...current, [itemNumber]: outputs }))
      setSelectedSheetsByItem((current) => {
        const previous = new Set(current[itemNumber] ?? [])
        const selected = outputs.filter((output) => output.available && (previous.size === 0 || previous.has(output.sheetName)))
          .map((output) => output.sheetName)
        return { ...current, [itemNumber]: selected.length ? selected : outputs.filter((output) => output.available).map((output) => output.sheetName) }
      })
    } catch (exception) {
      setError(messageOf(exception, 'Không tải được quyết định biểu mẫu mới.'))
    } finally {
      setBusy(false)
    }
  }

  function changeFamily(itemNumber: string, family: MaterialFamily) {
    void refreshOutputs(itemNumber, family)
  }

  function changeTemplate(itemNumber: string, documentType: 'LM' | 'GM', sourceTemplate: string) {
    const family = familyByItem[itemNumber]
      ?? selectedItems.find((item) => item.itemNumber === itemNumber)?.materialFamily
      ?? 'UNKNOWN'
    const outputs = outputsByItem[itemNumber] ?? []
    const lmTemplate = documentType === 'LM' ? sourceTemplate : outputs.find((output) => output.documentType === 'LM')?.sourceTemplate
    const gmTemplate = documentType === 'GM' ? sourceTemplate : outputs.find((output) => output.documentType === 'GM')?.sourceTemplate
    void refreshOutputs(itemNumber, family, lmTemplate, gmTemplate)
  }

  function toggleSheet(itemNumber: string, sheet: string) {
    setSelectedSheetsByItem((current) => {
      const selected = current[itemNumber] ?? []
      return {
        ...current,
        [itemNumber]: selected.includes(sheet)
          ? selected.filter((value) => value !== sheet)
          : [...selected, sheet],
      }
    })
  }

  function selectAllSheets(itemNumber: string) {
    const all = (outputsByItem[itemNumber] ?? []).filter((output) => output.available).map((output) => output.sheetName)
    setSelectedSheetsByItem((current) => ({
      ...current,
      [itemNumber]: (current[itemNumber] ?? []).length === all.length ? [] : all,
    }))
  }

  async function handleGenerate() {
    if (!analysis || selectedItems.length === 0) return
    const incomplete = selectedItems.find((item) => (selectedSheetsByItem[item.itemNumber] ?? []).length === 0)
    if (incomplete) {
      setError(`DM ${incomplete.itemNumber} chưa có biểu mẫu được chọn.`)
      return
    }

    const selections: GenerateSelection[] = selectedItems.map((item) => {
      const selectedNames = new Set(selectedSheetsByItem[item.itemNumber] ?? [])
      const outputs = (outputsByItem[item.itemNumber] ?? []).filter((output) => selectedNames.has(output.sheetName))
      return {
        itemNumber: item.itemNumber,
        materialFamily: familyByItem[item.itemNumber] ?? item.materialFamily,
        outputs: outputs.map((output) => ({
          sheetName: output.sheetName,
          documentType: output.documentType,
          generationMode: output.generationMode!,
          sourceTemplate: output.sourceTemplate,
        })),
      }
    })

    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const response = await generateDocument(analysis.jobId, selections, true)
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

  function clearSelectionState() {
    setSelectedItems([])
    setOutputsByItem({})
    setSelectedSheetsByItem({})
    setFamilyByItem({})
    setResult(null)
  }

  function resetAll() {
    setPage('upload')
    setAnalysis(null)
    clearSelectionState()
    setError(null)
    setNotice(null)
  }

  function resetAnalysisOnly() {
    setAnalysis(null)
    clearSelectionState()
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
    if (target === 'templates') return Boolean(analysis && selectedItems.length && Object.keys(outputsByItem).length)
    return Boolean(analysis && selectedItems.length && result)
  }

  return (
    <AppLayout page={page} onNavigate={navigate} canNavigate={canNavigate} status={status}>
      {busy && <div className="global-progress"><span /> Đang xử lý file…</div>}
      {error && <Toast type="error" message={error} onClose={() => setError(null)} />}
      {notice && <Toast type="success" message={notice} onClose={() => setNotice(null)} />}

      {page === 'upload' && (
        <UploadPage analysis={analysis} status={status} busy={busy} onAnalyze={handleAnalyze}
          onContinue={() => setPage('work-items')} onResetAnalysis={resetAnalysisOnly} />
      )}

      {page === 'work-items' && analysis && (
        <WorkItemsPage analysis={analysis} selectedItems={selectedItems} onToggle={toggleItem}
          onContinue={openTemplates} busy={busy} />
      )}

      {page === 'templates' && analysis && selectedItems.length > 0 && (
        <TemplatesPage
          items={selectedItems}
          outputsByItem={outputsByItem}
          selectedSheetsByItem={selectedSheetsByItem}
          familyByItem={familyByItem}
          onChangeFamily={changeFamily}
          onChangeTemplate={changeTemplate}
          onToggle={toggleSheet}
          onSelectAll={selectAllSheets}
          onBack={() => setPage('work-items')}
          onGenerate={handleGenerate}
          status={status}
          busy={busy}
        />
      )}

      {page === 'preview' && analysis && selectedItems.length > 0 && result && excelUrl && (
        <PreviewPage analysis={analysis} items={selectedItems} result={result} excelUrl={excelUrl}
          pdfPreviewUrl={pdfPreviewUrl} pdfDownloadUrl={pdfDownloadUrl} onRegenerate={() => setPage('templates')} />
      )}

      {page !== 'upload' && (
        <button className="floating-reset" type="button" onClick={resetAll} title="Tải file BBNT khác">Tải file khác</button>
      )}
    </AppLayout>
  )
}

function Toast({ type, message, onClose }: { type: 'error' | 'success'; message: string; onClose: () => void }) {
  const autoCloseMs = type === 'error' ? 6000 : 4000
  const onCloseRef = useRef(onClose)
  useEffect(() => { onCloseRef.current = onClose }, [onClose])
  useEffect(() => {
    const timeoutId = window.setTimeout(() => onCloseRef.current(), autoCloseMs)
    return () => window.clearTimeout(timeoutId)
  }, [autoCloseMs, message])
  return (
    <div className={`toast ${type}`} role="status" aria-live="polite">
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
