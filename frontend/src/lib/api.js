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

// ---------------------------------------------------------------------------
// 物品接口（3 号后端尚未实现）。USE_MOCK_ITEMS = true 时按接口契约返回模拟
// 响应，字段与 docs/开发约束.md 5.2 / 5.4 保持一致；后端联调通过后改为
// false 即可切换到真实接口，页面代码无需改动。
// ---------------------------------------------------------------------------
const USE_MOCK_ITEMS = true

// 后端未启动时的演示兜底地点，仅供管理页面在 mock 阶段展示
export const FALLBACK_LOCATIONS = [
  { id: 1, name: '办公室 A', type: 'OFFICE', status: 'ACTIVE' },
  { id: 2, name: '物资室', type: 'STORAGE', status: 'ACTIVE' },
]

// 会话内 mock 数据仓库：上架、库存、归还核验三个页面共享同一份数据
const mockItems = [
  {
    id: 1,
    name: '佳能相机 EOS R50',
    locationId: 2,
    description: '含 18-45mm 套头、两块电池与相机包。',
    isValuable: true,
    status: 'ACTIVE',
    rules: { maxBorrowDays: 7, advanceDays: 14 },
    createdAt: '2026-10-20T09:00:00+08:00',
  },
  {
    id: 2,
    name: '博世电钻 GSB 120',
    locationId: 2,
    description: '含常用批头一套，借用前请检查电量。',
    isValuable: false,
    status: 'ACTIVE',
    rules: { maxBorrowDays: 3, advanceDays: 7 },
    createdAt: '2026-10-21T10:30:00+08:00',
  },
]
const mockUnits = [
  { id: 1, itemId: 1, unitCode: 'CAM-2026-001', status: 'AVAILABLE' },
  { id: 2, itemId: 1, unitCode: 'CAM-2026-002', status: 'MAINTENANCE' },
  { id: 3, itemId: 2, unitCode: 'DRL-2026-001', status: 'AVAILABLE' },
]
const mockReturns = [
  {
    id: 501,
    itemId: 1,
    itemName: '佳能相机 EOS R50',
    unitCode: 'CAM-2026-001',
    borrower: '王同学',
    submittedAt: '2026-10-25T16:20:00+08:00',
    remark: '镜头盖有一道划痕，机身其他部位完好。',
    photos: [{ id: 1, fileName: '机身正面.jpg' }, { id: 2, fileName: '镜头盖划痕.jpg' }],
  },
  {
    id: 502,
    itemId: 2,
    itemName: '博世电钻 GSB 120',
    unitCode: 'DRL-2026-001',
    borrower: '刘同学',
    submittedAt: '2026-10-26T09:05:00+08:00',
    remark: '',
    photos: [{ id: 3, fileName: '电钻整体.jpg' }],
  },
]

let mockItemId = 2
let mockImageId = 3
let mockUnitId = 3

function mockDelay(ms = 350) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function apiFailure(code, message, details = {}) {
  const error = new Error(message)
  error.code = code
  error.details = details
  return error
}

async function parseResponse(response, fallbackMessage) {
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw apiFailure(data?.code, data?.message || `${fallbackMessage}（HTTP ${response.status}）`, data?.details)
  }
  return data
}

// 登录令牌：真实模式下登录成功后写入，后续请求自动携带
let authToken = null
function authHeaders() {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {}
}

function getJson(path) {
  return fetch(`${API_BASE}${path}`, { headers: { Accept: 'application/json', ...authHeaders() } })
    .then((response) => parseResponse(response, '读取失败'))
}

function postJson(path, body, idempotencyKey) {
  return fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  }).then((response) => parseResponse(response, '请求失败'))
}

function patchJson(path, body) {
  return fetch(`${API_BASE}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  }).then((response) => parseResponse(response, '更新失败'))
}

function unitSummary(itemId) {
  const units = mockUnits.filter((unit) => unit.itemId === itemId)
  return {
    total: units.length,
    available: units.filter((unit) => unit.status === 'AVAILABLE').length,
  }
}

// ---- 上架（4 号页面：ItemCreateForm） ----

export async function createItem(payload, { idempotencyKey } = {}) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    if (!payload.name?.trim()) {
      throw apiFailure('VALIDATION_ERROR', '物品名称不能为空', { name: '必填' })
    }
    if (!payload.locationId) {
      throw apiFailure('VALIDATION_ERROR', '请选择归属地点', { locationId: '必填' })
    }
    mockItemId += 1
    const item = { id: mockItemId, ...payload, createdAt: new Date().toISOString() }
    mockItems.push(item)
    return item
  }
  return postJson('/items', payload, idempotencyKey)
}

export async function uploadItemImage(itemId, file) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(500)
    mockImageId += 1
    return { id: mockImageId, itemId, fileName: file.name }
  }
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${API_BASE}/items/${itemId}/images`, {
    method: 'POST',
    headers: { Accept: 'application/json', ...authHeaders() },
    body: formData,
  })
  return parseResponse(response, '照片上传失败')
}

export async function deleteItemImage(itemId, imageId) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(200)
    return { deleted: true }
  }
  const response = await fetch(`${API_BASE}/items/${itemId}/images/${imageId}`, {
    method: 'DELETE',
    headers: { Accept: 'application/json', ...authHeaders() },
  })
  if (!response.ok) {
    await parseResponse(response, '移除照片失败')
  }
  return { deleted: true }
}

export async function createItemUnit(itemId, unitCode) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    if (!unitCode?.trim()) {
      throw apiFailure('VALIDATION_ERROR', '实物编号不能为空', { unitCode: '必填' })
    }
    if (mockUnits.some((unit) => unit.itemId === itemId && unit.unitCode === unitCode.trim())) {
      throw apiFailure('VALIDATION_ERROR', '该编号已存在', { unitCode: '与其他实物重复' })
    }
    mockUnitId += 1
    const unit = { id: mockUnitId, itemId, unitCode: unitCode.trim(), status: 'AVAILABLE' }
    mockUnits.push(unit)
    return unit
  }
  return postJson(`/items/${itemId}/units`, { unitCode })
}

