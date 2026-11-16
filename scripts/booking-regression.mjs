// Run only against an isolated test environment. Credentials are read as data, never evaluated as shell code.
import assert from 'node:assert/strict'

if (process.env.E2E_ALLOW_WRITES !== 'true') throw new Error('Set E2E_ALLOW_WRITES=true for an isolated test environment')
function required(key) {
  const value = process.env[key]
  if (!value) throw new Error(`Missing ${key}`)
  return value
}
const base = new URL(process.env.E2E_BASE || 'http://127.0.0.1:8080/api/v1/')
if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new Error('Invalid E2E_BASE')
base.pathname = base.pathname.replace(/\/$/, '') + '/'
const spaceId = Number(required('E2E_SPACE_ID'))
if (!Number.isSafeInteger(spaceId) || spaceId <= 0) throw new Error('Invalid E2E_SPACE_ID')
const names = ['USER_A', 'USER_B', 'REVIEWER', 'ADMIN']
const accounts = Object.fromEntries(names.map(key => [key, {
  login: required(`E2E_${key}_LOGIN`), password: required(`E2E_${key}_PASSWORD`), cookies: new Map(),
}]))
assert.equal(new Set(Object.values(accounts).map(a => a.login)).size, 4, 'Use four different test accounts')
const runId = `booking-e2e-${Date.now()}`
const created = []
let checks = 0
function equal(actual, expected, message) { assert.equal(actual, expected, message); checks++ }
async function request(account, path, method = 'GET', payload, key) {
  const headers = { Accept: 'application/json', Cookie: [...account.cookies].map(([k, v]) => `${k}=${v}`).join('; ') }
  if (method !== 'GET') {
    const csrf = await request(account, 'auth/csrf')
    equal(csrf.status, 200, 'CSRF request')
    headers[csrf.data.headerName] = csrf.data.token
    // CSRF acquisition can create/rotate the session cookie.
    headers.Cookie = [...account.cookies].map(([k, v]) => `${k}=${v}`).join('; ')
  }
  if (payload !== undefined) headers['Content-Type'] = 'application/json'
  if (key) headers['Idempotency-Key'] = key
  const response = await fetch(new URL(path, base), { method, headers,
    body: payload === undefined ? undefined : JSON.stringify(payload), redirect: 'error', signal: AbortSignal.timeout(15000) })
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';', 1)[0], split = pair.indexOf('=')
    account.cookies.set(pair.slice(0, split), pair.slice(split + 1))
  }
  const body = await response.text()
  return { status: response.status, data: body ? JSON.parse(body) : null }
}
async function reserve(account, hour, suffix) {
  const startAt = `${date}T${String(hour).padStart(2, '0')}:00:00+08:00`
  const endAt = `${date}T${String(hour + 1).padStart(2, '0')}:00:00+08:00`
  const result = await request(account, 'reservations', 'POST', { resourceType: 'SPACE', resourceId: spaceId,
    startAt, endAt, purpose: `${runId}-${suffix}`, quantity: 1 }, `${runId}-${suffix}`)
  equal(result.status, 201, 'Reservation creation')
  created.push({ id: result.data.id, owner: account })
  equal(result.data.status, 'PENDING_APPROVAL', 'New space reservation waits for review')
  return result.data
}
const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' })
  .format(new Date(Date.now() + 86400000))
try {
  for (const account of Object.values(accounts)) {
    const result = await request(account, 'auth/login', 'POST', { loginName: account.login, password: account.password })
    equal(result.status, 200, 'Test account login')
  }
  const [a, b, reviewer, admin] = names.map(key => accounts[key])
  const slots = await request(a, `spaces/${spaceId}/slots?date=${date}`)
  equal(slots.status, 200, 'Space slots')
  const occupied = new Set(slots.data.map(slot => slot.hour))
  const free = Array.from({ length: 14 }, (_, i) => i + 8).filter(hour => !occupied.has(hour))
  assert.ok(free.length >= 2, 'Need two free hours in the configured test space')
  const first = await reserve(a, free[0], 'a')
  const second = await reserve(b, free[0], 'b')
  const self = await request(a, `reservations/${first.id}/review`, 'PATCH', { decision: 'APPROVE' })
  equal(self.status, 409, 'No self approval'); equal(self.data.code, 'SELF_APPROVAL_FORBIDDEN', 'Self approval code')
  equal((await request(reviewer, `reservations/${first.id}/review`, 'PATCH', { decision: 'APPROVE' })).status, 200, 'Reviewer can choose applicant A')
  const conflict = await request(reviewer, `reservations/${second.id}/review`, 'PATCH', { decision: 'APPROVE' })
  equal(conflict.status, 409, 'No double booking'); equal(conflict.data.code, 'RESOURCE_CONFLICT', 'Capacity conflict code')
  const repeated = await request(reviewer, `reservations/${first.id}/review`, 'PATCH', { decision: 'APPROVE' })
  equal(repeated.status, 409, 'No duplicate approval')
  const query = new URLSearchParams({ status: 'CONFIRMED', keyword: a.login, size: '100' })
  const log = await request(reviewer, `management/reservations?${query}`)
  equal(log.status, 200, 'Filtered log')
  const row = log.data.content.find(row => row.id === first.id)
  assert.ok(row, 'Created reservation appears in filtered logs'); checks++
  equal(row.decision, 'APPROVED', 'Recorded approval'); assert.ok(row.reviewerName); checks++
  equal((await request(reviewer, 'management/reservations?status=NOT_A_STATUS')).status, 400, 'Invalid log state rejected')
  equal((await request(a, 'management/reservations')).status, 403, 'Ordinary user cannot read management logs')
  const concurrent = await reserve(a, free[1], 'concurrent')
  const results = await Promise.all([reviewer, admin].map(actor => request(actor,
    `reservations/${concurrent.id}/review`, 'PATCH', { decision: 'APPROVE' })))
  assert.deepEqual(results.map(row => row.status).sort(), [200, 409]); checks++
  console.log(`PASS ${checks} checks (${runId}); cleanup cancels this run's future bookings`)
} finally {
  let failed = false
  for (const row of created.reverse()) {
    try { equal((await request(row.owner, `me/reservations/${row.id}`, 'DELETE')).status, 200, 'Cancel this run booking') }
    catch { failed = true; console.error(`Cleanup incomplete for test reservation ${row.id} (${runId})`) }
  }
  for (const account of Object.values(accounts)) {
    await request(account, 'auth/logout', 'POST').catch(() => {})
  }
  if (failed) process.exitCode = 1
}
