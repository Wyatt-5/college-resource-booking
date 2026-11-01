import { createServer } from 'node:http'

const host = process.env.WORKER_HOST || '127.0.0.1'
const port = Number(process.env.WORKER_PORT || 8091)
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('WORKER_PORT must be between 1 and 65535')
}

const server = createServer((request, response) => {
  const path = new URL(request.url, 'http://localhost').pathname
  response.setHeader('Content-Type', 'application/json; charset=utf-8')
  response.setHeader('Cache-Control', 'no-store')
  if (request.method === 'GET' && path === '/health') {
    response.writeHead(200)
    response.end(JSON.stringify({ status: 'UP', service: 'booking-worker', tasks: 'NOT_CONNECTED' }))
  } else if (request.method === 'POST' && path === '/jobs/run') {
    response.writeHead(503)
    response.end(JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE',
      message: '内部任务认证和领取接口尚未接入；未执行任何任务' }))
  } else {
    response.writeHead(404)
    response.end(JSON.stringify({ code: 'NOT_FOUND', message: '路径不存在' }))
  }
  process.stdout.write(JSON.stringify({ at: new Date().toISOString(), method: request.method,
    path, status: response.statusCode }) + '\n')
})

server.listen(port, host, () => {
  process.stdout.write(`booking-worker listening on http://${host}:${port}\n`)
})
