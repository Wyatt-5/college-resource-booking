import { createServer } from 'node:http'
import { timingSafeEqual } from 'node:crypto'
import { createTaskClient, createRunner } from './tasks.mjs'

const host = process.env.WORKER_HOST || '127.0.0.1'
const port = Number(process.env.WORKER_PORT || 8091)
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('WORKER_PORT must be between 1 and 65535')
}

const interval = Number(process.env.WORKER_POLL_MS || 30000)
if (!Number.isInteger(interval) || interval < 1000 || interval > 3600000) throw new Error('Invalid WORKER_POLL_MS')
const token = process.env.TASK_SERVICE_TOKEN || ''
const api = createTaskClient({ baseUrl: process.env.TASK_API_URL || 'http://127.0.0.1:8080', token })
const run = createRunner(api)
let lastRun = null, lastSuccess = null, lastResult = null, lastError = null
async function cycle() {
  try {
    const result = await run()
    if (result.busy) return result
    lastRun = new Date().toISOString()
    lastResult = result
    lastError = result.failed ? '部分任务失败，将按任务重试规则处理' : null
    if (!lastError) lastSuccess = lastRun
    process.stdout.write(JSON.stringify({ at: lastRun, ...result }) + '\n')
    return result
  } catch (error) {
    lastRun = new Date().toISOString()
    lastError = error.status ? `任务接口返回 HTTP ${error.status}` : '无法连接任务接口'
    process.stderr.write(JSON.stringify({ at: lastRun, error: lastError }) + '\n')
    throw error
  }
}
function authorized(request) {
  const expected = Buffer.from('Bearer ' + token)
  const supplied = Buffer.from(request.headers.authorization || '')
  return expected.length === supplied.length && timingSafeEqual(expected, supplied)
}
const server = createServer(async (request, response) => {
  const path = new URL(request.url, 'http://localhost').pathname
  response.setHeader('Content-Type', 'application/json; charset=utf-8')
  response.setHeader('Cache-Control', 'no-store')
  if (request.method === 'GET' && path === '/health') {
    response.writeHead(lastSuccess && !lastError && Date.now() - Date.parse(lastSuccess) < interval * 3 + 60000 ? 200 : 503)
    response.end(JSON.stringify({ service: 'booking-worker', status: response.statusCode === 200 ? 'UP' : 'DEGRADED',
      lastRun, lastSuccess, lastResult, lastError }))
  } else if (request.method === 'POST' && path === '/jobs/run') {
    if (!authorized(request)) { response.writeHead(401); response.end(JSON.stringify({ code: 'UNAUTHORIZED' })); return }
    try { const result = await cycle(); response.writeHead(result.busy ? 409 : 200); response.end(JSON.stringify(result)) }
    catch { response.writeHead(503); response.end(JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE' })) }
  } else {
    response.writeHead(404)
    response.end(JSON.stringify({ code: 'NOT_FOUND', message: '路径不存在' }))
  }
})

const timer = setInterval(() => { cycle().catch(() => {}) }, interval)
server.listen(port, host, () => {
  process.stdout.write(`booking-worker listening on http://${host}:${port}\n`)
  cycle().catch(() => {})
})
function shutdown() { clearInterval(timer); server.close(); setTimeout(() => process.exit(0), 25000).unref() }
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)
