import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { loginUser, createReservation, createUserAccount, getMyReservations, logoutUser } from '../src/lib/api.js'

const originalFetch = globalThis.fetch
after(() => { globalThis.fetch = originalFetch })

test('ordinary user logs in and submits a real booking with CSRF and retry key', async () => {
  const calls = []
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options })
    if (url.endsWith('/auth/csrf')) return Response.json({ token: 'csrf-test', headerName: 'X-CSRF-TOKEN' })
    if (url.endsWith('/auth/login')) {
      return Response.json({ id: 4, loginName: 'student04', displayName: '测试用户',
        status: 'ACTIVE', grants: [], reviewer: false })
    }
    if (url.endsWith('/users')) {
      return Response.json({ id: 4, loginName: 'student04', displayName: '测试用户',
        status: 'ACTIVE', grants: [], reviewer: false }, { status: 201 })
    }
    if (url.endsWith('/reservations')) {
      return Response.json({ id: 9, status: 'CONFIRMED' }, { status: 201 })
    }
    if (url.includes('/me/reservations')) {
      return Response.json({ content: [{ id: 9 }], page: 0, size: 20, totalElements: 1 })
    }
    return new Response(null, { status: 204 })
  }
  await createUserAccount({ loginName: 'student04', displayName: '测试用户', password: 'password-1234' })
  const { user } = await loginUser('student04', 'password-1234')
  assert.equal(user.name, '测试用户')
  const body = { resourceType: 'ITEM', resourceId: 7, startAt: '2026-11-03T01:00:00Z',
    endAt: '2026-11-03T02:00:00Z', purpose: '学院活动', quantity: 1 }
  assert.equal((await createReservation(body, 'booking-uuid-key')).status, 'CONFIRMED')
  assert.equal((await getMyReservations()).content[0].id, 9)
  const booking = calls.find(call => call.url.endsWith('/reservations'))
  const account = calls.find(call => call.url.endsWith('/users'))
  assert.equal(account.options.headers['X-CSRF-TOKEN'], 'csrf-test')
  assert.equal(account.options.credentials, 'same-origin')
  assert.equal(booking.options.headers['Idempotency-Key'], 'booking-uuid-key')
  assert.equal(booking.options.headers['X-CSRF-TOKEN'], 'csrf-test')
  assert.equal(booking.options.credentials, 'same-origin')
  assert.deepEqual(JSON.parse(booking.options.body), body)
  await logoutUser()
})
