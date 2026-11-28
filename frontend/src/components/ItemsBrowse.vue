<template>
  <section class="content-section">
    <div class="section-heading">
      <div>

        <h1>物品预约</h1>
        <p class="section-sub">选择物品和借用时段，提交预约。</p>
        <button v-if="initialLocation" class="link-button" type="button" @click="emit('back-to-locations')">← 返回资源总览</button>
      </div>

    </div>

    <p v-if="bookingMessage" class="success-banner" role="status">{{ bookingMessage }}</p>
    <form class="advanced-search" role="search" aria-label="物品高级搜索" @submit.prevent="applyFilters">
      <div class="search-primary-row">
        <label class="advanced-field search-keyword"><span>关键词</span><input v-model="searchForm.keyword" aria-label="关键词" maxlength="100" type="search" placeholder="搜索物品名称或介绍"></label>
        <div class="search-actions">
          <button class="primary-button" type="submit">搜索</button>
          <button class="refresh-button" type="button" @click="resetFilters">重置</button>
        </div>
      </div>
      <div class="advanced-search-fields">
        <label class="advanced-field"><span>存放地点</span><select v-model="searchForm.locationId" aria-label="存放地点">
          <option value="">全部地点</option><option v-for="loc in locations" :key="loc.id" :value="String(loc.id)">{{ loc.name }}</option>
        </select></label>
        <label class="advanced-field"><span>物品类型</span><select v-model="searchForm.isValuable" aria-label="物品类型">
          <option value="">全部类型</option><option value="false">普通物品</option><option value="true">贵重物品</option>
        </select></label>
        <label class="advanced-field"><span>库存状态</span><select v-model="searchForm.onlyAvailable" aria-label="库存状态">
          <option :value="false">全部库存</option><option :value="true">当前有库存</option>
        </select></label>
        <label class="advanced-field"><span>排序</span><select v-model="searchForm.sort" aria-label="排序">
          <option value="CREATED_DESC">最新上架</option><option value="AVAILABLE_DESC">可借库存最多</option><option value="NAME_ASC">物品名称</option>
        </select></label>
      </div>
    </form>

    <p v-if="locationsError" class="error-card" role="alert">地点列表加载失败：{{ locationsError }} <button class="link-button" @click="loadLocations">重试</button></p>
    <p v-if="loadError" class="state-card error-card" role="alert">{{ loadError }} <button class="link-button" @click="loadItems">重试</button></p>
    <p v-else-if="loading" class="state-card">正在加载物品…</p>
    <p v-else-if="visibleItems.length === 0" class="state-card">没有符合条件的物品。</p>

    <div v-else class="items-grid">
      <article v-for="item in visibleItems" :key="item.id" class="item-card">
        <div class="item-photo">
          <img v-if="item.images?.length" class="item-cover" :src="`${item.images[0].url}?thumbnail=true`" loading="lazy" decoding="async" :alt="`${item.name}的展示照片`">
          <span v-else aria-hidden="true">📦</span>
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
            <button class="primary-button card-book-button" type="button" @click="openDetail(item)">
              预约物品
            </button>
          </div>
        </div>
      </article>
    </div>

    <nav class="pagination-bar" aria-label="物品分页">
      <button class="refresh-button" :disabled="loading || page === 0" @click="changePage(page - 1)">上一页</button>
      <span role="status">共 {{ total }} 件 · 第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <button class="refresh-button" :disabled="loading || (page + 1) * size >= total" @click="changePage(page + 1)">下一页</button>
    </nav>
    <BookingModal v-if="detail" :title="detail.name" @close="closeDetail">
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
          <div class="photo-grid"><img v-for="photo in detail.images" :key="photo.id" :src="photo.url" :alt="photo.fileName"></div>
        </div>
        <dl class="rule-list">
          <div><dt>单次最长借用</dt><dd>{{ borrowLimit(detail) }}</dd></div>
          <div><dt>可提前预约</dt><dd>{{ advanceLimit(detail) }}</dd></div>
          <div><dt>归还要求</dt><dd>归还时需拍照，待管理员核验通过后才完成归还。</dd></div>
        </dl>
        <ReservationForm :key="detail.id" :item="detail" :current-user="currentUser" @login-requested="emit('login-requested')" @created="onBookingCreated" />
        </template>
    </BookingModal>
  </section>
