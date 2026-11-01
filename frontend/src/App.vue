<script setup>
import { onMounted, ref } from 'vue'
import { getLocations, getSessionAdmin, logoutAdmin } from './lib/api'
import AccountSettings from './components/AccountSettings.vue'
import AdminLogin from './components/AdminLogin.vue'
import ItemCreateForm from './components/ItemCreateForm.vue'
import ItemStockList from './components/ItemStockList.vue'
import ItemsBrowse from './components/ItemsBrowse.vue'
import ReturnVerification from './components/ReturnVerification.vue'

const sections = [
  { key: 'locations', label: '存放地点', group: 'user' },
  { key: 'items', label: '物品预约', group: 'user' },
  { key: 'rooms', label: '会议室预约', group: 'user' },
  { key: 'mine', label: '我的预约', group: 'user' },
  { key: 'itemCreate', label: '物品上架', group: 'admin' },
  { key: 'itemStock', label: '库存管理', group: 'admin' },
  { key: 'returnCheck', label: '归还核验', group: 'admin' },
  { key: 'account', label: '账号管理', group: 'admin' },
]

const groups = {
  user: { label: '用户端', caption: '资源预约' },
  admin: { label: '管理端', caption: '管理后台' },
}

const mode = ref('user')
const activeSection = ref('locations')
const adminUser = ref(getSessionAdmin())

function switchMode(next) {
  mode.value = next
  activeSection.value = sections.find((item) => item.group === next).key
}

function onLoggedIn(user) {
  adminUser.value = user
  activeSection.value = 'itemCreate'
}

function onLogout() {
  logoutAdmin()
  adminUser.value = null
  activeSection.value = 'itemCreate'
}
const locations = ref([])
const loading = ref(false)
const error = ref('')

const locationTypes = {
  OFFICE: '办公室',
  STORAGE: '物资室',
  STORAGE_ROOM: '物资室',
  MATERIAL_ROOM: '物资室',
}

const locationStatuses = {
  ACTIVE: '使用中',
  INACTIVE: '停用',
  AVAILABLE: '可用',
}

function typeLabel(type) {
  return locationTypes[type] ?? type ?? '未分类'
}

function statusLabel(status) {
  return locationStatuses[status] ?? status ?? '状态未设置'
}

async function loadLocations() {
  loading.value = true
  error.value = ''
  try {
    locations.value = await getLocations()
  } catch (cause) {
    locations.value = []
    error.value = cause instanceof Error ? cause.message : '读取地点失败。'
  } finally {
    loading.value = false
  }
}

onMounted(loadLocations)
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <div class="header-inner">
        <div class="brand" aria-label="学院共用资源预约与借还系统">
          <img class="brand-logo" src="/logo.png" alt="汕头大学数学与计算机学院">
          <div>
            <strong>共用资源预约与借还系统</strong>
            <small>数学与计算机学院</small>
          </div>
        </div>

        <div class="header-actions">
          <div v-if="mode === 'admin' && adminUser" class="admin-chip">
            <span>{{ adminUser.name }}</span>
            <button type="button" class="logout-button" @click="onLogout">退出</button>
          </div>
          <div class="mode-switch" role="tablist" aria-label="切换工作区">
          <button
            v-for="(group, key) in groups"
            :key="key"
            type="button"
            role="tab"
            class="mode-tab"
            :class="{ active: mode === key }"
            :aria-selected="mode === key"
            @click="switchMode(key)"
          >
            {{ group.label }}
          </button>
          </div>
        </div>
      </div>
    </header>

    <div class="layout" :class="`mode-${mode}`">
      <nav class="side-nav" aria-label="主导航">
        <p class="nav-caption">{{ groups[mode].caption }}</p>
        <button
          v-for="section in sections.filter((item) => item.group === mode)"
          :key="section.key"
          type="button"
          class="nav-button"
          :class="{ active: activeSection === section.key }"
          :aria-current="activeSection === section.key ? 'page' : undefined"
          :disabled="mode === 'admin' && !adminUser"
          @click="activeSection = section.key"
        >
          {{ section.label }}
        </button>
      </nav>

      <main class="main-content">
        <AdminLogin v-if="mode === 'admin' && !adminUser" @logged-in="onLoggedIn" />

        <template v-else-if="activeSection === 'locations'">
          <section class="hero">
            <div>
              <p class="eyebrow">资源目录</p>
              <h1>先找到物品存放的位置</h1>
              <p>办公室和物资室是各类共用物品的存放地点，确认位置后再预约物品。</p>
            </div>
            <div class="hero-decoration" aria-hidden="true">01<span>/ 04</span></div>
          </section>

          <section class="content-section" aria-labelledby="locations-title">
            <div class="section-heading">
              <div>
                <p class="eyebrow">存放地点一览</p>
                <h2 id="locations-title">存放地点</h2>
              </div>
              <button type="button" class="refresh-button" :disabled="loading" @click="loadLocations">
                {{ loading ? '读取中…' : '重新读取' }}
              </button>
            </div>

            <p v-if="loading" class="state-card" role="status">正在从后端读取地点…</p>
            <div v-else-if="error" class="state-card error-card" role="alert">
              <strong>暂时无法显示地点</strong>
              <p>{{ error }}</p>
              <button type="button" class="retry-button" @click="loadLocations">重试</button>
            </div>
            <p v-else-if="locations.length === 0" class="state-card">暂时没有存放地点。</p>
            <div v-else class="location-grid">
              <article v-for="location in locations" :key="location.id" class="location-card">
                <div class="location-icon" aria-hidden="true">{{ typeLabel(location.type).slice(0, 1) }}</div>
                <div class="location-copy">
                  <span class="location-type">{{ typeLabel(location.type) }}</span>
                  <h3>{{ location.name || `地点 ${location.id}` }}</h3>
                  <p>状态：{{ statusLabel(location.status) }}</p>
                </div>
              </article>
            </div>
          </section>
        </template>

        <ItemsBrowse v-else-if="activeSection === 'items'" />

        <ItemCreateForm v-else-if="activeSection === 'itemCreate'" />
        <ItemStockList v-else-if="activeSection === 'itemStock'" />
        <ReturnVerification v-else-if="activeSection === 'returnCheck'" />
        <AccountSettings
          v-else-if="activeSection === 'account'"
          @profile-changed="adminUser = $event"
          @password-changed="onLogout"
        />

        <section v-else class="placeholder" aria-live="polite">
          <p class="eyebrow">{{ groups[sections.find((item) => item.key === activeSection)?.group]?.label }}</p>
          <h1>{{ sections.find((item) => item.key === activeSection)?.label }}</h1>
          <div class="placeholder-box">暂无内容。</div>
        </section>

        <footer class="site-footer">学院共用资源预约与借还系统</footer>
      </main>
    </div>
  </div>
</template>
