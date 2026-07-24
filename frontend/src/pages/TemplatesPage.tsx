import type { DocumentType, FieldDecision, MaterialFamily, OutputSheet, SystemStatus, WorkItem } from '../types'
import {
  CheckIcon, ChevronLeftIcon, ChevronRightIcon, ExcelIcon, FileIcon, InfoIcon,
  LightbulbIcon, PdfIcon, TemplateIcon,
} from '../icons'
import { textOrDash } from '../utils'
import {
  countSelectedSheets, familyLabel, fieldLabel,
  hasIncompleteSelection, requiresManualFamily, sheetStatusLabel,
} from '../uiRules'

export function TemplatesPage({
  items, outputsByItem, selectedSheetsByItem, familyByItem, fieldOverridesByItem, onChangeFamily, onChangeTemplate,
  onChangeFieldOverride, onToggle, onSelectAll, onBack, onGenerate, status, busy,
}: {
  items: WorkItem[]
  outputsByItem: Record<string, OutputSheet[]>
  selectedSheetsByItem: Record<string, string[]>
  familyByItem: Record<string, MaterialFamily>
  fieldOverridesByItem: Record<string, Record<string, string>>
  onChangeFamily: (itemNumber: string, family: MaterialFamily) => void
  onChangeTemplate: (itemNumber: string, documentType: 'LM' | 'GM', sourceTemplate: string) => void
  onChangeFieldOverride: (itemNumber: string, fieldName: string, value: string) => void
  onToggle: (itemNumber: string, sheet: string) => void
  onSelectAll: (itemNumber: string) => void
  onBack: () => void
  onGenerate: () => void
  status: SystemStatus | null
  busy: boolean
}) {
  const selectedCount = countSelectedSheets(selectedSheetsByItem)
  const incomplete = hasIncompleteSelection(items, selectedSheetsByItem)
  const manualFamilyRequired = items.some((item) => {
    const selectedFamily = familyByItem[item.itemNumber] ?? item.materialFamily
    const selected = new Set(selectedSheetsByItem[item.itemNumber] ?? [])
    return selectedFamily === 'UNKNOWN'
      && (outputsByItem[item.itemNumber] ?? []).some((output) => selected.has(output.sheetName) && output.generated && output.documentType !== 'MAIN')
  })
  const fatalMissingTemplate = items.some((item) => {
    const selected = new Set(selectedSheetsByItem[item.itemNumber] ?? [])
    return (outputsByItem[item.itemNumber] ?? []).some((output) =>
      selected.has(output.sheetName) && output.availability === 'MISSING_TEMPLATE')
  })

  return (
    <div className="page-stack templates-page">
      <section className="page-heading page-heading-with-back">
        <button className="back-icon-button" type="button" onClick={onBack} disabled={busy} aria-label="Quay lại danh mục công việc"><ChevronLeftIcon size={23} /></button>
        <div><h1>Chọn biểu mẫu & kiểm tra dữ liệu</h1><p>MAIN, LM và GM có kế hoạch riêng; mỗi output có thể chọn độc lập.</p></div>
      </section>

      <div className="templates-layout multi-template-layout">
        <section className="templates-content multi-template-content">
          {items.map((item) => {
            const outputs = outputsByItem[item.itemNumber] ?? []
            const selectedSheets = selectedSheetsByItem[item.itemNumber] ?? []
            const family = familyByItem[item.itemNumber] ?? item.materialFamily
            const availableOutputs = outputs.filter((output) => output.available)
            const allSelected = availableOutputs.length > 0 && availableOutputs.length === selectedSheets.length
            const selectedOutputs = outputs.filter((output) => selectedSheets.includes(output.sheetName))
            const decisions = selectedOutputs.flatMap((output) => output.fieldDecisions)
            const warnings = unique([...item.warnings, ...outputs.flatMap((output) => output.warnings)])
            const familyLocked = item.hasLmSheet || item.hasGmSheet
            return (
              <section className="surface work-template-block" key={item.itemNumber} data-testid={`template-item-${item.itemNumber}`}>
                <div className="work-template-heading">
                  <div>
                    <div className="work-template-title"><FileIcon size={19} /><strong>DM {item.itemNumber}</strong><span className={`generation-badge ${item.hasCompleteSamplePair ? 'existing' : 'clone'}`}>{sheetStatusLabel(item.sheetStatus)}</span></div>
                    <p>{textOrDash(item.content)} · {textOrDash(item.position)}</p>
                  </div>
                  {!item.hasCompleteSamplePair && !familyLocked && (
                    <label className="family-selector"><span>Loại mẫu</span><select aria-label={`Loại mẫu DM ${item.itemNumber}`} value={family} onChange={(event) => onChangeFamily(item.itemNumber, event.target.value as MaterialFamily)} disabled={busy}>
                      <option value="UNKNOWN">Chưa xác định</option><option value="VUA">Vữa — LMV/GMV</option><option value="BETONG">Bê tông — LMBT/GMBT</option>
                    </select></label>
                  )}
                  {!item.hasCompleteSamplePair && familyLocked && (
                    <div className="family-selector family-selector-locked" aria-label={`Loại mẫu khóa DM ${item.itemNumber}`}>
                      <span>Loại mẫu</span><strong>{familyLabel(family)}</strong><small>Khóa theo LM/GM hiện có</small>
                    </div>
                  )}
                </div>

                <div className="work-template-meta"><Field label="Số biên bản" value={textOrDash(item.recordNumber)} /><Field label="Ngày lấy mẫu" value={textOrDash(item.sampleDate)} /><Field label="Nhận diện" value={familyLabel(family)} /><Field label="Lý do" value={textOrDash(item.detectionReason)} /></div>

                {requiresManualFamily(item, family) && (
                  <div className="inline-warning"><InfoIcon size={17} /> Không nhận diện chắc chắn vật liệu. MAIN hiện có vẫn có thể chọn và xuất; hãy chọn Vữa hoặc Bê tông nếu cần tạo LM/GM.</div>
                )}
                <div className="template-block-toolbar"><div><strong>Biểu mẫu đầu ra</strong><small>Có thể chọn chỉ MAIN, chỉ LM, chỉ GM hoặc mọi tổ hợp hợp lệ.</small></div><button className="secondary-button compact" type="button" onClick={() => onSelectAll(item.itemNumber)} disabled={availableOutputs.length === 0}>{allSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}</button></div>
                <div className="template-list compact-template-list">
                  {outputs.map((output) => {
                    const checked = selectedSheets.includes(output.sheetName)
                    return (
                      <div className={`template-card-wrapper ${checked ? 'checked' : ''}`} key={`${output.documentType}-${output.sheetName}`}>
                        <button type="button" className={`template-card ${checked ? 'checked' : ''}`} onClick={() => output.available && onToggle(item.itemNumber, output.sheetName)} disabled={!output.available} aria-label={`${checked ? 'Bỏ chọn' : 'Chọn'} ${output.displayName}`}>
                          <span className={`template-checkbox ${checked ? 'checked' : ''}`}>{checked ? <CheckIcon size={16} /> : null}</span>
                          <span className={`template-file-icon ${output.documentType === 'LM' ? 'blue' : output.documentType === 'GM' ? 'gold' : 'red'}`}><ExcelIcon size={25} /></span>
                          <span className="template-main"><strong>{output.displayName}</strong><small>{output.description}</small><span className="template-code"><FileIcon size={13} /> Sheet: {output.sheetName}</span>{output.sourceTemplate && <span className="template-source">Nguồn layout: {output.sourceTemplate}</span>}</span>
                          <span className={`template-badge ${output.generated ? 'recommended' : output.available ? 'required' : 'missing'}`}>{output.generated ? 'Tạo mới an toàn' : output.available ? 'Có sẵn' : 'Thiếu template'}</span>
                        </button>
                        {output.generated && output.documentType !== 'MAIN' && (
                          <label className="source-template-select"><span>Biểu mẫu phụ</span><select aria-label={`Biểu mẫu phụ ${output.documentType} DM ${item.itemNumber}`} value={checked ? familyCodeOf(output.sourceTemplate) : NO_AUX_FORM} onChange={(event) => {
                            const nextCode = event.target.value
                            if (nextCode === SWITCH_TO_VUA || nextCode === SWITCH_TO_BETONG) {
                              onChangeFamily(item.itemNumber, nextCode === SWITCH_TO_VUA ? 'VUA' : 'BETONG')
                              return
                            }
                            if (nextCode === NO_AUX_FORM) { if (checked) onToggle(item.itemNumber, output.sheetName); return }
                            if (!checked) onToggle(item.itemNumber, output.sheetName)
                            const nextTemplate = pickTemplateForCode(output.availableSourceTemplates, nextCode, output.sourceTemplate)
                            if (nextTemplate && nextTemplate !== output.sourceTemplate) onChangeTemplate(item.itemNumber, output.documentType as 'LM' | 'GM', nextTemplate)
                          }} disabled={busy}>
                            {templateCodeOptions(output.availableSourceTemplates).map((code) => <option key={code} value={code}>{code}</option>)}
                            {!familyLocked && family === 'BETONG' && <option value={SWITCH_TO_VUA}>→ Đổi sang Vữa (LMV/GMV)</option>}
                            {!familyLocked && family === 'VUA' && <option value={SWITCH_TO_BETONG}>→ Đổi sang Bê tông (LMBT/GMBT)</option>}
                            <option value={NO_AUX_FORM}>Không sinh biểu mẫu phụ</option>
                          </select></label>
                        )}
                      </div>
                    )
                  })}
                  {outputs.length === 0 && <div className="inline-warning"><InfoIcon size={17} /> Chưa có output LM/GM phù hợp. MAIN vẫn có thể xuất nếu workbook đã có; nếu không, hãy chọn loại vật liệu hoặc kiểm tra template registry.</div>}
                </div>

                {decisions.length > 0 && <DecisionTable decisions={decisions} itemNumber={item.itemNumber} overrides={fieldOverridesByItem[item.itemNumber] ?? {}} onChangeFieldOverride={onChangeFieldOverride} busy={busy} />}
                {warnings.length > 0 && <div className="item-warning-list">{warnings.map((warning) => <p key={warning}><InfoIcon size={14} /> {warning}</p>)}</div>}
              </section>
            )
          })}
          <div className="templates-note"><InfoIcon size={17} /> Cảnh báo dữ liệu không chặn xuất; thiếu family hoặc template cần thiết mới chặn.</div>
        </section>

        <aside className="selection-column sticky-selection-column">
          <section className="surface selection-summary">
            <div className="summary-title"><TemplateIcon size={20} /><h2>Tóm tắt lựa chọn</h2></div>
            <strong className="summary-selected-count">{items.length} dòng DM · {selectedCount} sheet</strong>
            <div className="selected-template-list">{items.map((item, index) => {
              const outputs = outputsByItem[item.itemNumber] ?? []
              const selected = new Set(selectedSheetsByItem[item.itemNumber] ?? [])
              const labels = outputs.filter((output) => selected.has(output.sheetName)).map((output) => `${output.documentType} ${output.generated ? 'clone' : 'có sẵn'}`)
              return <div className="selected-template-item" key={item.itemNumber}><span>{index + 1}</span><div><strong>DM {item.itemNumber}</strong><small>{labels.join(' · ') || 'Chưa chọn output'}</small></div></div>
            })}</div>
            <div className="summary-divider" /><div className="summary-info-row"><span>Định dạng</span><strong>{status?.pdfAvailable ? 'Excel + PDF' : 'Excel'}</strong></div><div className="summary-info-row"><span>Dữ liệu clone</span><strong>Chỉ CERTAIN</strong></div>
            <div className="selection-tip"><LightbulbIcon size={20} /><p>FieldDecision điều khiển trực tiếp việc điền/xóa ô; mác, kích thước, tuổi mẫu, LAS và ngày giao không chắc chắn đều blank thực sự.</p></div>
            {!status?.pdfAvailable && <p className="pdf-hint">PDF chưa sẵn sàng: {status?.message ?? 'chưa kết nối bộ chuyển đổi PDF'}.</p>}
          </section>
          <button className="secondary-button summary-back-button" type="button" onClick={onBack} disabled={busy}><ChevronLeftIcon size={18} /> Quay lại</button>
          <button className="primary-button generate-button" type="button" onClick={onGenerate} disabled={selectedCount === 0 || incomplete || manualFamilyRequired || fatalMissingTemplate || busy}><PdfIcon size={18} /> {busy ? 'Đang tạo hồ sơ…' : 'Tạo file xem trước'} <ChevronRightIcon size={18} /></button>
          {incomplete && <small className="generate-hint warning-text">Mỗi dòng DM phải có ít nhất một output được chọn.</small>}
          {manualFamilyRequired && <small className="generate-hint warning-text">Output LM/GM được chọn nhưng loại vật liệu chưa xác định.</small>}
          {fatalMissingTemplate && <small className="generate-hint warning-text">Không tìm thấy template tương thích cho output cần tạo.</small>}
        </aside>
      </div>
    </div>
  )
}

