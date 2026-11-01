// Keep API paths relative to the website origin. Vite proxies /api during local development;
// production deployment must route /api to the backend as well.
const API_BASE = '/api/v1'
import { createPublicCatalog } from './publicCatalog.js'
const publicCatalog = createPublicCatalog()

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
// 管理操作暂保留 PR #2 的模拟实现；App 仅在显式启用开发演示时展示。
// 用户端 getPublicItems/getPublicItem 始终调用真实接口，不受此开关影响。
// 管理接口和身份接口尚未全部实现，不得整体关闭开关并标记为联调完成。
// ---------------------------------------------------------------------------
export const MANAGEMENT_DEMO = import.meta.env?.DEV === true && import.meta.env?.VITE_ENABLE_MANAGEMENT_DEMO === 'true'
const USE_MOCK_ITEMS = MANAGEMENT_DEMO

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
// 物品照片记录：上架页上传后与物品关联，用户端按 photoIds 统计展示
const mockImages = []
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
    const error = apiFailure(data?.code, data?.message || `${fallbackMessage}（HTTP ${response.status}）`, data?.details)
    error.status = response.status
    throw error
  }
  return data
}

// 真实模式使用 HttpOnly 会话 Cookie；仅开发演示使用本地模拟令牌。
const SESSION_KEY = 'crb-admin-session'

let authToken = null
let csrfToken = null
let csrfHeaderName = 'X-CSRF-TOKEN'
function authHeaders() {
  return USE_MOCK_ITEMS && authToken ? { Authorization: `Bearer ${authToken}` } : {}
}

async function ensureCsrf() {
  if (csrfToken) return { [csrfHeaderName]: csrfToken }
  const response = await fetch(`${API_BASE}/auth/csrf`, { credentials: 'same-origin' })
  const data = await parseResponse(response, '获取安全令牌失败')
  if (!data?.token || !data?.headerName) throw new Error('安全令牌响应格式不符')
  csrfToken = data.token
  csrfHeaderName = data.headerName
  return { [csrfHeaderName]: csrfToken }
}

function adminFromProfile(profile) {
  const grants = profile.grants || []
  return {
    ...profile,
    account: profile.loginName,
    name: profile.displayName,
    roles: [...grants.map((grant) => grant.role), ...(profile.reviewer ? ['REVIEWER'] : [])],
    resourceScopes: grants.filter((grant) => grant.role === 'RESOURCE_MANAGER').map((grant) => grant.locationId),
  }
}

function saveSession(token, admin) {
  authToken = token
  currentAdmin = admin
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ token, admin: publicAdmin(admin) }))
  } catch {
    // 隐私模式等场景下存储不可用，退化为仅内存保持
  }
}

function restoreSession() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return
    const { token, admin } = JSON.parse(raw)
    if (token && admin) {
      authToken = token
      currentAdmin = admin
    }
  } catch {
    if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(SESSION_KEY)
  }
}

function getJson(path) {
  return fetch(`${API_BASE}${path}`, { headers: { Accept: 'application/json', ...authHeaders() } })
    .then((response) => parseResponse(response, '读取失败'))
}

async function postJson(path, body, idempotencyKey) {
  const csrf = await ensureCsrf()
  return fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      ...authHeaders(),
      ...csrf,
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  }).then((response) => parseResponse(response, '请求失败'))
}

