import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { createItem, getItems, loginAdmin, uploadItemImage, deleteItemImage, MANAGEMENT_DEMO, logoutAdmin } from '../src/lib/api.js'
const originalFetch = globalThis.fetch
after(() => { globalThis.fetch = originalFetch; logoutAdmin() })

test('default mode requires real login instead of accepting the demonstration password', async () => {
  assert.equal(MANAGEMENT_DEMO, false)
  globalThis.fetch = async () => Response.json({ code: 'NOT_FOUND' }, { status: 404 })
  await assert.rejects(loginAdmin('admin', 'admin123'), /账号登录接口尚未接入/)
})

test('item create sends agreed fields, identity and idempotency key to the server', async () => {
  globalThis.fetch = async () => Response.json({ token: 'test-session', user: { id: 1, name: '测试管理员' } })
  await loginAdmin('test', 'test-password')
  let request
  globalThis.fetch = async (url, options) => { request = { url, options }; return Response.json({ id: 7, createdAt: '2026-10-28T00:00:00Z' }, { status: 201 }) }
  const payload = { name: '相机', locationId: 1, rules: { maxBorrowDays: 7, advanceDays: 0 } }
  assert.equal((await createItem(payload, { idempotencyKey: 'form-session-1' })).id, 7)
  assert.equal(request.url, '/api/v1/items')
  assert.equal(request.options.headers['Idempotency-Key'], 'form-session-1')
  assert.equal(request.options.headers.Authorization, 'Bearer test-session')
  assert.deepEqual(JSON.parse(request.options.body), payload)
})

test('managed list requests scoped pagination', async () => {
  let url
  globalThis.fetch = async (value) => { url = value; return Response.json({ content: [], totalElements: 0, page: 1, size: 20 }) }
  assert.equal((await getItems({ page: 1 })).page, 1)
  assert.equal(url, '/api/v1/items?scope=managed&page=1&size=20')
})

test('photo upload and deletion include identity; multipart field is file', async () => {
  let request
  globalThis.fetch = async (url, options) => { request = { url, options }; return Response.json({ id: 9 }, { status: 201 }) }
  await uploadItemImage(7, new File(['test'], 'test.png', { type: 'image/png' }))
  assert.equal(request.options.headers.Authorization, 'Bearer test-session')
  assert.equal(request.options.body.get('file').name, 'test.png')
  assert.equal(request.options.headers['Content-Type'], undefined)
  globalThis.fetch = async (url, options) => { request = { url, options }; return new Response(null, { status: 204 }) }
  await deleteItemImage(7, 9)
  assert.equal(request.options.method, 'DELETE')
  assert.equal(request.options.headers.Authorization, 'Bearer test-session')
})

test('server field errors reach the form instead of returning simulated success', async () => {
  globalThis.fetch = async () => Response.json({ code: 'VALIDATION_ERROR', message: '请检查提交的信息', details: { name: '必填' } }, { status: 400 })
  await assert.rejects(createItem({ name: '' }, { idempotencyKey: 'bad' }), error => error.code === 'VALIDATION_ERROR' && error.details.name === '必填')
})