function DecisionTable({ decisions, itemNumber, overrides, onChangeFieldOverride, busy }: {
  decisions: FieldDecision[]
  itemNumber: string
  overrides: Record<string, string>
  onChangeFieldOverride: (itemNumber: string, fieldName: string, value: string) => void
  busy: boolean
}) {
  const autoFilledCount = countCertainFields(decisions)
  const uncertainFields = groupUncertainFields(decisions)
  return <div className="field-decision-compact">
    <div className="field-decision-compact-head">
      <h3>Chi tiết trường dữ liệu</h3>
      {autoFilledCount > 0 && <span className="decision-autofill-note"><CheckIcon size={12} /> {autoFilledCount} trường tự động điền chắc chắn</span>}
    </div>
    {uncertainFields.length > 0 ? (
      <div className="field-decision-grid">
        {uncertainFields.map((field) => (
          <label className="field-decision-input-row" key={field.fieldName} title={field.reason}>
            <span className="field-decision-input-label">{fieldLabel(field.fieldName)}<small>{field.documentTypes.join(' · ')}</small></span>
            <input
              type="text"
              placeholder="Để trống"
              value={overrides[field.fieldName] ?? ''}
              onChange={(event) => onChangeFieldOverride(itemNumber, field.fieldName, event.target.value)}
              disabled={busy}
            />
          </label>
        ))}
      </div>
    ) : <p className="decision-autofill-note all-certain"><CheckIcon size={13} /> Mọi trường đều đã xác định chắc chắn, không cần người dùng điền thêm.</p>}
  </div>
}
function documentTypeLabel(type: DocumentType) { return type === 'MAIN' ? 'MAIN' : type === 'LM' ? 'LM' : type === 'GM' ? 'GM' : '—' }
function unique(values: string[]) { return Array.from(new Set(values.filter(Boolean))) }
/** Đếm số trường (theo fieldName, không lặp giữa LM/GM) đã được điền tự động chắc chắn. */
function countCertainFields(decisions: FieldDecision[]) {
  const seen = new Set<string>()
  decisions.forEach((decision) => { if (decision.certainty === 'CERTAIN') seen.add(decision.fieldName) })
  return seen.size
}
/**
 * Gom các trường chưa chắc chắn (UNCERTAIN/UNKNOWN) theo fieldName — cùng 1 trường như "grade" có thể
 * xuất hiện cả ở LM và GM, nhưng người dùng chỉ cần nhập 1 lần, giá trị sẽ được áp cho mọi biểu mẫu liên quan.
 */
