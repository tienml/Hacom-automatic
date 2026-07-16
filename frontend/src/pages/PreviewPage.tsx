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
    <div className="page-stack preview-page">
      <section className="page-heading preview-heading">
        <div className="page-heading-with-icon">
          <span className="heading-icon red"><PdfIcon size={25} /></span>
          <div>
            <h1>Xem trước & Xuất</h1>
            <p>Kiểm tra nội dung hồ sơ trước khi tải xuống hoặc in.</p>
          </div>
        </div>
        <span className="success-banner"><CheckIcon size={17} /> Tạo file thành công <small>{formatDateTime(result.createdAt)}</small></span>
      </section>

      <section className="surface result-summary-strip">
        <ResultSummary icon={<BuildingIcon />} label="Dự án" value={textOrDash(analysis.project.projectName)} />
        <ResultSummary icon={<FileIcon />} label="Danh mục" value={`DM ${item.number}`} />
        <ResultSummary icon={<TemplateIcon />} label="Biểu mẫu" value={`${result.selectedSheets.length} sheet`} />
        <ResultSummary icon={<ClockIcon />} label="Thời gian tạo" value={formatDateTime(result.createdAt)} />
      </section>

      <div className="preview-layout">
        <section className="pdf-viewer surface-dark">
          <div className="viewer-toolbar">
            <div className="viewer-file-name"><PdfIcon size={17} /> <span>{result.pdfFileName ?? `Hồ sơ DM ${item.number}`}</span></div>
            <div className="viewer-toolbar-actions">
              {pdfDownloadUrl && <a href={pdfDownloadUrl} aria-label="Tải PDF" title="Tải PDF"><DownloadIcon size={18} /></a>}
              {pdfPreviewUrl && <button type="button" onClick={openPrint} aria-label="Mở để in" title="Mở để in"><PrinterIcon size={18} /></button>}
            </div>
          </div>
          {pdfPreviewUrl ? (
            <iframe title={`Preview hồ sơ ${item.number}`} src={pdfPreviewUrl} />
          ) : (
            <div className="pdf-unavailable">
              <PdfIcon size={56} />
              <h2>Chưa tạo được preview PDF</h2>
              <p>{result.pdfMessage ?? 'Backend chưa kết nối được Gotenberg hoặc LibreOffice.'}</p>
              <p>File Excel đã được tạo thành công và vẫn có thể tải xuống.</p>
            </div>
          )}
        </section>

        <aside className="preview-side-panel">
          <section className="surface document-info">
            <h2>Thông tin file</h2>
            <InfoRow label="Tên Excel" value={result.excelFileName} />
            <InfoRow label="Tên PDF" value={result.pdfFileName ?? '—'} />
            <InfoRow label="Dung lượng Excel" value={formatBytes(result.excelSize)} />
            <InfoRow label="Dung lượng PDF" value={result.pdfAvailable ? formatBytes(result.pdfSize) : '—'} />
            <InfoRow label="Số sheet" value={String(result.selectedSheets.length)} />
          </section>

          <section className="surface used-templates-card">
            <h2>Biểu mẫu đã sử dụng</h2>
            <div className="used-template-list">
              {result.selectedSheets.map((sheet, index) => (
                <div key={sheet}><span>{index + 1}</span><div><strong>Sheet {sheet}</strong><small>Đã đưa vào hồ sơ kết quả</small></div></div>
              ))}
            </div>
          </section>

          <section className="surface result-check-card">
            <h2>Trạng thái & kiểm tra</h2>
            <CheckRow text="Dữ liệu danh mục hợp lệ" />
            <CheckRow text="Đã chọn biểu mẫu" />
            <CheckRow text="File Excel đã tạo" />
            {result.pdfAvailable && <CheckRow text="PDF preview đã tạo" />}
            <span className="ready-handoff">Sẵn sàng tải xuống</span>
          </section>

          <section className="surface export-panel">
            <h2>Thao tác</h2>
            <a className="download-action primary-download" href={excelUrl}><DownloadIcon size={19} /> Tải Excel (.xls/.xlsx)</a>
            {pdfDownloadUrl ? (
              <a className="download-action primary-download" href={pdfDownloadUrl}><PdfIcon size={19} /> Tải PDF (.pdf)</a>
            ) : (
              <button className="download-action primary-download" type="button" disabled><PdfIcon size={19} /> PDF chưa có</button>
            )}
            <button className="download-action neutral" type="button" onClick={openPrint} disabled={!pdfPreviewUrl}><PrinterIcon size={19} /> In</button>
            <button className="download-action neutral" type="button" onClick={onRegenerate}><RefreshIcon size={19} /> Tạo lại file</button>
          </section>
        </aside>
      </div>
    </div>
  )
}

function ResultSummary({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <div className="result-summary"><span className="result-summary-icon">{icon}</span><div><span>{label}</span><strong title={value}>{value}</strong></div></div>
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <div className="info-row"><span>{label}</span><strong title={value}>{value}</strong></div>
}

function CheckRow({ text }: { text: string }) {
  return <div className="check-row"><CheckIcon size={15} /><span>{text}</span></div>
}
