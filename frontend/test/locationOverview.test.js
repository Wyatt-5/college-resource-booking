import { test } from 'node:test'
import assert from 'node:assert/strict'
import { loadResourceOverview } from '../src/features/dashboard/overview.js'

test('location overview counts real catalog data across pages', async () => {
  const pages = []
  const result = await loadResourceOverview({
    getLocations: async () => [
      { id: 1, name: '办公室 A', status: 'ACTIVE' },
      { id: 2, name: '物资室', status: 'ACTIVE' },
    ],
    getPublicItems: async ({ page, size }) => {
      pages.push({ page, size })
      const content = page === 0
        ? [{ id: 11, locationId: 1, unitSummary: { available: 2 } }, { id: 12, locationId: 1, unitSummary: { available: 0 } }]
        : [{ id: 13, locationId: 2, unitSummary: { available: 1 } }]
      return { content, totalElements: 3, page, size }
    },
  })
  assert.deepEqual(pages, [{ page: 0, size: 100 }, { page: 1, size: 100 }])
  assert.deepEqual(result.totals, { locations: 2, items: 3, availableItems: 2, availableUnits: 3 })
  assert.deepEqual(result.locations.map(({ itemCount, availableUnits }) => [itemCount, availableUnits]), [[2, 2], [1, 1]])
})
