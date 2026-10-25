<script setup>
import { onMounted, ref } from 'vue'
import { getLocations } from './lib/api'

const sections = [
  { key: 'locations', label: '存放地点', group: '用户端' },
  { key: 'items', label: '物品预约', group: '用户端' },
  { key: 'rooms', label: '会议室预约', group: '用户端' },
  { key: 'mine', label: '我的预约', group: '用户端' },
  { key: 'admin', label: '管理后台', group: '管理员端' },
]

const activeSection = ref('locations')
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
          <span class="brand-mark" aria-hidden="true">共</span>
          <div>
            <strong>学院共用资源</strong>
            <small>预约与借还系统</small>
          </div>
        </div>
        <span class="stage-tag">项目骨架 · 开发中</span>
      </div>
    </header>

    <div class="layout">
      <nav class="side-nav" aria-label="主导航">
        <p class="nav-caption">用户端</p>
        <button
          v-for="section in sections.filter((item) => item.group === '用户端')"
          :key="section.key"
          type="button"
          class="nav-button"
          :class="{ active: activeSection === section.key }"
          :aria-current="activeSection === section.key ? 'page' : undefined"
          @click="activeSection = section.key"
        >
          {{ section.label }}
        </button>
        <p class="nav-caption admin-caption">管理员端</p>
        <button
          type="button"
          class="nav-button"
          :class="{ active: activeSection === 'admin' }"
          :aria-current="activeSection === 'admin' ? 'page' : undefined"
          @click="activeSection = 'admin'"
        >
          管理后台
        </button>
        <p class="nav-note">登录和权限尚未实现，管理入口仅展示规划。</p>
      </nav>

      <main class="main-content">
        <div class="notice" role="status">
          当前只接通地点读取接口。预约、登录、审批、借还和管理操作尚未实现。
        </div>

        <template v-if="activeSection === 'locations'">
          <section class="hero">
            <div>
              <p class="eyebrow">资源目录 · 第一步</p>
              <h1>先找到物品存放的位置</h1>
              <p>这里直接读取后端的地点数据。办公室和物资室是存放地点；物品列表将在下一阶段接入。</p>
            </div>
            <div class="hero-decoration" aria-hidden="true">01<span>/ 04</span></div>
          </section>

          <section class="content-section" aria-labelledby="locations-title">
            <div class="section-heading">
              <div>
                <p class="eyebrow">真实接口 /api/v1/locations</p>
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
            <p v-else-if="locations.length === 0" class="state-card">地点表目前没有数据。请先检查后端迁移和初始数据。</p>
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

        <section v-else class="placeholder" aria-live="polite">
          <p class="eyebrow">待开发模块</p>
          <h1>{{ sections.find((item) => item.key === activeSection)?.label }}</h1>
          <p v-if="activeSection === 'admin'">管理员登录、资源维护、审批和日志功能尚未实现。后续必须由后端鉴权，不能仅靠隐藏页面控制权限。</p>
          <p v-else-if="activeSection === 'items'">物品列表、预约、确认取件和拍照归还接口尚未实现。</p>
          <p v-else-if="activeSection === 'rooms'">会议室日历、预约冲突检查和辅导员优先审核尚未实现。</p>
          <p v-else>登录和“我的预约”接口尚未实现。</p>
          <div class="placeholder-box">组员可在这里接入各自负责的页面与接口。</div>
        </section>

        <footer class="site-footer">学院共用资源预约与借还系统 · 开发骨架</footer>
      </main>
    </div>
  </div>
</template>
