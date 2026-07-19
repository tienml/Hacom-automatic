import { useMemo, useState, type FormEvent } from 'react'
import type { AnalyzeResponse, MaterialFamily, WorkItem, WorkItemSheetStatus } from '../types'
import {
  CalendarIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, FileIcon, FilterIcon,
  InfoIcon, ListIcon, SearchIcon, TemplateIcon, XIcon,
} from '../icons'
import { textOrDash } from '../utils'
import { canSelectWorkItem, familyLabel, sheetStatusLabel } from '../uiRules'

const PAGE_SIZE_OPTIONS = [10, 20, 50]
type SampleFilter = 'all' | 'with' | 'without'
type StatusFilter = 'all' | WorkItemSheetStatus
type FamilyFilter = 'all' | MaterialFamily

type Filters = { query: string; position: string; sample: SampleFilter; status: StatusFilter; family: FamilyFilter }
const DEFAULT_FILTERS: Filters = { query: '', position: 'all', sample: 'all', status: 'all', family: 'all' }

export function WorkItemsPage({ analysis, selectedItems, onToggle, onContinue, busy }: {
  analysis: AnalyzeResponse
  selectedItems: WorkItem[]
  onToggle: (item: WorkItem) => void
  onContinue: () => void
  busy: boolean
}) {
  const [draftFilters, setDraftFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const positions = useMemo(() => {
    const values = new Map<string, string>()
    analysis.workItems.forEach((item) => {
      const display = compactText(item.position)
      if (display && !values.has(normalizeText(display))) values.set(normalizeText(display), display)
    })
    return Array.from(values.values()).sort((a, b) => a.localeCompare(b, 'vi'))
  }, [analysis.workItems])

  const filtered = useMemo(() => {
    const needle = normalizeText(appliedFilters.query)
    const selectedPosition = normalizeText(appliedFilters.position)
    return analysis.workItems.filter((item) => {
      const searchable = normalizeText([item.itemNumber, item.localOrder, item.content, item.position,
        item.recordNumber, item.inspectionTime, item.sampleDate].join(' '))
      const hasSample = Boolean(compactText(item.sampleDate ?? ''))
      return (!needle || searchable.includes(needle))
        && (appliedFilters.position === 'all' || normalizeText(item.position) === selectedPosition)
        && (appliedFilters.sample === 'all' || (appliedFilters.sample === 'with' ? hasSample : !hasSample))
        && (appliedFilters.status === 'all' || item.sheetStatus === appliedFilters.status)
        && (appliedFilters.family === 'all' || item.materialFamily === appliedFilters.family)
    })
  }, [analysis.workItems, appliedFilters])

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage = Math.min(page, pageCount)
  const visibleItems = filtered.slice((safePage - 1) * pageSize, safePage * pageSize)
  const selectedNumbers = new Set(selectedItems.map((item) => item.itemNumber))
  const activeFilterCount = countActiveFilters(appliedFilters)
  const paginationItems = paginationRange(safePage, pageCount)

  function updateDraft<K extends keyof Filters>(key: K, value: Filters[K]) { setDraftFilters((current) => ({ ...current, [key]: value })) }
  function applyFilters(event?: FormEvent) { event?.preventDefault(); setAppliedFilters({ ...draftFilters, query: draftFilters.query.trim() }); setPage(1) }
  function clearFilters() { setDraftFilters(DEFAULT_FILTERS); setAppliedFilters(DEFAULT_FILTERS); setPage(1) }

  return (
    <div className="page-stack work-items-page">
      <section className="page-heading page-heading-with-icon">
        <span className="heading-icon gold"><ListIcon size={25} /></span>
        <div><h1>Danh mục công việc</h1><p>Trạng thái MAIN, LM và GM được phân tích độc lập; mọi dòng đều có thể chọn.</p></div>
      </section>

      <section className="metric-grid work-metrics">
        <SummaryCard icon={<FileIcon size={23} />} label="Tổng công việc" value={analysis.workItemCount} tone="blue" />
        <SummaryCard icon={<CheckIcon size={23} />} label="Đủ LM/GM" value={analysis.completeSamplePairCount} tone="green" />
        <SummaryCard icon={<TemplateIcon size={23} />} label="Chỉ có sheet chính" value={analysis.mainOnlyCount} tone="gold" />
        <SummaryCard icon={<CalendarIcon size={23} />} label="Chưa xác định vật liệu" value={analysis.unknownMaterialCount} tone="purple" />
      </section>

      {analysis.analysisWarnings.length > 0 && <section className="inline-warning analysis-warning-list"><InfoIcon size={18} /><div><strong>Cảnh báo phân tích workbook</strong>{analysis.analysisWarnings.map((warning) => <p key={warning}>{warning}</p>)}</div></section>}

      <form className="surface filters-surface safe-template-filters" onSubmit={applyFilters}>
        <label className="search-field"><SearchIcon size={20} /><input value={draftFilters.query} onChange={(event) => updateDraft('query', event.target.value)} placeholder="Tìm số DM, nội dung, vị trí hoặc số biên bản..." /></label>
        <FilterSelect label="Vị trí" value={draftFilters.position} onChange={(value) => updateDraft('position', value)}><option value="all">Tất cả vị trí</option>{positions.map((value) => <option key={value} value={value}>{value}</option>)}</FilterSelect>
        <FilterSelect label="Trạng thái sheet" value={draftFilters.status} onChange={(value) => updateDraft('status', value as StatusFilter)}>
          <option value="all">Tất cả</option><option value="NO_SHEETS">Không có sheet</option><option value="MAIN_ONLY">Chỉ có sheet chính</option><option value="MISSING_LM">Thiếu LM</option><option value="MISSING_GM">Thiếu GM</option><option value="MISSING_LM_GM">Thiếu LM/GM</option><option value="COMPLETE_SAMPLE_PAIR">Đã có đủ LM/GM</option><option value="UNKNOWN_MATERIAL">Chưa xác định vật liệu</option>
        </FilterSelect>
        <FilterSelect label="Vật liệu" value={draftFilters.family} onChange={(value) => updateDraft('family', value as FamilyFilter)}><option value="all">Tất cả</option><option value="VUA">Vữa</option><option value="BETONG">Bê tông</option><option value="UNKNOWN">Chưa xác định</option></FilterSelect>
        <FilterSelect label="Lấy mẫu" value={draftFilters.sample} onChange={(value) => updateDraft('sample', value as SampleFilter)}><option value="all">Tất cả</option><option value="with">Có ngày</option><option value="without">Chưa có ngày</option></FilterSelect>
        <button className="secondary-button filter-apply-button" type="submit"><FilterIcon size={18} /> Áp dụng{activeFilterCount ? ` (${activeFilterCount})` : ''}</button>
        <button className="filter-clear-button" type="button" onClick={clearFilters}><XIcon size={17} /> Xóa lọc</button>
      </form>

      <section className="surface table-surface">
        <div className="table-title-row"><div><h2>Danh sách công việc</h2><p>{filtered.length} kết quả; item MAIN-only vẫn tạo được LM/GM.</p></div>{selectedItems.length > 0 && <span className="selected-item-chip"><CheckIcon size={16} /> Đã chọn {selectedItems.length} dòng</span>}</div>
        <div className="data-table-wrap"><table className="data-table safe-template-table">
          <thead><tr><th className="select-col">Chọn</th><th className="number-col">Số DM</th><th>Nội dung công việc</th><th>Vị trí</th><th>Thời gian</th><th>Số biên bản</th><th>Loại vật liệu</th><th>Trạng thái</th></tr></thead>
          <tbody>
            {visibleItems.map((item) => {
              const selected = selectedNumbers.has(item.itemNumber)
              const selectable = canSelectWorkItem(item)
              return <tr key={`${item.itemNumber}-${item.excelRow}`} className={`${selected ? 'selected-row' : ''} ${!item.hasCompleteSamplePair ? 'output-missing' : ''}`.trim()} onClick={() => selectable && onToggle(item)} aria-selected={selected}>
                <td className="select-col"><button aria-label={`Chọn DM ${item.itemNumber}`} type="button" className={`selection-box ${selected ? 'selected' : ''}`} onClick={(event) => { event.stopPropagation(); if (selectable) onToggle(item) }} disabled={!selectable}>{selected ? <CheckIcon size={15} /> : null}</button></td>
                <td><strong>DM {item.itemNumber}</strong></td><td className="long-content">{textOrDash(item.content)}</td><td>{textOrDash(item.position)}</td><td>{textOrDash(item.inspectionTime)}</td><td>{textOrDash(item.recordNumber)}</td>
                <td><span className={`material-pill ${item.materialFamily.toLowerCase()}`}>{familyLabel(item.materialFamily)}</span></td>
                <td><span className={`availability-pill status-${item.sheetStatus.toLowerCase()}`}>{statusIcon(item)}{sheetStatusLabel(item.sheetStatus)}</span><small className="mode-caption">{documentSummary(item)}</small></td>
              </tr>
            })}
            {visibleItems.length === 0 && <tr><td colSpan={8} className="empty-table"><SearchIcon size={30} /><strong>Không có kết quả phù hợp</strong><span>Hãy thay đổi bộ lọc.</span></td></tr>}
          </tbody>
        </table></div>
        <div className="table-footer"><label className="page-size-control">Hiển thị<select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1) }}>{PAGE_SIZE_OPTIONS.map((value) => <option key={value}>{value}</option>)}</select>dòng</label><span className="pagination-status">Trang {safePage}/{pageCount}</span><div className="pagination"><button type="button" disabled={safePage === 1} onClick={() => setPage((value) => Math.max(1, value - 1))}><ChevronLeftIcon size={16} /></button>{paginationItems.map((value, index) => typeof value === 'number' ? <button key={value} type="button" className={value === safePage ? 'current' : ''} onClick={() => setPage(value)}>{value}</button> : <span className="pagination-ellipsis" key={`ellipsis-${index}`}>…</span>)}<button type="button" disabled={safePage === pageCount} onClick={() => setPage((value) => Math.min(pageCount, value + 1))}><ChevronRightIcon size={16} /></button></div></div>
      </section>

      <section className="work-action-bar"><div className="work-note"><InfoIcon size={20} /><div><strong>Nguyên tắc an toàn dữ liệu</strong><p>MAIN hiện có không bị sanitize; chỉ LM/GM clone mới được làm sạch và điền trường CERTAIN.</p></div></div><div className="work-selection-action"><span>Đã chọn <strong>{selectedItems.length}</strong> dòng</span><button className="primary-button continue-button" type="button" onClick={onContinue} disabled={selectedItems.length === 0 || busy}>Chọn biểu mẫu <ChevronRightIcon size={18} /></button></div></section>
    </div>
  )
}

