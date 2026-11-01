// Public catalog uses real HTTP only. Management demos have a separate switch.
export function createPublicCatalog({ fetchImpl = (...args) => fetch(...args), base = '/api/v1' } = {}) {
  async function get(path) {
    let response
    try {
      response = await fetchImpl(`${base}${path}`, { headers: { Accept: 'application/json' } })
    } catch {
      throw new Error('无法连接后端，请确认后端已启动后重试。')
    }
    const data = await response.json().catch(() => null)
    if (!response.ok) {
      const error = new Error(data?.message || `读取物品失败（HTTP ${response.status}）`)
      error.code = data?.code
      error.requestId = data?.requestId
      throw error
    }
    return data
  }
  return {
    async list({ locationId = '', keyword = '', onlyAvailable = false, page = 0, size = 12 } = {}) {
      const query = new URLSearchParams({ keyword, onlyAvailable: String(onlyAvailable), page: String(page), size: String(size) })
      if (locationId !== '') query.set('locationId', String(locationId))
      const data = await get(`/items?${query}`)
      if (!Array.isArray(data?.content) || !Number.isSafeInteger(data.totalElements)
          || !Number.isSafeInteger(data.page) || !Number.isSafeInteger(data.size)) {
        throw new Error('物品接口格式不符，请确认前后端使用相同接口版本。')
      }
      return data
    },
    detail(id) { return get(`/items/${encodeURIComponent(id)}`) },
  }
}
