import { useRef, useState, type DragEvent } from 'react'
import type { AnalyzeResponse, SystemStatus } from '../types'
import {
  BuildingIcon,
  CheckIcon,
  ExcelIcon,
  FileIcon,
  ListIcon,
  RefreshIcon,
  UploadIcon,
} from '../icons'
import { formatBytes, formatDateTime, textOrDash } from '../utils'

export function UploadPage({
  analysis,
  status,
  busy,
  onAnalyze,
  onContinue,
  onResetAnalysis,
}: {
  analysis: AnalyzeResponse | null
  status: SystemStatus | null
  busy: boolean
  onAnalyze: (file: File) => Promise<void>
  onContinue: () => void
  onResetAnalysis: () => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [dragging, setDragging] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  function selectFile(next: File | null) {
    if (!next) return
    setFile(next)
    onResetAnalysis()
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    selectFile(event.dataTransfer.files?.[0] ?? null)
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <h1>Tải file BBNT</h1>
        <p>Tải lên file BBNT để hệ thống tự động phân tích sheet DM và trích xuất danh mục công việc.</p>
      </section>

      <section className="surface upload-surface">
        <input
          ref={fileInput}
          className="sr-only"
          type="file"
          accept=".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
        />
        <div
          className={`drop-area ${dragging ? 'dragging' : ''}`}
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <span className="drop-file-icon"><ExcelIcon size={34} /></span>
          <h2>{file ? file.name : 'Kéo & thả file Excel vào đây'}</h2>
          <p>{file ? `${formatBytes(file.size)} · Sẵn sàng phân tích` : 'hoặc'}</p>
          <button type="button" className="primary-button upload-button" onClick={() => fileInput.current?.click()} disabled={busy}>
            <FileIcon size={19} /> Chọn file
          </button>
          <small>Hỗ trợ .xls, .xlsx · Dung lượng tối đa 50 MB</small>
        </div>

        {file && (
          <div className="uploaded-file-row">
            <span className="excel-file-badge"><ExcelIcon size={20} /></span>
            <div>
              <strong>{file.name}</strong>
              <span>{formatBytes(file.size)}</span>
            </div>
            {analysis ? (
              <span className="success-pill"><CheckIcon size={15} /> Đã phân tích</span>
            ) : (
              <button className="primary-button compact" type="button" onClick={() => onAnalyze(file)} disabled={busy}>
                <UploadIcon size={18} /> {busy ? 'Đang phân tích…' : 'Phân tích file'}
              </button>
            )}
          </div>
        )}
      </section>

      {analysis && (
        <section className="surface analysis-surface">
          <div className="section-toolbar">
            <div className="toolbar-title">
              <span className="soft-icon gold"><ListIcon /></span>
              <div>
                <h2>Kết quả phân tích</h2>
                <p>Hệ thống đã đọc workbook và trích xuất dữ liệu thành công.</p>
              </div>
            </div>
            <div className="toolbar-actions">
              <span className="timestamp">{formatDateTime(analysis.createdAt)}</span>
              <button className="secondary-button compact" type="button" onClick={() => file && onAnalyze(file)} disabled={busy}>
                <RefreshIcon size={17} /> Phân tích lại
              </button>
            </div>
          </div>

          <div className="metric-grid upload-metrics">
            <MetricCard icon={<FileIcon />} label="Sheet DM" value="Đã tìm thấy" valueClass="success-text" note={`Sheet “${analysis.dmSheetName}” hợp lệ`} />
            <MetricCard icon={<ListIcon />} label="Số công việc" value={String(analysis.workItemCount)} note="Dòng công việc được phát hiện" />
            <MetricCard icon={<ExcelIcon />} label="Số biểu mẫu" value={String(analysis.outputSheetCount)} note="Sheet đầu ra có thể sinh" />
            <MetricCard icon={<BuildingIcon />} label="Dự án" value={textOrDash(analysis.project.projectName)} note="Tên dự án đọc từ DM" />
          </div>

          {!status?.pdfAvailable && (
            <div className="inline-warning">
              Excel vẫn có thể xuất bình thường. Preview PDF chưa hoạt động: {status?.message ?? 'chưa kết nối được backend PDF.'}
            </div>
          )}

          <div className="center-action">
            <button className="primary-button large" type="button" onClick={onContinue}>
              <ListIcon size={19} /> Đọc danh mục công việc
            </button>
          </div>
        </section>
      )}
    </div>
  )
}

function MetricCard({
  icon,
  label,
  value,
  note,
  valueClass = '',
}: {
  icon: React.ReactNode
  label: string
  value: string
  note: string
  valueClass?: string
}) {
  return (
    <article className="metric-card">
      <span className="metric-icon">{icon}</span>
      <div>
        <span className="metric-label">{label}</span>
        <strong className={valueClass}>{value}</strong>
        <small>{note}</small>
      </div>
    </article>
  )
}
