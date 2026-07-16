import type { OutputSheet, SystemStatus, WorkItem } from '../types'
import {
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ExcelIcon,
  FileIcon,
  InfoIcon,
  PdfIcon,
  TemplateIcon,
} from '../icons'
import { textOrDash } from '../utils'

export function TemplatesPage({
  item,
  outputs,
  selectedSheets,
  onToggle,
  onSelectAll,
  onBack,
  onGenerate,
  status,
  busy,
}: {
  item: WorkItem
  outputs: OutputSheet[]
  selectedSheets: string[]
  onToggle: (sheet: string) => void
  onSelectAll: () => void
  onBack: () => void
  onGenerate: () => void
  status: SystemStatus | null
  busy: boolean
}) {
  return (
    <div className="page-stack">
      <section className="page-heading">
        <h1>Chọn biểu mẫu</h1>
        <p>Xác định các sheet đầu ra liên quan đến danh mục đã chọn.</p>
      </section>

      <section className="surface selected-item-surface">
        <div className="selected-item-title"><FileIcon size={20} /> Thông tin danh mục đã chọn</div>
        <div className="selected-item-grid">
          <Field label="Số danh mục" value={String(item.number)} />
          <Field label="Nội dung" value={textOrDash(item.content)} />
          <Field label="Vị trí" value={textOrDash(item.position)} />
          <Field label="Số biên bản" value={textOrDash(item.recordNumber)} />
          <Field label="Ngày lấy mẫu" value={textOrDash(item.sampleDate)} />
        </div>
      </section>

      <div className="templates-layout">
        <section className="surface templates-list-surface">
          <div className="section-toolbar simple-toolbar">
            <div>
              <h2>Danh sách biểu mẫu liên quan</h2>
              <p>Danh sách được nhận diện từ tên sheet trong file BBNT.</p>
            </div>
            <button className="secondary-button compact" type="button" onClick={onSelectAll}>
              Chọn tất cả
            </button>
          </div>

          <div className="template-list">
            {outputs.map((output) => {
              const checked = selectedSheets.includes(output.sheetName)
              return (
                <button
                  type="button"
                  key={output.sheetName}
                  className={`template-row ${checked ? 'checked' : ''}`}
                  onClick={() => output.available && onToggle(output.sheetName)}
                  disabled={!output.available}
                >
                  <span className={`template-checkbox ${checked ? 'checked' : ''}`}>{checked ? <CheckIcon size={17} /> : null}</span>
                  <span className="template-file-icon"><ExcelIcon size={23} /></span>
                  <span className="template-main">
                    <strong>{output.displayName}</strong>
                    <small>{output.sheetName}</small>
                  </span>
                  <span className="template-description">{output.description}</span>
                  <span className={`availability-pill ${output.available ? 'ready' : 'missing'}`}>
                    {output.available ? <><CheckIcon size={14} /> Sẵn sàng</> : 'Không khả dụng'}
                  </span>
                </button>
              )
            })}
          </div>

          <div className="templates-note"><InfoIcon size={17} /> Chỉ các sheet được nhận diện là “Sẵn sàng” mới có thể xuất.</div>
        </section>

        <aside className="surface selection-summary">
          <h2>Tóm tắt lựa chọn</h2>
          <div className="selected-count">
            <span className="selected-count-icon"><TemplateIcon size={27} /></span>
            <div><span>Đã chọn</span><strong>{selectedSheets.length} biểu mẫu</strong></div>
          </div>
          <div className="summary-divider" />
          <h3>Định dạng xuất</h3>
          <div className="format-row"><span className="format-icon excel"><ExcelIcon /></span><div><strong>Excel</strong><small>File chỉnh sửa và lưu trữ</small></div></div>
          <div className={`format-row ${status?.pdfAvailable ? '' : 'muted-format'}`}><span className="format-icon pdf"><PdfIcon /></span><div><strong>PDF</strong><small>{status?.pdfAvailable ? `Preview bằng ${status.activePdfEngine}` : 'Chưa cài bộ chuyển PDF'}</small></div></div>
          {!status?.pdfAvailable && <p className="pdf-hint">Bạn vẫn tạo và tải Excel được. Cài Gotenberg hoặc LibreOffice để có preview PDF.</p>}
          <button className="primary-button generate-button" type="button" onClick={onGenerate} disabled={selectedSheets.length === 0 || busy}>
            <TemplateIcon size={18} /> {busy ? 'Đang tạo hồ sơ…' : 'Tạo Excel & Preview PDF'} <ChevronRightIcon size={18} />
          </button>
          <button className="secondary-button back-button" type="button" onClick={onBack} disabled={busy}>
            <ChevronLeftIcon size={18} /> Quay lại
          </button>
        </aside>
      </div>
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return <div className="selected-field"><span>{label}</span><strong>{value}</strong></div>
}
