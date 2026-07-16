import type { AnalyzeResponse, GenerateResponse, WorkItem } from '../types'
import {
  BuildingIcon,
  CheckIcon,
  DownloadIcon,
  ExcelIcon,
  FileIcon,
  PdfIcon,
  PrinterIcon,
  RefreshIcon,
  TemplateIcon,
} from '../icons'
import { formatBytes, formatDateTime, textOrDash } from '../utils'

export function PreviewPage({
  analysis,
  item,
  result,
  excelUrl,
  pdfPreviewUrl,
  pdfDownloadUrl,
  onRegenerate,
}: {
  analysis: AnalyzeResponse
  item: WorkItem
  result: GenerateResponse
  excelUrl: string
  pdfPreviewUrl: string | null
  pdfDownloadUrl: string | null
  onRegenerate: () => void
}) {
  function openPrint() {
    if (pdfPreviewUrl) window.open(pdfPreviewUrl, '_blank', 'noopener,noreferrer')
  }

  return (
    <div className="page-stack">
      <section className="page-heading preview-heading">
        <div>
          <h1>Xem trước & Xuất</h1>
          <p>Kiểm tra hồ sơ trước khi tải xuống hoặc in.</p>
        </div>
        <span className="success-banner"><CheckIcon size={17} /> Đã tạo hồ sơ thành công</span>
      </section>

      <section className="surface result-summary-strip">
        <ResultSummary icon={<FileIcon />} label="Danh mục" value={String(item.number)} />
        <ResultSummary icon={<TemplateIcon />} label="Biểu mẫu" value={`${result.selectedSheets.length} sheet`} />
        <ResultSummary icon={<><ExcelIcon size={20} /><PdfIcon size={20} /></>} label="Đầu ra" value={result.pdfAvailable ? 'Excel + PDF' : 'Excel'} />
        <ResultSummary icon={<BuildingIcon />} label="Dự án" value={textOrDash(analysis.project.projectName)} />
      </section>

      <div className="preview-layout">
        <section className="pdf-viewer surface-dark">
          <div className="viewer-toolbar">
            <span>Preview hồ sơ DM {item.number}</span>
            <div className="viewer-toolbar-actions">
              {pdfDownloadUrl && <a href={pdfDownloadUrl} aria-label="Tải PDF"><DownloadIcon size={18} /></a>}
              {pdfPreviewUrl && <button type="button" onClick={openPrint} aria-label="Mở để in"><PrinterIcon size={18} /></button>}
            </div>
          </div>
          {pdfPreviewUrl ? (
            <iframe title={`Preview hồ sơ ${item.number}`} src={pdfPreviewUrl} />
          ) : (
            <div className="pdf-unavailable">
              <PdfIcon size={54} />
              <h2>Chưa tạo được preview PDF</h2>
              <p>{result.pdfMessage ?? 'Backend chưa có Gotenberg hoặc LibreOffice.'}</p>
              <p>File Excel đã được tạo thành công và vẫn có thể tải xuống.</p>
            </div>
          )}
        </section>

        <aside className="preview-side-panel">
          <section className="surface export-panel">
            <h2>Xuất & Tải xuống</h2>
            <a className="download-action excel" href={excelUrl}><ExcelIcon size={20} /> Tải Excel</a>
            {pdfDownloadUrl ? (
              <a className="download-action pdf" href={pdfDownloadUrl}><PdfIcon size={20} /> Tải PDF</a>
            ) : (
              <button className="download-action pdf" type="button" disabled><PdfIcon size={20} /> PDF chưa có</button>
            )}
            <button className="download-action neutral" type="button" onClick={openPrint} disabled={!pdfPreviewUrl}><PrinterIcon size={20} /> In</button>
            <button className="download-action neutral" type="button" onClick={onRegenerate}><RefreshIcon size={20} /> Tạo lại</button>
          </section>

          <section className="surface document-info">
            <h2>Thông tin hồ sơ</h2>
            <InfoRow label="Tạo lúc" value={formatDateTime(result.createdAt)} />
            <InfoRow label="Số DM" value={String(item.number)} />
            <InfoRow label="Định dạng" value={result.pdfAvailable ? 'Excel, PDF' : 'Excel'} />
            <InfoRow label="Số sheet" value={String(result.selectedSheets.length)} />
            <InfoRow label="Excel" value={formatBytes(result.excelSize)} />
            <InfoRow label="PDF" value={result.pdfAvailable ? formatBytes(result.pdfSize) : '—'} />
          </section>
        </aside>
      </div>
    </div>
  )
}

function ResultSummary({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <div className="result-summary"><span className="result-summary-icon">{icon}</span><div><span>{label}</span><strong>{value}</strong></div></div>
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <div className="info-row"><span>{label}</span><strong>{value}</strong></div>
}
