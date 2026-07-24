import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { analysis, workItem } from '../test/fixtures'
import { WorkItemsPage } from './WorkItemsPage'

describe('WorkItemsPage per-document status', () => {
  it('renders main-only status and lets the row be selected', async () => {
    const user = userEvent.setup()
    const item = workItem()
    const onToggle = vi.fn()
    render(<WorkItemsPage analysis={analysis([item])} selectedItems={[]} onToggle={onToggle} onContinue={vi.fn()} busy={false} />)
    expect(screen.getAllByText('Chỉ có sheet chính').length).toBeGreaterThan(0)
    expect(screen.getByText(/MAIN có sẵn · LM sẽ tạo · GM sẽ tạo/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Chọn DM 150' }))
    expect(onToggle).toHaveBeenCalledWith(item)
  })

  it('filters by missing LM status instantly (no Apply button needed)', async () => {
    const user = userEvent.setup()
    const missingLm = workItem({ itemNumber: '151', sheetStatus: 'MISSING_LM', hasLmSheet: false, hasGmSheet: true })
    render(<WorkItemsPage analysis={analysis([workItem(), missingLm])} selectedItems={[]} onToggle={vi.fn()} onContinue={vi.fn()} busy={false} />)
    await user.selectOptions(screen.getByLabelText('Trạng thái sheet'), 'MISSING_LM')
    expect(screen.getByText('DM 151')).toBeInTheDocument()
    expect(screen.queryByText('DM 150')).not.toBeInTheDocument()
  })

  it('cascades tầng/khu vực options and rows when a hạng mục lớn is chosen', async () => {
    const user = userEvent.setup()
    const wallItem = workItem({ itemNumber: '150', position: 'Tầng 2', majorCategory: 'HẠNG MỤC XÂY TƯỜNG' })
    const plasterItem = workItem({ itemNumber: '160', position: 'Nhà rác', majorCategory: 'HẠNG MỤC TRÁT TƯỜNG' })
    render(<WorkItemsPage analysis={analysis([wallItem, plasterItem])} selectedItems={[]} onToggle={vi.fn()} onContinue={vi.fn()} busy={false} />)
    expect(screen.getByText('DM 150')).toBeInTheDocument()
    expect(screen.getByText('DM 160')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Hạng mục lớn'), 'HẠNG MỤC TRÁT TƯỜNG')
    expect(screen.queryByText('DM 150')).not.toBeInTheDocument()
    expect(screen.getByText('DM 160')).toBeInTheDocument()
    const positionSelect = screen.getByLabelText('Tầng / khu vực') as HTMLSelectElement
    const positionOptions = Array.from(positionSelect.options).map((option) => option.textContent)
    expect(positionOptions).toContain('Nhà rác')
    expect(positionOptions).not.toContain('Tầng 2')
  })
})
