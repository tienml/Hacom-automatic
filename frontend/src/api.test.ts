import { afterEach, describe, expect, it, vi } from 'vitest'
import { generateDocument } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('generation API payload', () => {
  it('sends mode and source template for every selected output', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ documentId: '1' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await generateDocument('job-1', [{ itemNumber: '150', materialFamily: 'VUA', outputs: [
      { sheetName: '150', documentType: 'MAIN', generationMode: 'EXISTING_SHEET' },
      { sheetName: '1.LMV (150)', documentType: 'LM', generationMode: 'CLONE_TEMPLATE', sourceTemplate: '1.LMV (114)' },
      { sheetName: '1.GMV (150)', documentType: 'GM', generationMode: 'CLONE_TEMPLATE', sourceTemplate: '1.GMV (114)' },
    ] }], false)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    const body = JSON.parse(String(init.body))
    expect(body.selections[0].outputs).toEqual([
      expect.objectContaining({ documentType: 'MAIN', generationMode: 'EXISTING_SHEET' }),
      expect.objectContaining({ documentType: 'LM', sourceTemplate: '1.LMV (114)' }),
      expect.objectContaining({ documentType: 'GM', sourceTemplate: '1.GMV (114)' }),
    ])
  })
})