async function patchJson(path, body) {
  const csrf = await ensureCsrf()
  return fetch(`${API_BASE}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeaders(), ...csrf },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  }).then((response) => parseResponse(response, '更新失败'))
}
async function putJson(path, body) {
  const csrf = await ensureCsrf()
  return fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeaders(), ...csrf },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  }).then((response) => parseResponse(response, '库存更新失败'))
}

function unitSummary(itemId) {
  const units = mockUnits.filter((unit) => unit.itemId === itemId)
  return {
    total: units.length,
    available: units.filter((unit) => unit.status === 'AVAILABLE').length,
  }
}

// 物品照片 id 列表（用户端卡片角标与库存页展示用）
function photoIds(itemId) {
  return mockImages.filter((image) => image.itemId === itemId).map((image) => image.id)
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
    for (let n = 0; n < (payload.initialStock || 0); n++) {
      mockUnitId += 1
      mockUnits.push({ id: mockUnitId, itemId: item.id, unitCode: `ITEM-${item.id}-${mockUnitId}`, status: 'AVAILABLE' })
    }
    item.unitSummary = unitSummary(item.id)
    return item
  }
  return postJson('/items', payload, idempotencyKey)
}

export async function uploadItemImage(itemId, file) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(500)
    mockImageId += 1
    const image = { id: mockImageId, itemId, fileName: file.name }
    mockImages.push(image)
    return image
  }
  const formData = new FormData()
  formData.append('file', file)
  const csrf = await ensureCsrf()
  const response = await fetch(`${API_BASE}/items/${itemId}/images`, {
    method: 'POST',
    headers: { Accept: 'application/json', ...authHeaders(), ...csrf },
    credentials: 'same-origin',
    body: formData,
  })
  return parseResponse(response, '照片上传失败')
}

export async function deleteItemImage(itemId, imageId) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(200)
    const index = mockImages.findIndex((image) => image.itemId === itemId && image.id === imageId)
    if (index !== -1) mockImages.splice(index, 1)
    return { deleted: true }
  }
  const csrf = await ensureCsrf()
  const response = await fetch(`${API_BASE}/items/${itemId}/images/${imageId}`, {
    method: 'DELETE',
    headers: { Accept: 'application/json', ...authHeaders(), ...csrf },
    credentials: 'same-origin',
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
export const getPublicItems = (filters) => publicCatalog.list(filters)
export const getPublicItem = (id) => publicCatalog.detail(id)
export const getMyReservations = ({ page = 0, size = 20 } = {}) =>
  getJson(`/me/reservations?page=${page}&size=${size}`)
export async function cancelMyReservation(id) {
  const csrf = await ensureCsrf()
  return fetch(`${API_BASE}/me/reservations/${id}`, { method:'DELETE',
    headers: { Accept:'application/json', ...authHeaders(), ...csrf }, credentials:'same-origin' })
    .then(response => parseResponse(response,'取消预约失败'))
}
export const getBookingLogs = ({ itemId, page = 0, size = 20 } = {}) =>
  getJson(`/management/reservations?page=${page}&size=${size}${itemId ? `&itemId=${itemId}` : ''}`)
export const updateItem = (id, payload) => patchJson(`/items/${id}`,payload)
export const createReservation = (payload, idempotencyKey) =>
  postJson('/reservations', payload, idempotencyKey)
export const getPendingReviews = ({ page = 0, size = 20 } = {}) =>
  getJson(`/review/reservations?page=${page}&size=${size}`)
export const reviewReservation = (reservationId, payload) =>
  patchJson(`/reservations/${reservationId}/review`, payload)
export const getSpaces = () => getJson('/spaces')
export const getSpaceSlots = (spaceId, date) => getJson(`/spaces/${spaceId}/slots?date=${encodeURIComponent(date)}`)
export const getItemAvailability = (itemId, date) => getJson(`/items/${itemId}/availability?date=${encodeURIComponent(date)}`)
export const createSpace = (payload) => postJson('/spaces', payload)
export async function uploadSpaceImage(spaceId, file) {
  const body = new FormData()
  body.append('file', file)
  const csrf = await ensureCsrf()
  const response = await fetch(`${API_BASE}/spaces/${spaceId}/image`, {
    method: 'POST', headers: { Accept: 'application/json', ...authHeaders(), ...csrf },
    credentials: 'same-origin', body,
  })
  return parseResponse(response, '地点照片上传失败')
}
export const getItemImages = (id) => getJson(`/items/${id}/images`)
export async function readItemImage(itemId, imageId) {
  const response = await fetch(`${API_BASE}/items/${itemId}/images/${imageId}`, { headers: authHeaders() })
  if (!response.ok) await parseResponse(response, '图片读取失败')
  return response.blob()
}

export async function getItems({ page = 0, size = 20 } = {}) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    const content = mockItems.slice(page * size, (page + 1) * size).map((item) => ({ ...item, unitSummary: unitSummary(item.id), photoIds: photoIds(item.id) }))
    return { content, page, size, totalElements: mockItems.length }
  }
  return getJson(`/items?scope=managed&page=${page}&size=${size}`)
}

export async function getItemUnits(itemId) {
  if (USE_MOCK_ITEMS) {
    await mockDelay(250)
    return mockUnits.filter((unit) => unit.itemId === itemId).map((unit) => ({ ...unit }))
  }
  return getJson(`/items/${itemId}/units`)
}
export async function setItemStock(itemId, quantity) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    const item = mockItems.find((entry) => entry.id === itemId)
    if (!item) throw apiFailure('NOT_FOUND', '物品不存在')
    if (!Number.isInteger(quantity) || quantity < 0 || quantity > 500) throw apiFailure('VALIDATION_ERROR', '请输入0至500的整数')
    const current = mockUnits.filter((unit) => unit.itemId === itemId)
    const removable = current.filter((unit) => unit.status === 'AVAILABLE').sort((a, b) => b.id - a.id)
    if (quantity < current.length && removable.length < current.length - quantity) throw apiFailure('STOCK_IN_USE', '部分实物不可移除')
    for (let n = current.length; n < quantity; n++) {
      mockUnitId += 1
      mockUnits.push({ id: mockUnitId, itemId, unitCode: `ITEM-${itemId}-${mockUnitId}`, status: 'AVAILABLE' })
    }
    for (const unit of removable.slice(0, Math.max(0, current.length - quantity))) mockUnits.splice(mockUnits.indexOf(unit), 1)
    return { ...item, unitSummary: unitSummary(itemId) }
  }
  return putJson(`/items/${itemId}/stock`, { quantity })
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

// 模块加载时从 sessionStorage 恢复登录态（须在 currentAdmin 声明之后调用）
restoreSession()

export function getSessionAdmin() {
  return currentAdmin ? publicAdmin(currentAdmin) : null
}

export const getSessionUser = getSessionAdmin
export const createUserAccount = (payload) => postJson('/users', payload)
export const getUserAccounts = () => getJson('/users')
export const setUserGrants = (userId, grants, reviewer) => putJson(`/users/${userId}/grants`, { grants, reviewer })

export async function loginAdmin(account, password) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    const found = mockAdmins.find((admin) => admin.account === account && admin.password === password)
    if (!found) throw apiFailure('UNAUTHORIZED', '账号或密码不正确')
    found.lastLoginAt = new Date().toISOString()
    saveSession('mock-token', found)
    return { token: 'mock-token', user: publicAdmin(found) }
  }
  const profile = await postJson('/auth/login', { loginName: account, password })
  if (!profile?.id || !profile?.loginName || !Array.isArray(profile.grants)) {
    throw new Error('登录响应格式不符，请核对账号模块接口。')
  }
  const user = adminFromProfile(profile)
  saveSession(null, user)
  return { user }

}

export async function loginUser(account, password) {
  const profile = await postJson('/auth/login', { loginName: account, password })
  if (!profile?.id || !profile?.loginName || !Array.isArray(profile.grants)) {
    throw new Error('登录响应格式不符，请核对账号模块接口。')
  }
  const user = adminFromProfile(profile)
  saveSession(null, user)
  return { user }
}

export async function logoutAdmin() {
  if (!USE_MOCK_ITEMS && currentAdmin) await postJson('/auth/logout', {})
  currentAdmin = null
  authToken = null
  csrfToken = null
  try { sessionStorage.removeItem(SESSION_KEY) } catch { /* storage unavailable */ }
}

export const logoutUser = logoutAdmin

export async function refreshAdminSession() {
  if (USE_MOCK_ITEMS) return getSessionAdmin()
  try {
    const profile = await getJson('/auth/me')
    currentAdmin = adminFromProfile(profile)
    return currentAdmin
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      currentAdmin = null
      return null
    }
    throw error
  }
}

export const refreshUserSession = refreshAdminSession

export async function updateMyProfile(input) {
  if (USE_MOCK_ITEMS) {
    await mockDelay()
    if (!currentAdmin) throw apiFailure('UNAUTHORIZED', '请先登录')
    Object.assign(currentAdmin, {
      name: input.name?.trim() || currentAdmin.name,
      email: input.email?.trim() || currentAdmin.email,
      phone: input.phone?.trim() || currentAdmin.phone,
    })
    saveSession(authToken || 'mock-token', currentAdmin)
    return publicAdmin(currentAdmin)
  }
  const data = await patchJson('/users/me', input)
  saveSession(authToken, data)
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

// 具有任一管理类角色即视为管理员（普通 USER 不进名单）
const ADMIN_ROLES = ['COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER', 'SYSTEM_ADMIN']

export async function getAdminList() {
  if (USE_MOCK_ITEMS) {
    await mockDelay(250)
    return mockAdmins.map(publicAdmin)
  }
  const users = await getJson('/users')
  return users.map(adminFromProfile).filter((user) => user.roles.some((role) => ADMIN_ROLES.includes(role)))
}
