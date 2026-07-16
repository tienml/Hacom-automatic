import { useMemo, useState } from 'react'
import type { AnalyzeResponse, WorkItem } from '../types'
import {
  BanIcon,
  BuildingIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  FileIcon,
  FilterIcon,
  ListIcon,
  SearchIcon,
} from '../icons'
import { safeLower, textOrDash } from '../utils'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

type SampleFilter = 'all' | 'with' | 'without'

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
  const [query, setQuery] = useState('')
  const [position, setPosition] = useState('all')
  const [sampleFilter, setSampleFilter] = useState<SampleFilter>('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const positions = useMemo(() => {
    return Array.from(new Set(analysis.workItems.map((item) => item.position.trim()).filter(Boolean))).sort((a, b) => a.localeCompare(b, 'vi'))
  }, [analysis.workItems])

  const filtered = useMemo(() => {
    const needle = safeLower(query.trim())
    return analysis.workItems.filter((item) => {
      const matchesQuery = !needle || safeLower([
        item.number,
        item.content,
        item.position,
        item.recordNumber,
      ].join(' ')).includes(needle)
      const matchesPosition = position === 'all' || item.position === position
      const hasSample = Boolean(item.sampleDate?.trim())
      const matchesSample = sampleFilter === 'all' || (sampleFilter === 'with' ? hasSample : !hasSample)
      return matchesQuery && matchesPosition && matchesSample
    })
  }, [analysis.workItems, position, query, sampleFilter])

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage = Math.min(page, pageCount)
  const visibleItems = filtered.slice((safePage - 1) * pageSize, safePage * pageSize)

  function changeFilter(action: () => void) {
    action()
    setPage(1)
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <h1>Danh mục công việc</h1>
        <p>Danh sách công việc được trích xuất trực tiếp từ sheet {analysis.dmSheetName}.</p>
      </section>

      <section className="metric-grid work-metrics">
        <SummaryCard icon={<FileIcon />} label="Tổng số công việc" value={analysis.workItemCount} />
        <SummaryCard icon={<ListIcon />} label="Có lấy mẫu" value={analysis.withSampleCount} />
        <SummaryCard icon={<BanIcon />} label="Không lấy mẫu" value={analysis.withoutSampleCount} />
        <SummaryCard icon={<BuildingIcon />} label="Dự án" value={textOrDash(analysis.project.projectName)} />
      </section>

      <section className="surface filters-surface">
        <label className="search-field">
          <SearchIcon size={20} />
          <input
            value={query}
            onChange={(event) => changeFilter(() => setQuery(event.target.value))}
            placeholder="Tìm theo số DM, nội dung hoặc số biên bản"
          />
        </label>
        <label className="filter-field">
          <span>Vị trí</span>
          <select value={position} onChange={(event) => changeFilter(() => setPosition(event.target.value))}>
            <option value="all">Tất cả vị trí</option>
            {positions.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label className="filter-field">
          <span>Lấy mẫu</span>
          <select value={sampleFilter} onChange={(event) => changeFilter(() => setSampleFilter(event.target.value as SampleFilter))}>
            <option value="all">Tất cả</option>
            <option value="with">Có lấy mẫu</option>
            <option value="without">Không lấy mẫu</option>
          </select>
        </label>
        <button className="filter-icon-button" type="button" title="Bộ lọc đang áp dụng"><FilterIcon size={20} /></button>
      </section>

      <section className="surface table-surface">
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
                <th>Biểu mẫu</th>
              </tr>
            </thead>
            <tbody>
              {visibleItems.map((item) => {
                const selected = selectedItem?.number === item.number
                return (
                  <tr
                    key={item.number}
                    className={selected ? 'selected-row' : ''}
                    onClick={() => item.hasOutputSheets && onSelect(item)}
                  >
                    <td className="select-col">
                      <button
                        type="button"
                        className={`selection-box ${selected ? 'selected' : ''}`}
                        disabled={!item.hasOutputSheets}
                        aria-label={`Chọn danh mục ${item.number}`}
                        onClick={(event) => {
                          event.stopPropagation()
                          if (item.hasOutputSheets) onSelect(item)
                        }}
                      >{selected ? '✓' : ''}</button>
                    </td>
                    <td className="number-col"><strong>{item.number}</strong></td>
                    <td className="long-content">{textOrDash(item.content)}</td>
                    <td>{textOrDash(item.position)}</td>
                    <td>{textOrDash(item.inspectionTime)}</td>
                    <td>{textOrDash(item.recordNumber)}</td>
                    <td>{textOrDash(item.sampleDate)}</td>
                    <td>
                      <span className={`availability-pill ${item.hasOutputSheets ? 'ready' : 'missing'}`}>
                        {item.hasOutputSheets ? 'Sẵn sàng' : 'Chưa có'}
                      </span>
                    </td>
                  </tr>
                )
              })}
              {visibleItems.length === 0 && (
                <tr><td colSpan={8} className="empty-table">Không tìm thấy công việc phù hợp với bộ lọc.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="table-footer">
          <span>
            Hiển thị {filtered.length === 0 ? 0 : (safePage - 1) * pageSize + 1} đến {Math.min(safePage * pageSize, filtered.length)} trong {filtered.length} công việc
          </span>
          <div className="pagination">
            <button type="button" onClick={() => setPage(Math.max(1, safePage - 1))} disabled={safePage <= 1}><ChevronLeftIcon size={17} /></button>
            {Array.from({ length: Math.min(pageCount, 5) }, (_, index) => index + 1).map((number) => (
              <button type="button" key={number} className={safePage === number ? 'current' : ''} onClick={() => setPage(number)}>{number}</button>
            ))}
            {pageCount > 5 && <span>… {pageCount}</span>}
            <button type="button" onClick={() => setPage(Math.min(pageCount, safePage + 1))} disabled={safePage >= pageCount}><ChevronRightIcon size={17} /></button>
            <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1) }}>
              {PAGE_SIZE_OPTIONS.map((size) => <option key={size} value={size}>{size} / trang</option>)}
            </select>
          </div>
          <button className="primary-button continue-button" type="button" onClick={onContinue} disabled={!selectedItem || busy}>
            <ListIcon size={18} /> Tiếp tục chọn biểu mẫu <ChevronRightIcon size={18} />
          </button>
        </footer>
      </section>
    </div>
  )
}

function SummaryCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: React.ReactNode }) {
  return (
    <article className="summary-card">
      <span className="summary-icon">{icon}</span>
      <div><span>{label}</span><strong>{value}</strong></div>
    </article>
  )
}
