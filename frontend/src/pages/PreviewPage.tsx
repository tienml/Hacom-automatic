import type { AnalyzeResponse, GenerateResponse, WorkItem } from '../types'
import {
  BuildingIcon,
  CheckIcon,
  ClockIcon,
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
  items,
  result,
  excelUrl,
  pdfPreviewUrl,
  pdfDownloadUrl,
  onRegenerate,
}: {
  analysis: AnalyzeResponse
  items: WorkItem[]
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
    <div className="page-stack preview-page">
      <section className="page-heading preview-heading">
        <div className="page-heading-with-icon"><span className="heading-icon red"><PdfIcon size={25} /></span><div><h1>Xem trước & Xuất</h1><p>Kiểm tra hồ sơ hỗn hợp trước khi tải xuống hoặc in.</p></div></div>
        <span className="success-banner"><CheckIcon size={17} /> Tạo file thành công <small>{formatDateTime(result.createdAt)}</small></span>
      </section>

      <section className="surface result-summary-strip">
        <ResultSummary icon={<BuildingIcon />} label="Dự án" value={textOrDash(analysis.project.projectName)} />
        <ResultSummary icon={<FileIcon />} label="Danh mục" value={result.workItemNumbers.map((value) => `DM ${value}`).join(', ')} />
        <ResultSummary icon={<TemplateIcon />} label="Biểu mẫu" value={`${result.selectedSheets.length} sheet`} />
        <ResultSummary icon={<ClockIcon />} label="Hết hạn" value={formatDateTime(result.expiresAt)} />
      </section>

      {result.warnings.length > 0 && <section className="inline-warning result-warning-list"><div><strong>Cảnh báo dữ liệu</strong>{result.warnings.map((warning) => <p key={warning}>• {warning}</p>)}</div></section>}

      <div className="preview-layout">
        <section className="surface preview-canvas">
          <div className="preview-toolbar"><div><strong>{result.pdfAvailable ? 'Bản xem trước PDF' : 'Tóm tắt file Excel'}</strong><small>{result.pdfAvailable ? result.pdfFileName : result.excelFileName}</small></div><span>{result.selectedSheets.length} sheet</span></div>
          {pdfPreviewUrl ? <iframe title="Xem trước hồ sơ PDF" src={pdfPreviewUrl} className="pdf-frame" /> : (
            <div className="pdf-unavailable"><ExcelIcon size={58} /><h2>Excel đã được tạo thành công</h2><p>{result.pdfMessage ?? 'Môi trường chưa có dịch vụ chuyển đổi PDF.'}</p><a className="primary-button" href={excelUrl}><DownloadIcon size={18} /> Tải Excel</a></div>
          )}
        </section>

        <aside className="preview-sidebar">
          <section className="surface document-info-card">
            <h2>Thông tin file</h2>
            <InfoRow label="Excel" value={`${result.excelFileName} · ${formatBytes(result.excelSize)}`} />
            <InfoRow label="PDF" value={result.pdfAvailable ? `${result.pdfFileName} · ${formatBytes(result.pdfSize)}` : 'Chưa có'} />
            <InfoRow label="Dòng DM" value={String(items.length)} />
            <InfoRow label="Sheet" value={String(result.selectedSheets.length)} />
            <div className="summary-divider" />
            <h3>Sheet đã xuất</h3>
            <div className="preview-sheet-list">{result.selectedSheets.map((sheet) => <CheckRow text={sheet} key={sheet} />)}</div>
          </section>
          <section className="download-actions">
            <a className="download-action excel" href={excelUrl}><ExcelIcon size={19} /> Tải Excel</a>
            {pdfDownloadUrl ? <a className="download-action primary-download" href={pdfDownloadUrl}><PdfIcon size={19} /> Tải PDF</a> : <button className="download-action primary-download" type="button" disabled><PdfIcon size={19} /> PDF chưa có</button>}
            <button className="download-action neutral" type="button" onClick={openPrint} disabled={!pdfPreviewUrl}><PrinterIcon size={19} /> In</button>
            <button className="download-action neutral" type="button" onClick={onRegenerate}><RefreshIcon size={19} /> Tạo lại file</button>
          </section>
        </aside>
      </div>
    </div>
  )
}

function ResultSummary({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) { return <div className="result-summary"><span className="result-summary-icon">{icon}</span><div><span>{label}</span><strong title={value}>{value}</strong></div></div> }
function InfoRow({ label, value }: { label: string; value: string }) { return <div className="info-row"><span>{label}</span><strong title={value}>{value}</strong></div> }
function CheckRow({ text }: { text: string }) { return <div className="check-row"><CheckIcon size={15} /><span>{text}</span></div> }
