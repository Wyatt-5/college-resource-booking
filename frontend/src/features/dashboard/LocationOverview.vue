<template>
  <section class="location-overview" aria-labelledby="location-overview-title">
    <header class="location-overview-intro">
      <div>

        <h1 id="location-overview-title">存放地点</h1>
        <p>选择办公室或物资室，查看其中的物品并提交预约。</p>
      </div>
      <button class="primary-button" type="button" @click="emit('open-all')">浏览全部物品</button>
    </header>

    <div v-if="overview" class="location-overview-summary" aria-label="资源概况">
      <span><strong>{{ overview.totals.locations }}</strong> 个开放地点</span>
      <span><strong>{{ overview.totals.items }}</strong> 种已上架物品</span>
      <span><strong>{{ overview.totals.availableUnits }}</strong> 件当前可借实物</span>
    </div>

    <div class="section-heading location-overview-heading">
      <div>
        <p class="eyebrow">按地点查找</p>
        <h2>办公室与物资室</h2>
      </div>
      <button class="refresh-button" type="button" :disabled="loading" @click="load">{{ loading ? '读取中…' : '刷新' }}</button>
    </div>

    <p v-if="loading" class="state-card" role="status">正在读取存放地点…</p>
    <div v-else-if="error" class="state-card error-card" role="alert">{{ error }} <button class="link-button" type="button" @click="load">重试</button></div>
    <p v-else-if="!overview?.locations.length" class="state-card">暂时没有存放地点。</p>
    <div v-else class="location-overview-grid">
      <button v-for="location in overview.locations" :key="location.id" class="location-overview-card"
        type="button" :disabled="location.status !== 'ACTIVE'"
        :aria-label="`查看${location.name}的物品，${location.itemCount}种物品，${location.availableUnits}件当前可借`"
        @click="emit('open-location', location)">
        <img v-if="location.imageUrl" class="location-overview-photo" :src="location.imageUrl" :alt="`${location.name}照片`">
        <span v-else class="location-overview-icon" aria-hidden="true">{{ typeLabel(location.type).slice(0, 1) }}</span>
        <span class="location-overview-body">
          <span class="location-overview-type">{{ typeLabel(location.type) }}</span>
          <strong>{{ location.name }}</strong>
          <span class="location-overview-count">{{ location.itemCount }} 种物品 · {{ location.availableUnits }} 件当前可借</span>
        </span>
        <span class="location-overview-arrow" aria-hidden="true">→</span>
      </button>
    </div>
    <p class="location-overview-note">当前可借数量仅反映实物状态，具体预约时段的余量以提交时检查为准。</p>
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { getLocations, getPublicItems } from '../../lib/api'
import { loadResourceOverview } from './overview.js'

const emit = defineEmits(['open-location', 'open-all'])
const overview = ref(null)
const loading = ref(false)
const error = ref('')
let requestVersion = 0
const typeLabel = type => ({ OFFICE: '办公室', STORAGE: '物资室', STORAGE_ROOM: '物资室', MATERIAL_ROOM: '物资室' })[type] || '存放地点'
async function load() {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const result = await loadResourceOverview({ getLocations, getPublicItems })
    if (version === requestVersion) overview.value = result
  } catch (cause) {
    if (version === requestVersion) { overview.value = null; error.value = cause.message || '地点读取失败。' }
  } finally { if (version === requestVersion) loading.value = false }
}
onMounted(load)
onUnmounted(() => { ++requestVersion })
</script>