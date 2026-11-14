<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getSessionAdmin, logoutAdmin, refreshAdminSession, MANAGEMENT_DEMO } from './lib/api'
import { nextTabAfterClose } from './lib/pageTabs.js'
import ItemStockList from './components/ItemStockList.vue'
import ItemsBrowse from './components/ItemsBrowse.vue'
import ReturnVerification from './components/ReturnVerification.vue'
import UserLogin from './features/user/UserLogin.vue'
import BookingModal from './components/BookingModal.vue'
import UserAvatar from './components/UserAvatar.vue'
import MyPage from './features/user/MyPage.vue'
import UserProvision from './features/admin/UserProvision.vue'
import RoomPrototype from './features/rooms/RoomPrototype.vue'
import SpaceManagement from './features/rooms/SpaceManagement.vue'
import ApprovalQueue from './features/approval/ApprovalQueue.vue'
import LocationOverview from './features/dashboard/LocationOverview.vue'
import Announcements from './features/communication/Announcements.vue'
import Feedback from './features/communication/Feedback.vue'

const sections = [
  { key: 'locations', label: '存放地点', group: 'user' },
  { key: 'items', label: '物品预约', group: 'user' },
  { key: 'rooms', label: '地点预约', group: 'user' },
  { key: 'mine', label: '我的', group: 'user' },
  { key: 'itemStock', label: '库存管理', group: 'admin' },
  { key: 'spaceManage', label: '地点管理', group: 'admin' },
  { key: 'returnCheck', label: '归还核验', group: 'admin' },
  { key: 'account', label: '账号管理', group: 'admin' },
  { key: 'approval', label: '审核工作台', group: 'admin' },
  { key: 'announcementAdmin', label: '公告管理', group: 'admin' },
  { key: 'feedbackAdmin', label: '用户反馈', group: 'admin' },
]

const groups = {
  user: { label: '用户端', caption: '资源预约' },
  admin: { label: '管理端', caption: '管理后台' },
}

