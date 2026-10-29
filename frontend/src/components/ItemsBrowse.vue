<template>
  <section class="content-section">
    <div class="section-heading">
      <div>
        <p class="eyebrow">用户端 · 物品预约</p>
        <h1>可借物品</h1>
        <p class="section-sub">查看已上架物品。当前库存不代表某个预约时段的可用量，预约功能尚在对接。</p>
      </div>
      <button class="refresh-button" :disabled="loading" @click="loadItems">{{ loading ? '刷新中…' : '刷新' }}</button>
    </div>

    <div class="filter-bar">
      <input v-model="filters.keyword" maxlength="100" aria-label="搜索物品名称或介绍" type="search" placeholder="搜索物品名称或介绍" @input="applyFilters(true)">
      <select aria-label="按地点筛选" v-model="filters.locationId" @change="applyFilters()">
        <option value="">全部地点</option>
        <option v-for="loc in locations" :key="loc.id" :value="String(loc.id)">{{ loc.name }}</option>
      </select>
      <label class="filter-toggle">
        <input v-model="filters.onlyAvailable" type="checkbox" @change="applyFilters()"> 只看有库存
      </label>
    </div>

    <p v-if="locationsError" class="error-card" role="alert">地点列表加载失败：{{ locationsError }} <button class="link-button" @click="loadLocations">重试</button></p>
    <p v-if="loadError" class="state-card error-card" role="alert">{{ loadError }} <button class="link-button" @click="loadItems">重试</button></p>
    <p v-else-if="loading" class="state-card">正在加载物品…</p>
    <p v-else-if="visibleItems.length === 0" class="state-card">没有符合条件的物品。</p>

    <div v-else class="items-grid">
      <article v-for="item in visibleItems" :key="item.id" class="item-card">
        <div class="item-photo">
          <span aria-hidden="true">📦</span>
          <span v-if="(item.images || []).length > 0" class="photo-count">{{ item.images.length }} 张照片</span>
        </div>
        <div class="item-body">
          <div class="item-title-row">
            <h3>{{ item.name }}</h3>
            <span v-if="item.isValuable" class="status-badge badge-valuable">贵重</span>
          </div>
          <p class="item-location">{{ locationName(item.locationId) }}</p>
          <p class="item-rules">{{ borrowLimit(item) }} · {{ advanceLimit(item) }}</p>
          <div class="item-footer">
            <span :class="['stock-text', item.unitSummary.available > 0 ? '' : 'stock-out']">
              可借 {{ item.unitSummary.available }} / 共 {{ item.unitSummary.total }} 件
            </span>
            <button class="link-button" @click="openDetail(item)">查看详情</button>
          </div>
        </div>
      </article>
    </div>

    <nav class="section-heading" aria-label="物品分页">
      <button class="refresh-button" :disabled="loading || page === 0" @click="changePage(page - 1)">上一页</button>
      <span role="status">共 {{ total }} 件 · 第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <button class="refresh-button" :disabled="loading || (page + 1) * size >= total" @click="changePage(page + 1)">下一页</button>
    </nav>
    <div v-if="detail" class="modal-mask" @click.self="closeDetail()">
      <div class="modal-box" role="dialog" aria-modal="true" :aria-label="detail.name">
        <div class="modal-head">
          <h2>{{ detail.name }}</h2>
          <button class="link-button" @click="closeDetail()">关闭</button>
        </div>
        <p v-if="detailLoading" role="status">正在读取最新详情…</p>
        <p v-else-if="detailError" class="error-card" role="alert">{{ detailError }} <button class="link-button" @click="openDetail(detail)">重试</button></p>
        <template v-else>
        <div class="badge-row">
          <span v-if="detail.isValuable" class="status-badge badge-valuable">贵重 · 预约需审批</span>
          <span class="status-badge badge-normal">{{ locationName(detail.locationId) }}</span>
          <span :class="['status-badge', detail.unitSummary.available > 0 ? 'badge-ok' : 'badge-valuable']">
            可借 {{ detail.unitSummary.available }} / 共 {{ detail.unitSummary.total }} 件
          </span>
        </div>
        <p class="modal-desc">{{ detail.description || '暂无介绍。' }}</p>
        <div v-if="detail.images?.length">
          <button class="link-button" :disabled="photoLoading" @click="loadPhotos">{{ photoLoading ? '读取照片中…' : '查看照片（需登录）' }}</button>
          <p v-if="photoError" class="field-error">{{ photoError }}</p>
          <div class="photo-grid"><img v-for="photo in photos" :key="photo.url" :src="photo.url" :alt="photo.name" style="max-width:100%;max-height:220px"></div>
        </div>
        <dl class="rule-list">
          <div><dt>单次最长借用</dt><dd>{{ borrowLimit(detail) }}</dd></div>
          <div><dt>可提前预约</dt><dd>{{ advanceLimit(detail) }}</dd></div>
          <div><dt>归还要求</dt><dd>归还时需拍照，待管理员核验通过后才完成归还。</dd></div>
        </dl>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { getLocations, getPublicItems, getPublicItem, readItemImage } from '../lib/api'
