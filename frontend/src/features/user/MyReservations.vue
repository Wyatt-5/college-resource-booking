<template>
  <section class="content-section">
    <div class="section-heading">
      <div><p class="eyebrow">用户端</p><h1>我的预约</h1></div>
      <button v-if="currentUser" class="refresh-button" :disabled="loading" @click="load">
        {{ loading ? '读取中…' : '刷新' }}
      </button>
    </div>
    <div v-if="!currentUser" class="state-card">
      <p>登录后查看自己的预约。</p>
      <button class="primary-button" @click="emit('login-requested')">去登录</button>
    </div>
    <p v-else-if="errorMessage" class="state-card error-card" role="alert">
      {{ errorMessage }} <button class="link-button" @click="load">重试</button>
    </p>
    <p v-else-if="loading" class="state-card">正在读取预约…</p>
    <p v-else-if="!reservations.length" class="state-card">你还没有提交预约。</p>
    <div v-else class="reservation-list">
      <article v-for="reservation in reservations" :key="reservation.id" class="item-card reservation-entry">
        <div><h3>{{ reservation.itemName }}</h3><p>{{ formatTime(reservation.startAt) }} 至 {{ formatTime(reservation.endAt) }}</p>
          <p>数量 {{ reservation.quantity }} · {{ reservation.purpose }}</p></div>
        <span class="status-badge badge-normal">{{ statusName(reservation.status) }}</span>
      </article>
    </div>
    <nav v-if="currentUser && total > size" class="section-heading" aria-label="预约分页">
      <button class="refresh-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
      <span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span>
      <button class="refresh-button" :disabled="(page + 1) * size >= total || loading" @click="changePage(page + 1)">下一页</button>
    </nav>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { getMyReservations } from '../../lib/api'

const props = defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested'])
const reservations = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const loading = ref(false)
const errorMessage = ref('')
const labels = { CONFIRMED: '已确认', PENDING_APPROVAL: '待审核', REJECTED: '已拒绝',
  CANCELLED: '已取消', EXPIRED: '已过期' }
const statusName = (status) => labels[status] || status
const formatTime = (value) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })

async function load() {
  if (!props.currentUser) return
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getMyReservations({ page: page.value, size })
    reservations.value = result.content
    total.value = result.totalElements
  } catch (error) {
    errorMessage.value = error.message || '读取预约失败。'
  } finally {
    loading.value = false
  }
}
function changePage(next) { page.value = next; load() }
watch(() => props.currentUser?.id, () => { reservations.value = []; page.value = 0; load() })
onMounted(load)
</script>