// ---- 库存管理（4 号页面：ItemStockList） ----

// 用户端浏览：只看启用中的物品
export async function getPublicItems() {
  if (USE_MOCK_ITEMS) {
    return mockItems
      .filter((item) => item.status === 'ACTIVE')
      .map((item) => ({ ...item, unitSummary: unitSummary(item.id) }));
  }
  return getJson('/items?status=ACTIVE')
}

export async function getItems() {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    return mockItems.map((item) => ({ ...item, unitSummary: unitSummary(item.id) }))
  }
  return getJson('/items')
}

export async function getItemUnits(itemId) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(250)
    return mockUnits.filter((unit) => unit.itemId === itemId).map((unit) => ({ ...unit }))
  }
  return getJson(`/items/${itemId}/units`)
}

export async function updateItemUnit(itemId, unitId, patch) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(250)
    const unit = mockUnits.find((entry) => entry.itemId === itemId && entry.id === unitId)
    if (!unit) throw apiFailure('NOT_FOUND', '实物不存在')
    Object.assign(unit, patch)
    return { ...unit }
  }
  return patchJson(`/items/${itemId}/units/${unitId}`, patch)
}

// ---- 归还核验（4 号页面：ReturnVerification） ----

export async function getReturnVerifications() {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    return mockReturns.map((entry) => ({ ...entry, status: 'RETURN_SUBMITTED' }))
  }
  return getJson('/reservations?status=RETURN_SUBMITTED')
}

export async function submitReturnVerification(reservationId, { decision, reason }, { idempotencyKey } = {}) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    const index = mockReturns.findIndex((entry) => entry.id === reservationId)
    if (index === -1) throw apiFailure('NOT_FOUND', '该归还申请不存在或已被处理')
    if (decision === 'REJECT' && !reason?.trim()) {
      throw apiFailure('VALIDATION_ERROR', '驳回时必须填写原因', { reason: '必填' })
    }
    const [submission] = mockReturns.splice(index, 1)
    if (decision === 'ACCEPT') {
      // 核验通过后实物重新可借
      const unit = mockUnits.find((entry) => entry.unitCode === submission.unitCode)
      if (unit) unit.status = 'AVAILABLE'
    }
    return { id: submission.id, status: decision === 'ACCEPT' ? 'COMPLETED' : 'RETURN_REJECTED' }
  }
  return postJson(`/reservations/${reservationId}/return-verification`, { decision, reason }, idempotencyKey)
}

// ---------------------------------------------------------------------------
// 管理员登录与账号管理（1 号后端尚未实现）。mock 演示账号：admin / admin123。
// ---------------------------------------------------------------------------
const mockAdmins = [
  {
    id: 1,
    account: 'admin',
    password: 'admin123',
    name: '陈老师',
    roles: ['COUNSELOR_ADMIN'],
    resourceScopes: [1, 2],
    department: '学院办公室',
    email: 'chen@mcs.stu.edu.cn',
    phone: '13800000001',
    joinedAt: '2024-09-01',
    lastLoginAt: '2026-10-26T17:30:00+08:00',
  },
  {
    id: 2,
    account: 'equipment',
    password: 'equip123',
    name: '林老师',
    roles: ['RESOURCE_MANAGER', 'REVIEWER'],
    resourceScopes: [2],
    department: '实验中心',
    email: 'lin@mcs.stu.edu.cn',
    phone: '13800000002',
    joinedAt: '2025-03-15',
    lastLoginAt: '2026-10-25T09:12:00+08:00',
  },
]

let currentAdmin = null

function publicAdmin(admin) {
  if (!admin) return null
  const { password: _password, ...profile } = admin
  return profile
}

export function getSessionAdmin() {
  return currentAdmin ? publicAdmin(currentAdmin) : null
}

export async function loginAdmin(account, password) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    const found = mockAdmins.find((admin) => admin.account === account && admin.password === password)
    if (!found) throw apiFailure('UNAUTHORIZED', '账号或密码不正确')
    found.lastLoginAt = new Date().toISOString()
    currentAdmin = found
    return { token: 'mock-token', user: publicAdmin(found) }
  }
  const data = await postJson('/auth/login', { account, password })
  authToken = data.token
  currentAdmin = data.user
  return data
}

export function logoutAdmin() {
  currentAdmin = null
  authToken = null
}

export async function updateMyProfile(input) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    if (!currentAdmin) throw apiFailure('UNAUTHORIZED', '请先登录')
    Object.assign(currentAdmin, {
      name: input.name?.trim() || currentAdmin.name,
      email: input.email?.trim() || currentAdmin.email,
      phone: input.phone?.trim() || currentAdmin.phone,
    })
    return publicAdmin(currentAdmin)
  }
  const data = await patchJson('/users/me', input)
  currentAdmin = data
  return data
}

export async function changeMyPassword({ oldPassword, newPassword }) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    if (!currentAdmin) throw apiFailure('UNAUTHORIZED', '请先登录')
    if (currentAdmin.password !== oldPassword) {
      throw apiFailure('VALIDATION_ERROR', '旧密码不正确', { oldPassword: '不正确' })
    }
    currentAdmin.password = newPassword
    return { changed: true }
  }
  return postJson('/users/me/password', { oldPassword, newPassword })
}

export async function getAdminList() {
  if (USE_MOCK_ITEMS) {
    await mockDelay(250)
    return mockAdmins.map(publicAdmin)
  }
  return getJson('/users')
}
