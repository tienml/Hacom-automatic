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

  it('filters by missing LM status', async () => {
    const user = userEvent.setup()
    const missingLm = workItem({ itemNumber: '151', sheetStatus: 'MISSING_LM', hasLmSheet: false, hasGmSheet: true })
    render(<WorkItemsPage analysis={analysis([workItem(), missingLm])} selectedItems={[]} onToggle={vi.fn()} onContinue={vi.fn()} busy={false} />)
    await user.selectOptions(screen.getByLabelText('Trạng thái sheet'), 'MISSING_LM')
    await user.click(screen.getByRole('button', { name: /Áp dụng/ }))
    expect(screen.getByText('DM 151')).toBeInTheDocument()
    expect(screen.queryByText('DM 150')).not.toBeInTheDocument()
  })
})
