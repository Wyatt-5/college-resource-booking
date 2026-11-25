<script setup>
import { computed, onMounted, ref } from 'vue'
import { getSessionAdmin, logoutAdmin, refreshAdminSession, getUnreadAnnouncements, markAnnouncementsRead, MANAGEMENT_DEMO } from './lib/api'
import { nextTabAfterClose } from './lib/pageTabs.js'
import { navigation, sections } from './lib/navigation.js'
import ItemStockList from './components/ItemStockList.vue'
import ItemsBrowse from './components/ItemsBrowse.vue'
import ReturnVerification from './components/ReturnVerification.vue'
import ReturnReminders from './features/user/ReturnReminders.vue'
import UserLogin from './features/user/UserLogin.vue'
import BookingModal from './components/BookingModal.vue'
import UserAvatar from './components/UserAvatar.vue'
import MyReservations from './features/user/MyReservations.vue'
import MyProfile from './features/user/MyProfile.vue'
import UserProvision from './features/admin/UserProvision.vue'
import RoomPrototype from './features/rooms/RoomPrototype.vue'
import SpaceManagement from './features/rooms/SpaceManagement.vue'
import ApprovalQueue from './features/approval/ApprovalQueue.vue'
import LocationOverview from './features/dashboard/LocationOverview.vue'
import Announcements from './features/communication/Announcements.vue'
import Feedback from './features/communication/Feedback.vue'

const mode = ref('user')
const expandedGroups = ref({ booking: true, personal: false, management: true })
const mobileMenuOpen = ref(false)
const showLogin = ref(false)
const showAnnouncements = ref(false)
const announcementPage = ref(null)
const unreadAnnouncementsOnly = ref(false)
const viewedAnnouncements = new Map()
let announcementCheckVersion = 0

function openAnnouncements() {
  announcementPage.value = null
  unreadAnnouncementsOnly.value = false
  viewedAnnouncements.clear()
  showAnnouncements.value = true
}

function recordViewedAnnouncements(rows) {
  for (const row of rows) viewedAnnouncements.set(row.id, { id: row.id, updatedAt: row.updatedAt })
}

async function closeAnnouncements() {
  showAnnouncements.value = false
  const versions = [...viewedAnnouncements.values()]
  viewedAnnouncements.clear()
  const userId = adminUser.value?.id
  if (!userId || !versions.length) return
  try {
    for (let offset = 0; offset < versions.length; offset += 100) {
      if (adminUser.value?.id !== userId) return
      await markAnnouncementsRead(versions.slice(offset, offset + 100))
    }
  } catch {
    // Leave notices unread if saving fails, so they can be shown at the next login.
  }
}

