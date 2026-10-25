// Keep API paths relative to the website origin. Vite proxies /api during local development;
// production deployment must route /api to the backend as well.
const API_BASE = '/api/v1'

export async function getLocations() {
  let response
  try {
    response = await fetch(`${API_BASE}/locations`, {
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new Error('无法连接后端。请确认数据库和后端已启动。')
  }

  if (!response.ok) {
    throw new Error(`读取地点失败（HTTP ${response.status}）。`)
  }

  let locations
  try {
    locations = await response.json()
  } catch {
    throw new Error('后端返回的地点数据不是有效 JSON。')
  }

  if (!Array.isArray(locations)) {
    throw new Error('地点接口格式不符：应返回数组。')
  }

  return locations
}
