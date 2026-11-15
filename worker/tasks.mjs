export function decide(task, serverTime) {
  const now = Date.parse(serverTime)
  if (!Number.isFinite(now)) throw new Error('Invalid API server time')
  if (task.type === 'EXPIRE_PENDING') {
    if (!Number.isFinite(Date.parse(task.startAt))) throw new Error('Invalid reservation start time')
    return { outcome: task.status === 'PENDING_APPROVAL' && Date.parse(task.startAt) <= now ? 'EXPIRE' : 'SKIP' }
  }
  if (task.type === 'REMIND_OVERDUE') {
    if (!Number.isFinite(Date.parse(task.endAt))) throw new Error('Invalid reservation end time')
    if (task.status !== 'CONFIRMED' || !['CHECKED_OUT', 'RETURN_REJECTED'].includes(task.handoverStatus)
        || Date.parse(task.endAt) > now) return { outcome: 'SKIP' }
    return { outcome: 'REMIND', message: '借用已到期，请尽快提交归还材料；物品须经管理员核验后才恢复可借。' }
  }
  throw new Error('Unsupported task type')
}

export function createTaskClient({ baseUrl, token, fetchImpl = fetch }) {
  const base = new URL(baseUrl)
  if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password
      || base.pathname !== '/' || base.search || base.hash) throw new Error('TASK_API_URL must be an HTTP(S) origin')
  if (!token || token.length < 32) throw new Error('TASK_SERVICE_TOKEN must contain at least 32 characters')
  return async (path, body, attempts = 1) => {
    for (let attempt = 1; ; attempt++) {
      try {
        const response = await fetchImpl(new URL('/api/v1/internal/tasks' + path, base), {
          method: 'POST', redirect: 'error', signal: AbortSignal.timeout(10000),
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        })
        if (!response.ok) {
          const error = new Error(`Task API returned HTTP ${response.status}`)
          error.status = response.status
          throw error
        }
        return await response.json()
      } catch (error) {
        // Claim is not retried blindly: an unobserved claim is recovered by lease expiry.
        if (attempt >= attempts || (error.status && error.status < 500)) throw error
      }
    }
  }
}

export function createRunner(api) {
  let running = false
  return async () => {
    if (running) return { busy: true }
    running = true
    try {
      const batch = await api('/claim', { limit: 20 })
      const result = { claimed: batch.tasks.length, succeeded: 0, skipped: 0, failed: 0 }
      for (const task of batch.tasks) {
        try {
          const decision = decide(task, batch.serverTime)
          const completed = await api(`/${task.id}/complete`, { leaseToken: task.leaseToken, ...decision }, 2)
          if (completed.state === 'SUCCEEDED') result.succeeded++
          else result.skipped++
        } catch (error) {
          result.failed++
          await api(`/${task.id}/fail`, { leaseToken: task.leaseToken,
            reason: error.status ? `任务接口返回 HTTP ${error.status}` : '任务处理或网络请求失败' }, 2).catch(() => {})
        }
      }
      return result
    } finally { running = false }
  }
}
