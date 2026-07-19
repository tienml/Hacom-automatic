import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { output, workItem } from '../test/fixtures'
import { TemplatesPage } from './TemplatesPage'

const status = { application: 'HaCom', version: '1', configuredPdfMode: 'disabled', activePdfEngine: 'none', pdfAvailable: false, message: 'disabled' }
function renderPage(overrides: Record<string, unknown> = {}) {
  const item = workItem()
  const outputs = [
    output({ sheetName: '150', displayName: 'Sheet chính', type: 'MAIN', documentType: 'MAIN', generated: false, sourceTemplate: null, availableSourceTemplates: [], generationMode: 'EXISTING_SHEET', availability: 'EXISTING', fieldDecisions: [] }),
    output(),
    output({ sheetName: '1.GMV (150)', displayName: 'Biên bản giao mẫu', type: 'GM', documentType: 'GM', sourceTemplate: '1.GMV (111)', availableSourceTemplates: ['1.GMV (111)', '1.GMV (114)'] }),
  ]
  const props = {
    items: [item], outputsByItem: { '150': outputs }, selectedSheetsByItem: { '150': outputs.map((value) => value.sheetName) }, familyByItem: { '150': 'VUA' as const },
    onChangeFamily: vi.fn(), onChangeTemplate: vi.fn(), onToggle: vi.fn(), onSelectAll: vi.fn(), onBack: vi.fn(), onGenerate: vi.fn(), status, busy: false,
    ...overrides,
  }
  return { ...render(<TemplatesPage {...props} />), props, item, outputs }
}

describe('TemplatesPage output-level selection', () => {
  it('shows MAIN existing plus LM and GM generated', () => {
    renderPage()
    expect(screen.getByText('Sheet chính')).toBeInTheDocument()
    expect(screen.getAllByText('Tạo mới an toàn')).toHaveLength(2)
    expect(screen.getByText(/MAIN có sẵn · LM clone · GM clone/)).toBeInTheDocument()
  })
  it('shows detailed FieldDecision values and CLEAR action', () => {
    renderPage()
    expect(screen.getAllByText('Vị trí').length).toBeGreaterThan(0)
    expect(screen.getAllByText('DM.columnD').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Tầng 2').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Kích thước mẫu').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Để trống').length).toBeGreaterThan(0)
  })
  it('changes source template independently', async () => {
    const user = userEvent.setup()
    const { props } = renderPage()
    await user.selectOptions(screen.getByLabelText('Template nguồn LM DM 150'), '1.LMV (114)')
    expect(props.onChangeTemplate).toHaveBeenCalledWith('150', 'LM', '1.LMV (114)')
  })
  it('requires family for UNKNOWN but warnings do not disable a valid export', () => {
    const unknown = workItem({ materialFamily: 'UNKNOWN', sheetStatus: 'UNKNOWN_MATERIAL', hasMainSheet: false, existingSheetNames: [], hasOutputSheets: false })
    const { rerender, props } = renderPage({ items: [unknown], outputsByItem: { '150': [] }, selectedSheetsByItem: { '150': [] }, familyByItem: { '150': 'UNKNOWN' } })
    expect(screen.getByText(/Không nhận diện chắc chắn vật liệu/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tạo file xem trước/ })).toBeDisabled()
    const warnedItem = workItem({ warnings: ['Cảnh báo không nghiêm trọng'] })
    const main = output({ sheetName: '150', displayName: 'Sheet chính', documentType: 'MAIN', type: 'MAIN', generated: false, sourceTemplate: null, availableSourceTemplates: [], generationMode: 'EXISTING_SHEET', availability: 'EXISTING', fieldDecisions: [] })
    rerender(<TemplatesPage {...props} items={[warnedItem]} outputsByItem={{ '150': [main] }} selectedSheetsByItem={{ '150': ['150'] }} familyByItem={{ '150': 'VUA' }} />)
    expect(screen.getByText('Cảnh báo không nghiêm trọng')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tạo file xem trước/ })).toBeEnabled()
  })
  it('allows UNKNOWN item to export its existing MAIN without selecting a material family', () => {
    const unknownMain = workItem({
      materialFamily: 'UNKNOWN', sheetStatus: 'UNKNOWN_MATERIAL', requiresTemplateSelection: true,
      hasMainSheet: true, hasLmSheet: false, hasGmSheet: false, existingSheetNames: ['150'], hasOutputSheets: true,
    })
    const main = output({ sheetName: '150', displayName: 'Sheet chính', documentType: 'MAIN', type: 'MAIN', generated: false, sourceTemplate: null, availableSourceTemplates: [], generationMode: 'EXISTING_SHEET', availability: 'EXISTING', fieldDecisions: [] })
    renderPage({ items: [unknownMain], outputsByItem: { '150': [main] }, selectedSheetsByItem: { '150': ['150'] }, familyByItem: { '150': 'UNKNOWN' } })
    expect(screen.getByText('Sheet chính')).toBeInTheDocument()
    expect(screen.getByText(/Không nhận diện chắc chắn vật liệu/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tạo file xem trước/ })).toBeEnabled()
  })

  it('locks material family when an existing LM or GM already defines the family', () => {
    const partial = workItem({
      itemNumber: '151', materialFamily: 'VUA', sheetStatus: 'MISSING_GM',
      hasMainSheet: true, hasLmSheet: true, hasGmSheet: false,
      hasCompleteSamplePair: false, hasPartialSamplePair: true,
    })
    renderPage({ items: [partial], familyByItem: { '151': 'VUA' }, outputsByItem: { '151': [] }, selectedSheetsByItem: { '151': [] } })
    expect(screen.getByLabelText('Loại mẫu khóa DM 151')).toHaveTextContent('Vữa')
    expect(screen.queryByLabelText('Loại mẫu DM 151')).not.toBeInTheDocument()
  })

})