function groupUncertainFields(decisions: FieldDecision[]): Array<{ fieldName: string; reason: string; documentTypes: string[] }> {
  const map = new Map<string, { fieldName: string; reason: string; documentTypes: string[] }>()
  decisions.filter((decision) => decision.certainty !== 'CERTAIN').forEach((decision) => {
    const docLabel = documentTypeLabel(decision.documentType)
    const existing = map.get(decision.fieldName)
    if (existing) { if (!existing.documentTypes.includes(docLabel)) existing.documentTypes.push(docLabel) }
    else map.set(decision.fieldName, { fieldName: decision.fieldName, reason: decision.reason, documentTypes: [docLabel] })
  })
  return Array.from(map.values())
}
function Field({ label, value }: { label: string; value: string }) { return <div className="selected-field"><span>{label}</span><strong title={value}>{value}</strong></div> }

const NO_AUX_FORM = '__NONE__'
const SWITCH_TO_VUA = '__SWITCH_VUA__'
const SWITCH_TO_BETONG = '__SWITCH_BETONG__'
/** Trích mã họ biểu mẫu (VD: "1.LMBT (141)" -> "LMBT") để không bắt người dùng chọn từng bản instance cụ thể. */
function familyCodeOf(templateName: string | null) {
  if (!templateName) return NO_AUX_FORM
  const match = templateName.match(/\.([A-Za-z]+)/)
  return match ? match[1].toUpperCase() : templateName
}
function templateCodeOptions(templates: string[]) {
  return unique(templates.map((template) => familyCodeOf(template)))
}
/** Chọn 1 template cụ thể đại diện cho mã họ được chọn: ưu tiên giữ nguyên nếu đang dùng thuộc đúng họ, ngược lại lấy bản đầu tiên. */
function pickTemplateForCode(templates: string[], code: string, current: string | null) {
  if (current && familyCodeOf(current) === code) return current
  return templates.find((template) => familyCodeOf(template) === code) ?? null
}