async function checkUnreadAnnouncements(user) {
  const version = ++announcementCheckVersion
  if (!user) return
  try {
    const data = await getUnreadAnnouncements({ size: 10 })
    if (version !== announcementCheckVersion || adminUser.value?.id !== user.id
        || mode.value !== 'user' || showAnnouncements.value || !data.content.length) return
    announcementPage.value = data
    unreadAnnouncementsOnly.value = true
    viewedAnnouncements.clear()
    showAnnouncements.value = true
  } catch {
    // A notice service error must not prevent the user from signing in.
  }
}
const loginBusy = ref(false)
const enableManagementDemo = MANAGEMENT_DEMO
const activeSection = ref('items')
const selectedLocation = ref(null)
const adminUser = ref(getSessionAdmin())
const hasManagementRole = computed(() => adminUser.value?.roles?.some((role) =>
  ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER'].includes(role)))
const canManageItems = computed(() => adminUser.value?.roles?.some((role) =>
  ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER'].includes(role)))

const openTabs = ref([sections.find(section => section.key === 'items')])
const lastSections = ref({ user: 'items', admin: 'approval' })
const sessionGeneration = ref(0)
const pageComponents = {
  locations: LocationOverview, items: ItemsBrowse,
  rooms: RoomPrototype, myReservations: MyReservations, myFeedback: Feedback, myProfile: MyProfile, itemStock: ItemStockList,
  spaceManage: SpaceManagement, returnCheck: ReturnVerification, account: UserProvision,
  approval: ApprovalQueue, announcementAdmin: Announcements, feedbackAdmin: Feedback,
  returnReminders: ReturnReminders,
}
const visibleTabs = computed(() => openTabs.value.filter(tab => tab.group === mode.value))
const visibleNavigation = computed(() => mode.value === 'admin' && !hasManagementRole.value ? [] : navigation[mode.value])
const currentSectionLabel = computed(() => sections.find(section => section.key === activeSection.value)?.label)
function ensureTab(key) {
  const section = sections.find(item => item.key === key)
  if (!section) return
  if (section.parent) expandedGroups.value[section.parent] = true
  if (!openTabs.value.some(tab => tab.key === key)) openTabs.value.push(section)
  lastSections.value[section.group] = key
  activeSection.value = key
}
function switchMode(next) {
  mobileMenuOpen.value = false
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
  mobileMenuOpen.value = false
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
    lastSections.value[section.group] = nextTabAfterClose(groupTabs, key, section.group === 'user' ? 'items' : 'approval')
  }
  if (activeSection.value === key) ensureTab(lastSections.value[section.group])
}
function pageProps(key) {
  if (key === 'items') return { currentUser: adminUser.value, initialLocation: selectedLocation.value }
  if (['myReservations', 'myFeedback', 'myProfile', 'rooms', 'account', 'approval', 'feedbackAdmin'].includes(key)) return { currentUser: adminUser.value, ...(key === 'feedbackAdmin' ? { managed: true } : {}) }
  if (key === 'announcementAdmin') return { managed: true }
  if (key === 'returnReminders') return { managed: true, currentUser: adminUser.value }
  return {}
}

function onLoggedIn(user) {
  adminUser.value = user
  showLogin.value = false
  checkUnreadAnnouncements(user)
}

async function onLogout() {
  announcementCheckVersion++
  showAnnouncements.value = false
  viewedAnnouncements.clear()
  await logoutAdmin()
  adminUser.value = null
  mode.value = 'user'
  openTabs.value = [sections.find(section => section.key === 'items')]
  lastSections.value = { user: 'items', admin: 'approval' }
  mobileMenuOpen.value = false
  selectedLocation.value = null
  sessionGeneration.value++
  activeSection.value = 'items'
}
onMounted(() => {
  if (!enableManagementDemo) refreshAdminSession().then((user) => {
    adminUser.value = user
    checkUnreadAnnouncements(user)
  }).catch(() => { adminUser.value = null })
})

</script>

<template>
  <div class="site-shell">
    <BookingModal v-if="showLogin" title="登录" compact disable-backdrop-close :busy="loginBusy" @close="showLogin = false"><UserLogin @busy-change="loginBusy = $event" @logged-in="onLoggedIn" /></BookingModal>
    <BookingModal v-if="showAnnouncements" title="公告栏" compact @close="closeAnnouncements">
      <Announcements in-modal :unread-only="unreadAnnouncementsOnly" :initial-page="announcementPage" @viewed="recordViewedAnnouncements" />
    </BookingModal>
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
          <button v-if="mode === 'user'" type="button" class="announcement-button" title="公告栏" aria-label="公告栏" :aria-expanded="showAnnouncements" @click="openAnnouncements">
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
      <nav class="side-nav" aria-label="主导航" @keydown.esc="mobileMenuOpen = false">
        <div class="mobile-nav-bar">
          <button class="nav-menu-toggle" type="button" aria-controls="navigation-tree" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen = !mobileMenuOpen">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
            菜单
          </button>
          <span>{{ currentSectionLabel }}</span>
        </div>
        <div id="navigation-tree" class="navigation-tree" :class="{ 'is-open': mobileMenuOpen }">
          <template v-for="entry in visibleNavigation" :key="entry.key">
            <div v-if="entry.children" class="nav-group">
              <button class="nav-group-toggle" type="button" :aria-expanded="expandedGroups[entry.key]" :aria-controls="`nav-${entry.key}`"
                @click="expandedGroups[entry.key] = !expandedGroups[entry.key]">
                <span>{{ entry.label }}</span>
                <svg :class="{ expanded: expandedGroups[entry.key] }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
              </button>
              <div v-show="expandedGroups[entry.key]" :id="`nav-${entry.key}`" class="nav-children">
                <button v-for="section in entry.children" :key="section.key"
                  type="button" class="nav-button" :class="{ active: activeSection === section.key }"
                  :aria-current="activeSection === section.key ? 'page' : undefined" @click="selectSection(section.key)">{{ section.label }}</button>
              </div>
            </div>
            <button v-else type="button" class="nav-button"
              :class="{ active: activeSection === entry.key }" :aria-current="activeSection === entry.key ? 'page' : undefined"
              @click="selectSection(entry.key)">{{ entry.label }}</button>
          </template>
        </div>
      </nav>

      <main class="main-content" tabindex="0" aria-label="页面内容" @click="mobileMenuOpen = false">
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
