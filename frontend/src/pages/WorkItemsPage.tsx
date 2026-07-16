import { useMemo, useState, type FormEvent } from 'react'
import type { AnalyzeResponse, WorkItem } from '../types'
import {
  CalendarIcon,
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  FileIcon,
  FilterIcon,
  InfoIcon,
  ListIcon,
  SearchIcon,
  TemplateIcon,
  XIcon,
} from '../icons'
import { textOrDash } from '../utils'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

type SampleFilter = 'all' | 'with' | 'without'
type OutputFilter = 'all' | 'ready' | 'missing'

type Filters = {
  query: string
  position: string
  sample: SampleFilter
  output: OutputFilter
}

const DEFAULT_FILTERS: Filters = {
  query: '',
  position: 'all',
  sample: 'all',
  output: 'all',
}

export function WorkItemsPage({
  analysis,
  selectedItem,
  onSelect,
  onContinue,
  busy,
}: {
  analysis: AnalyzeResponse
  selectedItem: WorkItem | null
  onSelect: (item: WorkItem) => void
  onContinue: () => void
  busy: boolean
}) {
  const [draftFilters, setDraftFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const positions = useMemo(() => {
    const byNormalized = new Map<string, string>()
    analysis.workItems.forEach((item) => {
      const display = compactText(item.position)
      if (!display) return
      const key = normalizeText(display)
      if (!byNormalized.has(key)) byNormalized.set(key, display)
    })
    return Array.from(byNormalized.values()).sort((a, b) => a.localeCompare(b, 'vi'))
  }, [analysis.workItems])

  const readyCount = useMemo(() => analysis.workItems.filter((item) => item.hasOutputSheets).length, [analysis.workItems])
  const missingCount = analysis.workItemCount - readyCount

  const filtered = useMemo(() => {
    const needle = normalizeText(appliedFilters.query)
    const selectedPosition = normalizeText(appliedFilters.position)

    return analysis.workItems.filter((item) => {
      const searchable = normalizeText([
        item.number,
        item.localOrder,
        item.content,
        item.position,
        item.recordNumber,
        item.inspectionTime,
        item.sampleDate,
      ].join(' '))
      const matchesQuery = !needle || searchable.includes(needle)
      const matchesPosition = appliedFilters.position === 'all' || normalizeText(item.position) === selectedPosition
      const hasSample = Boolean(compactText(item.sampleDate ?? ''))
      const matchesSample = appliedFilters.sample === 'all'
        || (appliedFilters.sample === 'with' ? hasSample : !hasSample)
      const matchesOutput = appliedFilters.output === 'all'
        || (appliedFilters.output === 'ready' ? item.hasOutputSheets : !item.hasOutputSheets)
      return matchesQuery && matchesPosition && matchesSample && matchesOutput
    })
  }, [analysis.workItems, appliedFilters])

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage = Math.min(page, pageCount)
  const visibleItems = filtered.slice((safePage - 1) * pageSize, safePage * pageSize)
  const selectedWithoutOutput = Boolean(selectedItem && !selectedItem.hasOutputSheets)
  const activeFilterCount = countActiveFilters(appliedFilters)
  const paginationItems = paginationRange(safePage, pageCount)

  function updateDraft<K extends keyof Filters>(key: K, value: Filters[K]) {
    setDraftFilters((current) => ({ ...current, [key]: value }))
  }

  function applyFilters(event?: FormEvent) {
    event?.preventDefault()
    setAppliedFilters({
      ...draftFilters,
      query: draftFilters.query.trim(),
    })
    setPage(1)
  }

  function clearFilters() {
    setDraftFilters(DEFAULT_FILTERS)
    setAppliedFilters(DEFAULT_FILTERS)
    setPage(1)
  }

  return (
    <div className="page-stack work-items-page">
      <section className="page-heading page-heading-with-icon">
        <span className="heading-icon gold"><ListIcon size={25} /></span>
        <div>
          <h1>Danh mục công việc</h1>
          <p>Danh sách được trích xuất từ sheet {analysis.dmSheetName.trim()}. Chọn một công việc để tiếp tục.</p>
        </div>
      </section>

      <section className="metric-grid work-metrics">
        <SummaryCard icon={<FileIcon size={23} />} label="Tổng công việc" value={analysis.workItemCount} tone="blue" />
        <SummaryCard icon={<CheckIcon size={23} />} label="Đã có biểu mẫu" value={readyCount} tone="green" note={toPercent(readyCount, analysis.workItemCount)} />
        <SummaryCard icon={<TemplateIcon size={23} />} label="Chưa có biểu mẫu" value={missingCount} tone="gold" note={toPercent(missingCount, analysis.workItemCount)} />
        <SummaryCard icon={<CalendarIcon size={23} />} label="Đã lấy mẫu" value={analysis.withSampleCount} tone="purple" note={toPercent(analysis.withSampleCount, analysis.workItemCount)} />
      </section>

      <form className="surface filters-surface" onSubmit={applyFilters}>
        <label className="search-field">
          <SearchIcon size={20} />
          <input
            value={draftFilters.query}
            onChange={(event) => updateDraft('query', event.target.value)}
            placeholder="Tìm theo số DM, nội dung hoặc số biên bản..."
          />
        </label>

        <label className="filter-field">
          <span>Vị trí</span>
          <select value={draftFilters.position} onChange={(event) => updateDraft('position', event.target.value)}>
            <option value="all">Tất cả vị trí</option>
            {positions.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>

        <label className="filter-field">
          <span>Lấy mẫu</span>
          <select value={draftFilters.sample} onChange={(event) => updateDraft('sample', event.target.value as SampleFilter)}>
            <option value="all">Tất cả</option>
            <option value="with">Có lấy mẫu</option>
            <option value="without">Không lấy mẫu</option>
          </select>
        </label>

        <label className="filter-field">
          <span>Biểu mẫu</span>
          <select value={draftFilters.output} onChange={(event) => updateDraft('output', event.target.value as OutputFilter)}>
            <option value="all">Tất cả</option>
            <option value="ready">Đã có biểu mẫu</option>
            <option value="missing">Chưa có biểu mẫu</option>
          </select>
        </label>

        <button className="secondary-button filter-apply-button" type="submit">
          <FilterIcon size={18} /> Áp dụng{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
        </button>
        <button className="filter-clear-button" type="button" onClick={clearFilters} disabled={activeFilterCount === 0 && countActiveFilters(draftFilters) === 0}>
          <XIcon size={17} /> Xóa lọc
        </button>
      </form>

      <section className="surface table-surface">
        <div className="table-title-row">
          <div>
            <h2>Danh sách công việc</h2>
            <p>{filtered.length} kết quả{activeFilterCount ? ` sau khi áp dụng ${activeFilterCount} bộ lọc` : ''}</p>
          </div>
          {selectedItem && (
            <span className={`selected-item-chip ${selectedWithoutOutput ? 'warning' : ''}`}>
              {selectedWithoutOutput ? <InfoIcon size={16} /> : <CheckIcon size={16} />}
              Đã chọn DM {selectedItem.number}
            </span>
          )}
        </div>

        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th className="select-col">Chọn</th>
                <th className="number-col">Số DM</th>
                <th>Nội dung công việc</th>
                <th>Vị trí</th>
                <th>Thời gian</th>
                <th>Số biên bản</th>
                <th>Ngày lấy mẫu</th>
                <th>Trạng thái biểu mẫu</th>
              </tr>
            </thead>
            <tbody>
              {visibleItems.map((item) => {
                const selected = selectedItem?.number === item.number
                return (
                  <tr
                    key={`${item.number}-${item.excelRow}`}
                    className={`${selected ? 'selected-row' : ''} ${!item.hasOutputSheets ? 'output-missing' : ''}`.trim()}
                    onClick={() => onSelect(item)}
                    aria-selected={selected}
                  >
                    <td className="select-col">
                      <button
                        type="button"
                        className={`selection-box ${selected ? 'selected' : ''}`}
                        aria-label={`Chọn danh mục ${item.number}`}
                        onClick={(event) => {
                          event.stopPropagation()
                          onSelect(item)
                        }}
                      >{selected ? <CheckIcon size={15} /> : null}</button>
                    </td>
                    <td className="number-col"><strong>{item.number}</strong></td>
                    <td className="long-content">{textOrDash(item.content)}</td>
                    <td>{textOrDash(item.position)}</td>
                    <td>{textOrDash(item.inspectionTime)}</td>
                    <td>{textOrDash(item.recordNumber)}</td>
                    <td>{textOrDash(item.sampleDate)}</td>
                    <td>
                      <span className={`availability-pill ${item.hasOutputSheets ? 'ready' : 'missing'}`}>
                        {item.hasOutputSheets ? <><CheckIcon size={14} /> Đã có biểu mẫu</> : <><InfoIcon size={14} /> Chưa có biểu mẫu</>}
                      </span>
                    </td>
                  </tr>
                )
              })}
              {visibleItems.length === 0 && (
                <tr>
                  <td colSpan={8} className="empty-table">
                    <SearchIcon size={30} />
                    <strong>Không tìm thấy công việc phù hợp</strong>
                    <span>Hãy thay đổi từ khóa hoặc xóa bớt bộ lọc.</span>
                    <button type="button" className="secondary-button compact" onClick={clearFilters}>Xóa toàn bộ bộ lọc</button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="table-footer">
          <div className="page-size-control">
            <span>Hiển thị</span>
            <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1) }}>
              {PAGE_SIZE_OPTIONS.map((size) => <option key={size} value={size}>{size}</option>)}
            </select>
            <span>dòng / trang</span>
          </div>

          <div className="pagination-status">
            {filtered.length === 0 ? '0 kết quả' : `${(safePage - 1) * pageSize + 1}–${Math.min(safePage * pageSize, filtered.length)} của ${filtered.length}`}
          </div>

          <div className="pagination">
            <button type="button" onClick={() => setPage(1)} disabled={safePage <= 1} aria-label="Trang đầu">«</button>
            <button type="button" onClick={() => setPage(Math.max(1, safePage - 1))} disabled={safePage <= 1} aria-label="Trang trước"><ChevronLeftIcon size={16} /></button>
            {paginationItems.map((item, index) => item === 'ellipsis'
              ? <span key={`ellipsis-${index}`} className="pagination-ellipsis">…</span>
              : <button type="button" key={item} className={safePage === item ? 'current' : ''} onClick={() => setPage(item)}>{item}</button>)}
            <button type="button" onClick={() => setPage(Math.min(pageCount, safePage + 1))} disabled={safePage >= pageCount} aria-label="Trang sau"><ChevronRightIcon size={16} /></button>
            <button type="button" onClick={() => setPage(pageCount)} disabled={safePage >= pageCount} aria-label="Trang cuối">»</button>
          </div>
        </footer>
      </section>

      <section className="work-action-bar">
        <div className="work-note">
          <InfoIcon size={20} />
          <div>
            <strong>Lưu ý</strong>
            <p>Công việc chưa có sheet đầu ra vẫn chọn được để kiểm tra, nhưng chưa thể chuyển sang bước tạo biểu mẫu trong V1.</p>
          </div>
        </div>
        <div className="work-selection-action">
          <span>Đã chọn <strong>{selectedItem ? 1 : 0}</strong> công việc</span>
          <button
            className="primary-button continue-button"
            type="button"
            onClick={onContinue}
            disabled={!selectedItem || !selectedItem.hasOutputSheets || busy}
            title={selectedWithoutOutput ? `Danh mục ${selectedItem?.number} chưa có sheet đầu ra` : undefined}
          >
            Tiếp tục chọn biểu mẫu <ChevronRightIcon size={18} />
          </button>
        </div>
      </section>
    </div>
  )
}

function SummaryCard({
  icon,
  label,
  value,
  tone,
  note,
}: {
  icon: React.ReactNode
  label: string
  value: React.ReactNode
  tone: 'blue' | 'green' | 'gold' | 'purple'
  note?: string
}) {
  return (
    <article className="summary-card">
      <span className={`summary-icon ${tone}`}>{icon}</span>
      <div>
        <strong>{value}</strong>
        <span>{label}</span>
      </div>
      {note && <small className={tone}>{note}</small>}
    </article>
  )
}

function compactText(value: string): string {
  return value.replace(/\s+/g, ' ').trim()
}

function normalizeText(value: unknown): string {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/\s+/g, ' ')
    .trim()
    .toLocaleLowerCase('vi-VN')
}

function countActiveFilters(filters: Filters): number {
  return Number(Boolean(filters.query.trim()))
    + Number(filters.position !== 'all')
    + Number(filters.sample !== 'all')
    + Number(filters.output !== 'all')
}

function toPercent(value: number, total: number): string {
  if (!total) return '0%'
  return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format((value / total) * 100)}%`
}

function paginationRange(current: number, total: number): Array<number | 'ellipsis'> {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1)
  const values: Array<number | 'ellipsis'> = [1]
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  if (start > 2) values.push('ellipsis')
  for (let value = start; value <= end; value += 1) values.push(value)
  if (end < total - 1) values.push('ellipsis')
  values.push(total)
  return values
}