const mode = ref('user')
const showLogin = ref(false)
const showAnnouncements = ref(false)
const loginBusy = ref(false)
const enableManagementDemo = MANAGEMENT_DEMO
const activeSection = ref('locations')
const selectedLocation = ref(null)
const adminUser = ref(getSessionAdmin())
const hasManagementRole = computed(() => adminUser.value?.roles?.some((role) =>
  ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER'].includes(role)))
const canManageItems = computed(() => adminUser.value?.roles?.some((role) =>
  ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER'].includes(role)))

const openTabs = ref([{ key: 'locations', label: '存放地点', group: 'user' }])
const lastSections = ref({ user: 'locations', admin: 'itemStock' })
const sessionGeneration = ref(0)
const pageComponents = {
  locations: LocationOverview, items: ItemsBrowse,
  rooms: RoomPrototype, mine: MyPage, itemStock: ItemStockList,
  spaceManage: SpaceManagement, returnCheck: ReturnVerification, account: UserProvision,
  approval: ApprovalQueue, announcementAdmin: Announcements, feedbackAdmin: Feedback,
}
const visibleTabs = computed(() => openTabs.value.filter(tab => tab.group === mode.value))
function ensureTab(key) {
  const section = sections.find(item => item.key === key)
  if (!section) return
  if (!openTabs.value.some(tab => tab.key === key)) openTabs.value.push(section)
  lastSections.value[section.group] = key
  activeSection.value = key
}
function switchMode(next) {
  mode.value = next
  const preferred = lastSections.value[next]
  ensureTab(next === 'admin' && !canManageItems.value ? 'approval' : preferred)
}
function openLocation(location) {
  selectedLocation.value = { id: location.id, name: location.name }
  ensureTab('items')
}
function openAllItems() {
  selectedLocation.value = null
  ensureTab('items')
}
function selectSection(key) {
  if (key === 'items') selectedLocation.value = null
  ensureTab(key)
}
function selectTab(key) { ensureTab(key) }
function closeTab(key) {
  const section = sections.find(item => item.key === key)
  if (!section) return
  const groupTabs = openTabs.value.filter(tab => tab.group === section.group)
  openTabs.value = openTabs.value.filter(tab => tab.key !== key)
  if (lastSections.value[section.group] === key) {
    lastSections.value[section.group] = nextTabAfterClose(groupTabs, key, section.group === 'user' ? 'locations' : 'itemStock')
  }
  if (activeSection.value === key) ensureTab(lastSections.value[section.group])
}
function pageProps(key) {
  if (key === 'items') return { currentUser: adminUser.value, initialLocation: selectedLocation.value }
  if (['mine', 'rooms', 'account', 'approval', 'feedbackAdmin'].includes(key)) return { currentUser: adminUser.value, ...(key === 'feedbackAdmin' ? { managed: true } : {}) }
  if (key === 'announcementAdmin') return { managed: true }
  return {}
}

function onLoggedIn(user) {
  adminUser.value = user
  showLogin.value = false
}

async function onLogout() {
  await logoutAdmin()
  adminUser.value = null
  mode.value = 'user'
  openTabs.value = [{ key: 'locations', label: '存放地点', group: 'user' }]
  lastSections.value = { user: 'locations', admin: 'itemStock' }
  selectedLocation.value = null
  sessionGeneration.value++
  activeSection.value = 'locations'
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

// 侧边导航滑动指示条：跟随当前激活项平滑移动
const navButtons = ref({})
const navIndicator = ref({ top: 0, height: 0, visible: false })
function syncNavIndicator() {
  const el = navButtons.value[activeSection.value]
  if (!el) { navIndicator.value.visible = false; return }
  // offsetTop 相对 offsetParent(.side-nav) 的 padding-box 顶部，与指示条 absolute 定位基准一致
  navIndicator.value = {
    top: el.offsetTop,
    height: el.offsetHeight,
    visible: true,
  }
}
watch([activeSection, mode, () => visibleTabs.value.length], () => nextTick(syncNavIndicator))
onMounted(() => { nextTick(syncNavIndicator); window.addEventListener('resize', syncNavIndicator) })
onUnmounted(() => window.removeEventListener('resize', syncNavIndicator))
</script>

<template>
  <div class="site-shell">
    <BookingModal v-if="showLogin" title="登录" compact disable-backdrop-close :busy="loginBusy" @close="showLogin = false"><UserLogin @busy-change="loginBusy = $event" @logged-in="onLoggedIn" /></BookingModal>
    <BookingModal v-if="showAnnouncements" title="公告栏" @close="showAnnouncements = false"><Announcements in-modal /></BookingModal>
    <header class="site-header">
      <div class="header-inner">
        <div class="brand" aria-label="汕头大学数学与计算机学院">
          <svg class="brand-filter" aria-hidden="true" width="0" height="0"><defs><filter id="emblem-transparent" color-interpolation-filters="sRGB"><feColorMatrix type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  -.3333 -.3333 -.3334 0 1"/><feComponentTransfer><feFuncA type="linear" slope="12" intercept="0"/></feComponentTransfer></filter></defs></svg>
          <span class="brand-logo" role="img" aria-label="汕头大学数学与计算机学院 Shantou University College of Mathematics and Computer Science">
            <img class="brand-wordmark-text" src="/college-wordmark.png" alt="" aria-hidden="true">
            <img class="brand-wordmark-emblem" src="/college-wordmark.png" alt="" aria-hidden="true">
            <img class="brand-wordmark-line" src="/college-wordmark.png" alt="" aria-hidden="true">
          </span>
        </div>
        <strong class="site-title">共用物品预约与借还系统</strong>

        <div class="header-actions">
          <button v-if="mode === 'user'" type="button" class="announcement-button" title="公告栏" aria-label="公告栏" :aria-expanded="showAnnouncements" @click="showAnnouncements = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-2 7-2 8h16c0-1-2-1-2-8M10 20h4"/></svg>
          </button>
          <div v-if="adminUser" class="admin-chip">
            <UserAvatar :user-id="adminUser.id" :name="adminUser.name" />
            <span>{{ adminUser.name }}</span>
            <button type="button" class="logout-button" @click="onLogout">退出</button>
          </div>
          <button v-else-if="mode === 'user'" type="button" class="logout-button" @click="showLogin = true">登录</button>
          <button v-if="hasManagementRole" type="button" class="logout-button workspace-toggle"
            @click="switchMode(mode === 'admin' ? 'user' : 'admin')">
            {{ mode === 'admin' ? '← 返回用户端' : '⚙ 管理工作台' }}
          </button>
        </div>
      </div>
    </header>

    <div class="layout" :class="`mode-${mode}`">
      <nav ref="navRef" class="side-nav" aria-label="主导航">
        <span class="nav-indicator" :style="{ top: `${navIndicator.top}px`, height: `${navIndicator.height}px`, opacity: navIndicator.visible ? 1 : 0 }" aria-hidden="true"></span>
        <p class="nav-caption">{{ groups[mode].caption }}</p>
        <button
          v-for="section in sections.filter((item) => item.group === mode && item.key !== 'announcements' && (item.key !== 'login' || !adminUser) && (item.group !== 'admin' || canManageItems || item.key === 'approval'))"
          :key="section.key"
          :ref="(el) => { navButtons[section.key] = el }"
          type="button"
          class="nav-button"
          :class="{ active: activeSection === section.key }"
          :aria-current="activeSection === section.key ? 'page' : undefined"
          :disabled="section.group === 'admin' && !hasManagementRole"
          @click="selectSection(section.key)"
        >
          {{ section.label }}
        </button>
      </nav>

      <main class="main-content">
        <div class="workspace-strip">
          <div class="page-tabs" role="tablist" :aria-label="mode === 'admin' ? '管理端已打开页面' : '用户端已打开页面'">
            <div v-for="tab in visibleTabs" :key="tab.key" class="page-tab" :class="{ active: activeSection === tab.key }">
              <button class="page-tab-label" type="button" role="tab" :aria-selected="activeSection === tab.key" @click="selectTab(tab.key)">{{ tab.label }}</button>
              <button class="page-tab-close" type="button" :aria-label="`关闭${tab.label}`" @click="closeTab(tab.key)">×</button>
            </div>
          </div>
        </div>
        <div class="workspace-page">
        <p v-if="mode === 'admin' && enableManagementDemo" class="notice" role="status">管理端演示：操作仅改变临时数据，刷新页面会重置，不会保存到数据库。</p>
        <template v-for="tab in openTabs" :key="`${sessionGeneration}-${tab.key}`">
          <Transition name="page" mode="out-in">
            <KeepAlive>
              <component v-if="tab.key === activeSection && tab.group === mode" :is="pageComponents[tab.key]" v-bind="pageProps(tab.key)"
                @open-location="openLocation" @open-all="openAllItems" @back-to-locations="selectSection('locations')"
                @login-requested="showLogin = true" @profile-changed="adminUser = $event" @password-changed="onLogout" />
            </KeepAlive>
          </Transition>
        </template>
        </div>
      </main>
    </div>
  </div>
</template>
