import { describe, expect, it } from 'vitest'
import { workItem } from './test/fixtures'
import { canSelectWorkItem, countSelectedSheets, familyLabel, hasIncompleteSelection, requiresManualFamily, sheetStatusLabel } from './uiRules'

describe('safe-template UI rules', () => {
  it('keeps main-only and no-sheet rows selectable', () => {
    expect(canSelectWorkItem(workItem())).toBe(true)
    expect(canSelectWorkItem(workItem({ hasOutputSheets: false, hasMainSheet: false, existingSheetNames: [], sheetStatus: 'NO_SHEETS' }))).toBe(true)
  })
  it('requires an explicit family for UNKNOWN incomplete items', () => {
    const unknown = workItem({ materialFamily: 'UNKNOWN', requiresTemplateSelection: true, sheetStatus: 'UNKNOWN_MATERIAL' })
    expect(requiresManualFamily(unknown)).toBe(true)
    expect(requiresManualFamily(unknown, 'VUA')).toBe(false)
  })
  it('detects incomplete mixed selections and counts outputs', () => {
    const items = [workItem(), workItem({ itemNumber: '111', hasCompleteSamplePair: true })]
    expect(hasIncompleteSelection(items, { '150': ['150'], '111': [] })).toBe(true)
    expect(countSelectedSheets({ '150': ['150', '1.LMV (150)'], '111': ['111'] })).toBe(3)
  })
  it('shows Vietnamese labels', () => {
    expect(familyLabel('VUA')).toBe('Vữa')
    expect(familyLabel('BETONG')).toBe('Bê tông')
    expect(sheetStatusLabel('MAIN_ONLY')).toBe('Chỉ có sheet chính')
  })
})
