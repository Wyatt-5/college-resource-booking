import { test } from 'node:test'
import assert from 'node:assert/strict'
import { decide, createRunner, createTaskClient } from './tasks.mjs'
const serverTime = '2026-11-15T10:00:00Z'
const pending = { id: 1, type: 'EXPIRE_PENDING', leaseToken: 'lease', status: 'PENDING_APPROVAL', startAt: serverTime }
const overdue = { ...pending, id: 2, type: 'REMIND_OVERDUE', status: 'CONFIRMED', handoverStatus: 'CHECKED_OUT', endAt: serverTime }
test('expiry uses API server time and includes the exact start boundary', () => {
  assert.equal(decide(pending, serverTime).outcome, 'EXPIRE')
  assert.equal(decide({ ...pending, startAt: '2026-11-15T10:00:01Z' }, serverTime).outcome, 'SKIP')
  assert.equal(decide({ ...pending, status: 'CONFIRMED' }, serverTime).outcome, 'SKIP')
  assert.throws(() => decide(pending, 'bad'))
})
test('only checked out and rejected returns generate reminders', () => {
  for (const state of ['CHECKED_OUT', 'RETURN_REJECTED']) assert.equal(decide({ ...overdue, handoverStatus: state }, serverTime).outcome, 'REMIND')
  for (const state of ['NOT_PICKED_UP', 'RETURN_SUBMITTED', 'COMPLETED', null]) assert.equal(decide({ ...overdue, handoverStatus: state }, serverTime).outcome, 'SKIP')
  assert.equal(decide({ ...overdue, endAt: '2026-11-16T10:00:00Z' }, serverTime).outcome, 'SKIP')
})
test('lost completion response retries the identical lease and payload', async () => {
  const requests = []
  const api = createTaskClient({ baseUrl: 'http://localhost:8080', token: 'test-token-which-is-at-least-32-characters', fetchImpl: async (url, options) => {
    requests.push({ url: String(url), body: options.body })
    if (requests.length === 1) throw new Error('Lost response')
    return { ok: true, json: async () => ({ state: 'SUCCEEDED' }) }
  } })
  assert.equal((await api('/1/complete', { leaseToken: 'lease', outcome: 'EXPIRE' }, 2)).state, 'SUCCEEDED')
  assert.deepEqual(requests[0], requests[1])
})
test('authentication errors are not retried and an unobserved claim waits for lease recovery', async () => {
  let calls = 0
  const api = createTaskClient({ baseUrl: 'http://localhost:8080', token: 'test-token-which-is-at-least-32-characters', fetchImpl: async () => {
    calls++; return { ok: false, status: 401 }
  } })
  await assert.rejects(api('/1/complete', {}, 2)); assert.equal(calls, 1)
  calls = 0
  const claimApi = createTaskClient({ baseUrl: 'http://localhost:8080', token: 'test-token-which-is-at-least-32-characters', fetchImpl: async () => { calls++; throw new Error('offline') } })
  await assert.rejects(claimApi('/claim', {})); assert.equal(calls, 1)
  assert.throws(() => createTaskClient({ baseUrl: 'http://localhost', token: '' }))
})
test('one runner prevents overlapping polls and reports failures for bounded API retry', async () => {
  let release
  const waiting = new Promise(resolve => { release = resolve })
  const requests = []
  const run = createRunner(async (path, body) => {
    requests.push(path)
    if (path === '/claim') { await waiting; return { serverTime, tasks: [pending, overdue] } }
    if (path === '/1/complete') throw new Error('offline')
    return { state: path.endsWith('/complete') ? 'SUCCEEDED' : 'PENDING' }
  })
  const first = run()
  assert.deepEqual(await run(), { busy: true })
  release()
  assert.deepEqual(await first, { claimed: 2, succeeded: 1, skipped: 0, failed: 1 })
  assert.ok(requests.includes('/1/fail'))
})