const visibleItems = ref([])
const locations = ref([])
const loading = ref(false)
const loadError = ref('')
const locationsError = ref('')
const detail = ref(null)
const detailError = ref('')
const detailLoading = ref(false)
const photos = ref([])
const photoLoading = ref(false)
const photoError = ref('')
const page = ref(0)
const size = 12
const total = ref(0)
const filters = reactive({ keyword: '', locationId: '', onlyAvailable: false })
let listVersion = 0
let detailVersion = 0
let searchTimer
function locationName(id) { return locations.value.find((loc) => loc.id === id)?.name || `地点 ${id}` }
function borrowLimit(item) { return item.rules.maxBorrowDays == null ? '最长借用时长尚未配置' : `单次最长 ${item.rules.maxBorrowDays} 天` }
function advanceLimit(item) { return item.rules.advanceDays == null ? '提前预约规则尚未配置' : `最多提前 ${item.rules.advanceDays} 天预约` }
async function loadLocations() {
  try { locations.value = (await getLocations()).filter(loc => loc.status === 'ACTIVE'); locationsError.value = '' }
  catch (error) { locationsError.value = error.message }
}
async function loadItems() {
  clearTimeout(searchTimer)
  const version = ++listVersion
  loading.value = true
  loadError.value = ''
  try {
    const data = await getPublicItems({ ...filters, page: page.value, size })
    if (version !== listVersion) return
    visibleItems.value = data.content
    total.value = data.totalElements
    if (!data.content.length && page.value > 0) {
      page.value = Math.max(0, Math.ceil(total.value / size) - 1)
      return loadItems()
    }
  } catch (error) {
    if (version !== listVersion) return
    visibleItems.value = []; total.value = 0; loadError.value = error.message
  } finally { if (version === listVersion) loading.value = false }
}
function applyFilters(debounce = false) {
  page.value = 0
  ++listVersion
  clearTimeout(searchTimer)
  if (debounce === true) searchTimer = setTimeout(loadItems, 300)
  else loadItems()
}
function changePage(next) { page.value = next; loadItems() }
function clearPhotos() { for (const photo of photos.value) URL.revokeObjectURL(photo.url); photos.value = []; photoLoading.value = false; photoError.value = '' }
function closeDetail() { ++detailVersion; detail.value = null; detailError.value = ''; detailLoading.value = false; clearPhotos() }
async function loadPhotos() {
  clearPhotos()
  const version = detailVersion
  const item = detail.value
  photoLoading.value = true
  try {
    for (const image of item.images) {
      const blob = await readItemImage(item.id, image.id)
      if (version !== detailVersion) return
      photos.value.push({ name: image.fileName, url: URL.createObjectURL(blob) })
    }
  } catch (error) {
    if (version === detailVersion) { clearPhotos(); photoError.value = error.status === 401 ? '请先登录后查看照片。' : error.message }
  } finally { if (version === detailVersion) photoLoading.value = false }
}
async function openDetail(item) {
  clearPhotos()
  const version = ++detailVersion
  detail.value = item; detailLoading.value = true; detailError.value = ''
  try { const data = await getPublicItem(item.id); if (version === detailVersion) detail.value = data }
  catch (error) { if (version === detailVersion) detailError.value = error.message }
  finally { if (version === detailVersion) detailLoading.value = false }
}
function onKeydown(event) { if (event.key === 'Escape') closeDetail() }
onMounted(() => { loadLocations(); loadItems(); window.addEventListener('keydown', onKeydown) })
onUnmounted(() => { clearTimeout(searchTimer); ++listVersion; ++detailVersion; clearPhotos(); window.removeEventListener('keydown', onKeydown) })
</script>
