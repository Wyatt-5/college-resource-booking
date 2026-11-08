<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { getSessionAdmin, logoutAdmin, refreshAdminSession, MANAGEMENT_DEMO } from './lib/api'
import AccountSettings from './components/AccountSettings.vue'
import AdminLogin from './components/AdminLogin.vue'
import ItemStockList from './components/ItemStockList.vue'
import ItemsBrowse from './components/ItemsBrowse.vue'
import ReturnVerification from './components/ReturnVerification.vue'
import UserLogin from './features/user/UserLogin.vue'
import MyReservations from './features/user/MyReservations.vue'
import UserProvision from './features/admin/UserProvision.vue'
import RoomPrototype from './features/rooms/RoomPrototype.vue'
import SpaceManagement from './features/rooms/SpaceManagement.vue'
import ApprovalQueue from './features/approval/ApprovalQueue.vue'
import LocationOverview from './features/dashboard/LocationOverview.vue'
import BookingLog from './features/admin/BookingLog.vue'

const sections = [
  { key: 'locations', label: '存放地点', group: 'user' },
  { key: 'items', label: '物品预约', group: 'user' },
  { key: 'rooms', label: '地点预约', group: 'user' },
  { key: 'mine', label: '我的预约', group: 'user' },
  { key: 'login', label: '登录', group: 'user' },
  { key: 'itemStock', label: '库存管理', group: 'admin' },
  { key: 'spaceManage', label: '地点管理', group: 'admin' },
  { key: 'returnCheck', label: '归还核验', group: 'admin' },
  { key: 'account', label: '账号管理', group: 'admin' },
  { key: 'userProvision', label: '账号与权限', group: 'admin' },
  { key: 'bookAsUser', label: '预约物品', group: 'admin' },
  { key: 'approval', label: '审核工作台', group: 'admin' },
  { key: 'bookingLog', label: '预约日志', group: 'admin' },
]

const groups = {
  user: { label: '用户端', caption: '资源预约' },
  admin: { label: '管理端', caption: '管理后台' },
}

const mode = ref('user')
const enableManagementDemo = MANAGEMENT_DEMO
const activeSection = ref('locations')
const selectedLocation = ref(null)
const adminUser = ref(getSessionAdmin())
const hasManagementRole = computed(() => adminUser.value?.roles?.some((role) =>
  ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER'].includes(role)))
const canManageItems = computed(() => adminUser.value?.roles?.some((role) =>
  ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER'].includes(role)))

function switchMode(next) {
  mode.value = next
  selectedLocation.value = null
  activeSection.value = next === 'admin' && !canManageItems.value ? 'approval' : sections.find((item) => item.group === next).key
}

function openLocation(location) {
  selectedLocation.value = { id: location.id, name: location.name }
  activeSection.value = 'items'
}

function openAllItems() {
  selectedLocation.value = null
  activeSection.value = 'items'
}

function selectSection(key) {
  if (key === 'items') selectedLocation.value = null
  activeSection.value = key
}

function onLoggedIn(user) {
  adminUser.value = user
  activeSection.value = mode.value === 'user' ? 'items' : (canManageItems.value ? 'itemStock' : 'approval')
}

async function onLogout() {
  await logoutAdmin()
  adminUser.value = null
  activeSection.value = mode.value === 'user' ? 'locations' : 'itemStock'
}
onMounted(() => {
  if (!enableManagementDemo) refreshAdminSession().then((user) => { adminUser.value = user }).catch(() => { adminUser.value = null })
})

// 手机上主导航是一条横向滑动的标签栏：切换模块后把当前标签滑进可视范围，
// 避免点了导航却看不到自己所在的页面。
const navRef = ref(null)
function keepActiveNavVisible() {
  const nav = navRef.value
  if (!nav || nav.scrollWidth <= nav.clientWidth + 1) return
  const active = nav.querySelector('.nav-button.active')
  if (!active) return
  const navBox = nav.getBoundingClientRect()
  const activeBox = active.getBoundingClientRect()
  const margin = 14
  if (activeBox.left < navBox.left + margin) {
    nav.scrollLeft -= navBox.left + margin - activeBox.left
  } else if (activeBox.right > navBox.right - margin) {
    nav.scrollLeft += activeBox.right - navBox.right + margin
  }
}
watch([activeSection, mode], () => nextTick(keepActiveNavVisible))
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
          <div v-if="adminUser" class="admin-chip">
            <span>{{ adminUser.name }}</span>
            <button type="button" class="logout-button" @click="onLogout">退出</button>
          </div>
          <button v-else-if="mode === 'user'" type="button" class="logout-button" @click="activeSection = 'login'">登录</button>
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
      <nav ref="navRef" class="side-nav" aria-label="主导航">
        <p class="nav-caption">{{ groups[mode].caption }}</p>
        <button
          v-for="section in sections.filter((item) => item.group === mode && (item.key !== 'login' || !adminUser) && (item.key !== 'userProvision' || adminUser?.roles?.includes('SYSTEM_ADMIN')) && (item.group !== 'admin' || canManageItems || ['approval', 'bookAsUser'].includes(item.key)))"
          :key="section.key"
          type="button"
          class="nav-button"
          :class="{ active: activeSection === section.key }"
          :aria-current="activeSection === section.key ? 'page' : undefined"
          :disabled="mode === 'admin' && !hasManagementRole"
          @click="selectSection(section.key)"
        >
          {{ section.label }}
        </button>
      </nav>

      <main class="main-content">
        <template v-if="mode === 'admin' && !hasManagementRole">
          <p v-if="enableManagementDemo" class="notice" role="status">管理端演示：使用模拟账号和临时数据，操作不会保存到数据库。</p>
          <p v-else class="notice" role="status">请使用真实账号登录。登录后才能使用管理功能；系统不会自动切换模拟账号。</p>
          <AdminLogin @logged-in="onLoggedIn" />
        </template>

        <LocationOverview v-else-if="activeSection === 'locations'" @open-location="openLocation" @open-all="openAllItems" />

        <ItemsBrowse v-else-if="activeSection === 'items'" :current-user="adminUser" :initial-location="selectedLocation" @login-requested="activeSection = 'login'" @back-to-locations="activeSection = 'locations'" />
        <MyReservations v-else-if="activeSection === 'mine'" :current-user="adminUser" @login-requested="activeSection = 'login'" />
        <RoomPrototype v-else-if="activeSection === 'rooms'" :current-user="adminUser" @login-requested="activeSection = 'login'" />
        <UserLogin v-else-if="activeSection === 'login'" @logged-in="onLoggedIn" />

        <template v-else-if="mode === 'admin'">
          <p v-if="enableManagementDemo" class="notice" role="status">管理端演示：操作仅改变临时数据，刷新页面会重置，不会保存到数据库。</p>
          <ItemStockList v-if="activeSection === 'itemStock'" />
          <ItemsBrowse v-else-if="activeSection === 'bookAsUser'" workspace="admin" :current-user="adminUser" @login-requested="onLogout" />
          <SpaceManagement v-else-if="activeSection === 'spaceManage'" />
          <template v-else-if="activeSection === 'returnCheck'">
            <ReturnVerification />
          </template>
          <AccountSettings v-else-if="activeSection === 'account'" @profile-changed="adminUser = $event" @password-changed="onLogout" />
          <UserProvision v-else-if="activeSection === 'userProvision' && adminUser?.roles?.includes('SYSTEM_ADMIN')" :current-user="adminUser" />
          <ApprovalQueue v-else-if="activeSection === 'approval'" :current-user="adminUser" />
          <BookingLog v-else-if="activeSection === 'bookingLog'" />
        </template>

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
