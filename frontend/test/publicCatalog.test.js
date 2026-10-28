import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createPublicCatalog } from '../src/lib/publicCatalog.js'

test('public list sends filters and pagination without relying on management mocks', async () => {
  let requested
  const page = { content: [{ id: 7, unitSummary: { available: 1, total: 3 } }], page: 1, size: 12, totalElements: 13 }
  const api = createPublicCatalog({ fetchImpl: async url => { requested = url; return Response.json(page) } })
  assert.deepEqual(await api.list({ keyword: '相机 & %', locationId: 2, onlyAvailable: true, page: 1 }), page)
  const query = new URL(requested, 'http://localhost').searchParams
  assert.equal(query.get('keyword'), '相机 & %')
  assert.equal(query.get('locationId'), '2')
  assert.equal(query.get('onlyAvailable'), 'true')
  assert.equal(query.get('page'), '1')
})

test('old array contract is rejected instead of silently displaying an empty list', async () => {
  const api = createPublicCatalog({ fetchImpl: async () => Response.json([]) })
  await assert.rejects(api.list(), /接口格式不符/)
})

test('network and server errors do not fall back to fake items', async () => {
  const offline = createPublicCatalog({ fetchImpl: async () => { throw new Error('offline') } })
  await assert.rejects(offline.list(), /无法连接后端/)
  const failed = createPublicCatalog({ fetchImpl: async () => Response.json({ code: 'NOT_FOUND', message: '物品已停用', requestId: 'r1' }, { status: 404 }) })
  await assert.rejects(failed.detail(7), error => error.code === 'NOT_FOUND' && error.requestId === 'r1')
})

test('detail performs its own request to retrieve current server state', async () => {
  let requested
  const item = { id: 7, name: '最新名称', rules: { maxBorrowDays: null, advanceDays: null }, unitSummary: { total: 2, available: 0 } }
  const api = createPublicCatalog({ fetchImpl: async url => { requested = url; return Response.json(item) } })
  assert.deepEqual(await api.detail(7), item)
  assert.equal(requested, '/api/v1/items/7')
})