function statusIcon(item: WorkItem) { return item.hasCompleteSamplePair ? <CheckIcon size={13} /> : <TemplateIcon size={13} /> }
function documentSummary(item: WorkItem) {
  const parts = [item.hasMainSheet ? 'MAIN có sẵn' : null, item.hasLmSheet ? 'LM có sẵn' : item.lmPlan.availability === 'GENERATABLE' ? 'LM sẽ tạo' : null, item.hasGmSheet ? 'GM có sẵn' : item.gmPlan.availability === 'GENERATABLE' ? 'GM sẽ tạo' : null].filter(Boolean)
  return parts.join(' · ') || 'Cần chọn loại/template'
}
function FilterSelect({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: React.ReactNode }) { return <label className="filter-field"><span>{label}</span><select value={value} onChange={(event) => onChange(event.target.value)}>{children}</select></label> }
function SummaryCard({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: number; tone: 'blue' | 'green' | 'gold' | 'purple' }) { return <article className="summary-card"><span className={`summary-icon ${tone}`}>{icon}</span><div><strong>{value}</strong><span>{label}</span></div></article> }
function compactText(value: string) { return (value ?? '').replace(/\s+/g, ' ').trim() }
function normalizeText(value: unknown) { return String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/gi, 'd').toLowerCase().replace(/\s+/g, ' ').trim() }
function countActiveFilters(filters: Filters) { return Number(Boolean(filters.query)) + Number(filters.position !== 'all') + Number(filters.sample !== 'all') + Number(filters.status !== 'all') + Number(filters.family !== 'all') }
function paginationRange(current: number, total: number): Array<number | 'ellipsis'> { if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1); const values: Array<number | 'ellipsis'> = [1]; if (current > 4) values.push('ellipsis'); for (let value = Math.max(2, current - 1); value <= Math.min(total - 1, current + 1); value += 1) values.push(value); if (current < total - 3) values.push('ellipsis'); values.push(total); return values }
