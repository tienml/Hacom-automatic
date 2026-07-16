import { useRef, useState, type DragEvent } from 'react'
import type { AnalyzeResponse, SystemStatus } from '../types'
import {
  BuildingIcon,
  CheckIcon,
  ExcelIcon,
  FileIcon,
  HelpIcon,
  LightbulbIcon,
  ListIcon,
  LockIcon,
  RefreshIcon,
  SearchIcon,
  TemplateIcon,
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
    <div className="page-stack upload-page">
      <section className="page-heading page-heading-with-icon">
        <span className="heading-icon red"><UploadIcon size={25} /></span>
        <div>
          <h1>Tải file BBNT</h1>
          <p>Tải file Excel BBNT để bắt đầu quy trình phân tích và tạo hồ sơ tự động.</p>
        </div>
      </section>

      <div className="upload-layout">
        <section className="surface upload-surface">
          <input
            ref={fileInput}
            className="sr-only"
            type="file"
            accept=".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
          />
          <div
            className={`drop-area ${dragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`}
            onDragOver={(event) => {
              event.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
          >
            <span className="drop-file-icon"><UploadIcon size={38} /></span>
            <h2>{file ? 'File đã sẵn sàng' : 'Kéo & thả file Excel vào đây'}</h2>
            <p>{file ? file.name : 'hoặc chọn file trực tiếp từ máy tính'}</p>
            <button type="button" className="primary-button upload-button" onClick={() => fileInput.current?.click()} disabled={busy}>
              <FileIcon size={19} /> {file ? 'Chọn file khác' : 'Chọn file từ máy tính'}
            </button>
            <small>Định dạng hỗ trợ: .xls, .xlsx · Tối đa 50 MB</small>
            <span className="security-note"><LockIcon size={15} /> File chỉ được lưu tạm để xử lý và tự động xóa theo thời hạn.</span>
          </div>
        </section>

        <section className="surface project-card">
          <div className="card-heading">
            <span className="soft-icon gold"><BuildingIcon size={21} /></span>
            <h2>Thông tin dự án</h2>
          </div>
          <dl className="project-details">
            <div><dt>Dự án</dt><dd>{analysis ? textOrDash(analysis.project.projectName) : 'Chưa phân tích'}</dd></div>
            <div><dt>Gói thầu</dt><dd>{analysis ? textOrDash(analysis.project.packageName) : 'Chưa phân tích'}</dd></div>
            <div><dt>Địa điểm</dt><dd>{analysis ? textOrDash(analysis.project.location) : 'Chưa phân tích'}</dd></div>
            <div><dt>Nhà thầu</dt><dd>{analysis ? textOrDash(analysis.project.contractor) : 'Chưa phân tích'}</dd></div>
            <div><dt>Ngày tải lên</dt><dd>{analysis ? formatDateTime(analysis.createdAt) : '—'}</dd></div>
          </dl>
          <p className="project-card-note">Các thông tin này được hệ thống đọc trực tiếp từ phần đầu sheet DM.</p>
        </section>

        <aside className="surface guide-card">
          <div className="card-heading">
            <span className="soft-icon gold"><HelpIcon size={21} /></span>
            <h2>Hướng dẫn sử dụng</h2>
          </div>
          <p className="guide-intro">Chỉ với 3 bước để tạo hồ sơ từ file BBNT.</p>
          <div className="guide-steps">
            <GuideStep number={1} icon={<UploadIcon size={19} />} title="Tải file BBNT" text="Chọn file .xls hoặc .xlsx từ dự án của bạn." />
            <GuideStep number={2} icon={<ListIcon size={19} />} title="Chọn công việc" text="Tìm và chọn số danh mục cần lập hồ sơ." />
            <GuideStep number={3} icon={<TemplateIcon size={19} />} title="Tạo và xuất file" text="Chọn biểu mẫu, xem trước PDF rồi tải Excel/PDF." />
          </div>
          <div className="guide-tip">
            <LightbulbIcon size={20} />
            <div><strong>Mẹo nhỏ</strong><p>Hãy dùng đúng cấu trúc file BBNT để hệ thống nhận diện sheet DM và các sheet đầu ra chính xác.</p></div>
          </div>
        </aside>
      </div>

      {file && (
        <section className="surface current-file-card">
          <div className="current-file-title"><span className="excel-file-badge"><ExcelIcon size={23} /></span><div><strong>{file.name}</strong><small>{formatBytes(file.size)}</small></div></div>
          <div className="current-file-meta">
            <span>Dự án <strong>{analysis ? textOrDash(analysis.project.projectName) : 'Chờ phân tích'}</strong></span>
            <span>Trạng thái <strong className={analysis ? 'success-text' : ''}>{analysis ? 'Phân tích thành công' : 'Sẵn sàng phân tích'}</strong></span>
          </div>
          {analysis && <span className="success-pill"><CheckIcon size={15} /> Đã đọc {analysis.workItemCount} công việc</span>}
        </section>
      )}

      {analysis && (
        <section className="metric-grid upload-metrics">
          <MetricCard icon={<FileIcon />} label="Sheet DM" value={analysis.dmSheetName.trim()} note="Đã nhận diện hợp lệ" tone="blue" />
          <MetricCard icon={<ListIcon />} label="Công việc" value={String(analysis.workItemCount)} note="Dòng dữ liệu đã đọc" tone="green" />
          <MetricCard icon={<TemplateIcon />} label="Biểu mẫu" value={String(analysis.outputSheetCount)} note="Sheet đầu ra phát hiện" tone="gold" />
          <MetricCard icon={<BuildingIcon />} label="Dự án" value={textOrDash(analysis.project.projectName)} note="Đọc từ sheet DM" tone="purple" />
        </section>
      )}

      {!status?.pdfAvailable && analysis && (
        <div className="inline-warning">
          Excel vẫn có thể xuất bình thường. Preview PDF chưa hoạt động: {status?.message ?? 'chưa kết nối được backend PDF.'}
        </div>
      )}

      {file && (
        <section className="analysis-action-strip">
          <div className="analysis-action-copy">
            <span className="action-brand-mark"><img src="/hacom-logo-vertical.png" alt="" /></span>
            <div>
              <strong>{analysis ? 'Dữ liệu đã sẵn sàng' : 'Sẵn sàng để phân tích file'}</strong>
              <p>{analysis ? 'Tiếp tục để kiểm tra và chọn công việc cần tạo hồ sơ.' : 'Hệ thống sẽ đọc sheet DM, thông tin dự án và các sheet đầu ra liên quan.'}</p>
            </div>
          </div>
          {analysis ? (
            <div className="analysis-action-buttons">
              <button className="secondary-button" type="button" onClick={() => onAnalyze(file)} disabled={busy}><RefreshIcon size={18} /> Phân tích lại</button>
              <button className="primary-button action-primary" type="button" onClick={onContinue}><ListIcon size={19} /> Xem danh mục công việc</button>
            </div>
          ) : (
            <button className="primary-button action-primary" type="button" onClick={() => onAnalyze(file)} disabled={busy}>
              <SearchIcon size={19} /> {busy ? 'Đang phân tích…' : 'Phân tích file'}
            </button>
          )}
        </section>
      )}
    </div>
  )
}

function GuideStep({ number, icon, title, text }: { number: number; icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="guide-step">
      <span className="guide-number">{number}</span>
      <span className="guide-icon">{icon}</span>
      <div><strong>{title}</strong><p>{text}</p></div>
    </div>
  )
}

function MetricCard({
  icon,
  label,
  value,
  note,
  tone,
}: {
  icon: React.ReactNode
  label: string
  value: string
  note: string
  tone: 'blue' | 'green' | 'gold' | 'purple'
}) {
  return (
    <article className="metric-card">
      <span className={`metric-icon ${tone}`}>{icon}</span>
      <div>
        <span className="metric-label">{label}</span>
        <strong title={value}>{value}</strong>
        <small>{note}</small>
      </div>
    </article>
  )
}
