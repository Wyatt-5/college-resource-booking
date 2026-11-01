import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { createItem, getItems, loginAdmin, uploadItemImage, deleteItemImage, setItemStock, getPendingReviews, reviewReservation, MANAGEMENT_DEMO, logoutAdmin } from '../src/lib/api.js'
const originalFetch = globalThis.fetch
after(() => { globalThis.fetch = originalFetch })

test('default mode requires real login instead of accepting the demonstration password', async () => {
  assert.equal(MANAGEMENT_DEMO, false)
  globalThis.fetch = async () => Response.json({ code: 'NOT_FOUND' }, { status: 404 })
  await assert.rejects(loginAdmin('admin', 'admin123'), /获取安全令牌失败/)
})

test('item create sends agreed fields, identity and idempotency key to the server', async () => {
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/auth/csrf')) return Response.json({ token: 'test-csrf', headerName: 'X-CSRF-TOKEN' })
    assert.equal(url, '/api/v1/auth/login')
    assert.deepEqual(JSON.parse(options.body), { loginName: 'test', password: 'test-password' })
    assert.equal(options.headers['X-CSRF-TOKEN'], 'test-csrf')
    return Response.json({ id: 1, loginName: 'test', displayName: '测试管理员', status: 'ACTIVE', grants: [{ role: 'SYSTEM_ADMIN', locationId: null }], reviewer: false })
  }
  await loginAdmin('test', 'test-password')
  let request
  globalThis.fetch = async (url, options) => { request = { url, options }; return Response.json({ id: 7, createdAt: '2026-10-28T00:00:00Z' }, { status: 201 }) }
  const payload = { name: '相机', locationId: 1, rules: { maxBorrowDays: 7, advanceDays: 0 } }
  assert.equal((await createItem(payload, { idempotencyKey: 'form-session-1' })).id, 7)
  assert.equal(request.url, '/api/v1/items')
  assert.equal(request.options.headers['Idempotency-Key'], 'form-session-1')
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'test-csrf')
  assert.equal(request.options.headers.Authorization, undefined)
  assert.equal(request.options.credentials, 'same-origin')
  assert.deepEqual(JSON.parse(request.options.body), payload)
})

test('managed list requests scoped pagination', async () => {
  let url
  globalThis.fetch = async (value) => { url = value; return Response.json({ content: [], totalElements: 0, page: 1, size: 20 }) }
  assert.equal((await getItems({ page: 1 })).page, 1)
  assert.equal(url, '/api/v1/items?scope=managed&page=1&size=20')
})

test('stock quantity uses the authenticated backend endpoint', async () => {
  let request
  globalThis.fetch = async (url, options) => { request = { url, options }; return Response.json({ id: 7, unitSummary: { total: 4, available: 4 } }) }
  const result = await setItemStock(7, 4)
  assert.equal(result.unitSummary.total, 4)
  assert.equal(request.url, '/api/v1/items/7/stock')
  assert.equal(request.options.method, 'PUT')
  assert.deepEqual(JSON.parse(request.options.body), { quantity: 4 })
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'test-csrf')
  assert.equal(request.options.credentials, 'same-origin')
})

test('valuable item review reads the queue and sends a real decision', async () => {
  let request
  globalThis.fetch = async (url, options) => {
    request = { url, options }
    return url.includes('/review/reservations?')
      ? Response.json({ content: [{ id: 31, status: 'PENDING_APPROVAL' }], totalElements: 1 })
      : Response.json({ id: 31, status: 'CONFIRMED' })
  }
  assert.equal((await getPendingReviews({ page: 0 })).content[0].id, 31)
  assert.equal(request.url, '/api/v1/review/reservations?page=0&size=20')
  assert.equal((await reviewReservation(31, { decision: 'APPROVE' })).status, 'CONFIRMED')
  assert.equal(request.url, '/api/v1/reservations/31/review')
  assert.equal(request.options.method, 'PATCH')
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'test-csrf')
})

test('photo upload and deletion include identity; multipart field is file', async () => {
  let request
  globalThis.fetch = async (url, options) => { request = { url, options }; return Response.json({ id: 9 }, { status: 201 }) }
  await uploadItemImage(7, new File(['test'], 'test.png', { type: 'image/png' }))
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'test-csrf')
  assert.equal(request.options.headers.Authorization, undefined)
  assert.equal(request.options.credentials, 'same-origin')
  assert.equal(request.options.body.get('file').name, 'test.png')
  assert.equal(request.options.headers['Content-Type'], undefined)
  globalThis.fetch = async (url, options) => { request = { url, options }; return new Response(null, { status: 204 }) }
  await deleteItemImage(7, 9)
  assert.equal(request.options.method, 'DELETE')
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'test-csrf')
  assert.equal(request.options.headers.Authorization, undefined)
  assert.equal(request.options.credentials, 'same-origin')
})

test('server field errors reach the form instead of returning simulated success', async () => {
  globalThis.fetch = async () => Response.json({ code: 'VALIDATION_ERROR', message: '请检查提交的信息', details: { name: '必填' } }, { status: 400 })
  await assert.rejects(createItem({ name: '' }, { idempotencyKey: 'bad' }), error => error.code === 'VALIDATION_ERROR' && error.details.name === '必填')
})

test('logout invalidates the server session using CSRF', async () => {
  let request
  globalThis.fetch = async (url, options) => { request = { url, options }; return new Response(null, { status: 204 }) }
  await logoutAdmin()
  assert.equal(request.url, '/api/v1/auth/logout')
  assert.equal(request.options.method, 'POST')
  assert.equal(request.options.headers['X-CSRF-TOKEN'], 'test-csrf')
})
