import { useMemo } from 'react'
import type { OutputSheet, SystemStatus, WorkItem } from '../types'
import {
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ExcelIcon,
  FileIcon,
  InfoIcon,
  LightbulbIcon,
  PdfIcon,
  TemplateIcon,
} from '../icons'
import { textOrDash } from '../utils'

type OutputGroup = {
  key: string
  title: string
  tone: 'red' | 'blue' | 'gold' | 'purple'
  items: OutputSheet[]
}

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
  const groups = useMemo(() => groupOutputs(outputs, item.number), [outputs, item.number])
  const selectedOutputs = outputs.filter((output) => selectedSheets.includes(output.sheetName))
  const allAvailableSelected = outputs.filter((output) => output.available).length === selectedSheets.length

  return (
    <div className="page-stack templates-page">
      <section className="page-heading page-heading-with-back">
        <button className="back-icon-button" type="button" onClick={onBack} disabled={busy} aria-label="Quay lại danh mục công việc">
          <ChevronLeftIcon size={23} />
        </button>
        <div>
          <h1>Chọn biểu mẫu</h1>
          <p>Chọn các sheet cần tạo từ dữ liệu của danh mục DM {item.number}.</p>
        </div>
      </section>

      <section className="surface selected-item-surface">
        <div className="selected-item-title"><FileIcon size={20} /> Tóm tắt công việc đã chọn</div>
        <div className="selected-item-grid">
          <Field label="Mã công việc" value={`DM ${item.number}`} />
          <Field label="Nội dung công việc" value={textOrDash(item.content)} wide />
          <Field label="Vị trí / Hạng mục" value={textOrDash(item.position)} />
          <Field label="Số biên bản" value={textOrDash(item.recordNumber)} />
          <Field label="Thời gian thực hiện" value={textOrDash(item.inspectionTime)} />
          <Field label="Ngày lấy mẫu" value={textOrDash(item.sampleDate)} />
        </div>
      </section>

      <div className="templates-layout">
        <section className="templates-content">
          <div className="templates-section-heading">
            <div>
              <h2>Chọn biểu mẫu cần tạo</h2>
              <p>Các biểu mẫu được nhận diện trực tiếp từ tên sheet trong file BBNT.</p>
            </div>
            <button className="secondary-button compact" type="button" onClick={onSelectAll}>
              {allAvailableSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}
            </button>
          </div>

          {groups.map((group) => (
            <section className="template-group" key={group.key}>
              <h3>{group.title}</h3>
              <div className="template-list">
                {group.items.map((output) => {
                  const checked = selectedSheets.includes(output.sheetName)
                  const isPrimary = output.sheetName.trim() === String(item.number)
                  return (
                    <button
                      type="button"
                      key={output.sheetName}
                      className={`template-card ${checked ? 'checked' : ''}`}
                      onClick={() => output.available && onToggle(output.sheetName)}
                      disabled={!output.available}
                    >
                      <span className={`template-checkbox ${checked ? 'checked' : ''}`}>{checked ? <CheckIcon size={16} /> : null}</span>
                      <span className={`template-file-icon ${group.tone}`}><ExcelIcon size={25} /></span>
                      <span className="template-main">
                        <strong>{output.displayName}</strong>
                        <small>{output.description || 'Biểu mẫu Excel trong workbook BBNT'}</small>
                        <span className="template-code"><FileIcon size={13} /> Sheet: {output.sheetName}</span>
                      </span>
                      <span className={`template-badge ${isPrimary ? 'required' : output.available ? 'recommended' : 'optional'}`}>
                        {isPrimary ? 'Biểu mẫu chính' : output.available ? 'Sẵn sàng' : 'Không khả dụng'}
                      </span>
                    </button>
                  )
                })}
              </div>
            </section>
          ))}

          <div className="templates-note"><InfoIcon size={17} /> Chỉ các sheet được đánh dấu sẵn sàng mới có thể đưa vào file kết quả.</div>
        </section>

        <aside className="selection-column">
          <section className="surface selection-summary">
            <div className="summary-title"><TemplateIcon size={20} /><h2>Tóm tắt lựa chọn</h2></div>
            <strong className="summary-selected-count">Đã chọn {selectedSheets.length} biểu mẫu</strong>

            <div className="selected-template-list">
              {selectedOutputs.length > 0 ? selectedOutputs.map((output, index) => (
                <div className="selected-template-item" key={output.sheetName}>
                  <span>{index + 1}</span>
                  <div><strong>{output.displayName}</strong><small>{output.sheetName}</small></div>
                </div>
              )) : <p className="empty-selection">Chưa chọn biểu mẫu nào.</p>}
            </div>

            <div className="summary-divider" />
            <h3>Thông tin bổ sung</h3>
            <div className="summary-info-row"><span>Tổng số sheet</span><strong>{selectedSheets.length}</strong></div>
            <div className="summary-info-row"><span>Định dạng xuất</span><strong>{status?.pdfAvailable ? 'Excel + PDF' : 'Excel'}</strong></div>
            <div className="summary-info-row"><span>Ngôn ngữ</span><strong>Tiếng Việt</strong></div>

            <div className="selection-tip"><LightbulbIcon size={20} /><p>Có thể thay đổi lựa chọn trước khi tạo file xem trước.</p></div>
            {!status?.pdfAvailable && <p className="pdf-hint">Preview PDF chưa sẵn sàng: {status?.message ?? 'chưa kết nối bộ chuyển đổi PDF'}.</p>}
          </section>

          <button className="secondary-button summary-back-button" type="button" onClick={onBack} disabled={busy}>
            <ChevronLeftIcon size={18} /> Quay lại
          </button>
          <button className="primary-button generate-button" type="button" onClick={onGenerate} disabled={selectedSheets.length === 0 || busy}>
            <PdfIcon size={18} /> {busy ? 'Đang tạo hồ sơ…' : 'Tạo file xem trước'} <ChevronRightIcon size={18} />
          </button>
          <small className="generate-hint">Hệ thống tạo Excel và PDF preview nếu dịch vụ PDF sẵn sàng.</small>
        </aside>
      </div>
    </div>
  )
}

function Field({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return <div className={`selected-field ${wide ? 'wide' : ''}`}><span>{label}</span><strong>{value}</strong></div>
}

function groupOutputs(outputs: OutputSheet[], workItemNumber: number): OutputGroup[] {
  const buckets: Record<string, OutputSheet[]> = {
    main: [],
    sample: [],
    delivery: [],
    other: [],
  }

  outputs.forEach((output) => {
    const normalized = `${output.sheetName} ${output.displayName} ${output.type}`.toLocaleLowerCase('vi-VN')
    if (output.sheetName.trim() === String(workItemNumber)) buckets.main.push(output)
    else if (normalized.includes('lmv') || normalized.includes('lấy mẫu') || normalized.includes('lay mau')) buckets.sample.push(output)
    else if (normalized.includes('gmv') || normalized.includes('giao mẫu') || normalized.includes('giao mau')) buckets.delivery.push(output)
    else buckets.other.push(output)
  })

  return [
    { key: 'main', title: 'Biểu mẫu chính', tone: 'red' as const, items: buckets.main },
    { key: 'sample', title: 'Phiếu lấy mẫu', tone: 'blue' as const, items: buckets.sample },
    { key: 'delivery', title: 'Phiếu giao mẫu', tone: 'gold' as const, items: buckets.delivery },
    { key: 'other', title: 'Biểu mẫu liên quan khác', tone: 'purple' as const, items: buckets.other },
  ].filter((group) => group.items.length > 0)
}