</template>

<script setup>
import { useReloadOnReturn } from '../lib/useReloadOnReturn'
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { getLocations, getPublicItems, getPublicItem } from '../lib/api'
import ReservationForm from '../features/user/ReservationForm.vue'
import BookingModal from './BookingModal.vue'
const props = defineProps({ currentUser: { type: Object, default: null }, initialLocation: { type: Object, default: null }, workspace: { type: String, default: 'user' } })
const emit = defineEmits(['login-requested', 'back-to-locations'])
const visibleItems = ref([])
const locations = ref([])
const loading = ref(false)
const loadError = ref('')
const locationsError = ref('')
const bookingMessage = ref('')
const detail = ref(null)
const detailError = ref('')
const detailLoading = ref(false)
const page = ref(0)
const size = 12
const total = ref(0)
const defaultFilters = () => ({ keyword: '', locationId: '', onlyAvailable: false, isValuable: '', sort: 'CREATED_DESC' })
const searchForm = reactive({ ...defaultFilters(), locationId: props.initialLocation ? String(props.initialLocation.id) : '' })
const filters = reactive({ ...searchForm })
let listVersion = 0
let detailVersion = 0
function locationName(id) { return locations.value.find((loc) => loc.id === id)?.name || '未标注存放地点' }
function borrowLimit(item) { return item.rules.maxBorrowDays == null ? '最长借用时长尚未配置' : `单次最长 ${item.rules.maxBorrowDays} 天` }
function advanceLimit(item) { return item.rules.advanceDays == null ? '提前预约规则尚未配置' : `最多提前 ${item.rules.advanceDays} 天预约` }
async function loadLocations() {
  try { locations.value = (await getLocations()).filter(loc => loc.status === 'ACTIVE'); locationsError.value = '' }
  catch (error) { locationsError.value = error.message }
}
async function loadItems() {
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
function applyFilters() {
  Object.assign(filters, searchForm, { keyword: searchForm.keyword.trim() })
  page.value = 0
  ++listVersion
  loadItems()
}
function resetFilters() {
  Object.assign(searchForm, defaultFilters())
  applyFilters()
}
watch(() => props.initialLocation?.id, id => {
  searchForm.locationId = id == null ? '' : String(id)
  applyFilters()
})
function changePage(next) { page.value = next; loadItems() }
function onBookingCreated(reservation) {
  closeDetail()
  bookingMessage.value = reservation.status === 'CONFIRMED'
    ? '预约成功，普通物品无需审核。可在“我的预约”查看。'
    : '预约申请已提交，等待审核。可在“我的预约”查看。'
  loadItems()
}
function closeDetail() { ++detailVersion; detail.value = null; detailError.value = ''; detailLoading.value = false }
async function openDetail(item) {
  bookingMessage.value = ''
  const version = ++detailVersion
  detail.value = item; detailLoading.value = true; detailError.value = ''
  try { const data = await getPublicItem(item.id); if (version === detailVersion) detail.value = data }
  catch (error) { if (version === detailVersion) detailError.value = error.message }
  finally { if (version === detailVersion) detailLoading.value = false }
}
function onKeydown(event) { if (event.key === 'Escape') closeDetail() }
onMounted(() => { loadLocations(); loadItems(); window.addEventListener('keydown', onKeydown) })
onUnmounted(() => { ++listVersion; ++detailVersion; window.removeEventListener('keydown', onKeydown) })
useReloadOnReturn(() => { loadLocations(); loadItems() })
</script>
