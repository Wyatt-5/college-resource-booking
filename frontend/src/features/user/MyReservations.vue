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
        <div><h3>{{ reservation.resourceType === 'SPACE' ? '地点 · ' : '物品 · ' }}{{ reservation.itemName }}</h3><p>{{ formatTime(reservation.startAt) }} 至 {{ formatTime(reservation.endAt) }}</p>
          <p>{{ reservation.resourceType === 'ITEM' ? `数量 ${reservation.quantity} · ` : '' }}{{ reservation.purpose }}</p></div>
        <span class="status-badge badge-normal">{{ statusName(reservation.status) }}</span>
        <div class="reservation-actions"><button class="refresh-button" type="button" @click="selected=reservation">查看详情</button><button v-if="['CONFIRMED','PENDING_APPROVAL'].includes(reservation.status) && new Date(reservation.startAt)>new Date()" class="link-button" type="button" :disabled="cancelling===reservation.id" @click="cancel(reservation)">取消预约</button></div>
      </article>
    </div>
    <BookingModal v-if="selected" :title="`预约 #${selected.id}`" @close="selected=null">
      <dl class="reservation-detail"><dt>资源</dt><dd>{{ selected.itemName }}（{{ selected.resourceType==='SPACE'?'地点':'物品' }}）</dd><dt>状态</dt><dd>{{ statusName(selected.status) }}</dd><dt>预约时间</dt><dd>{{ formatTime(selected.startAt) }} 至 {{ formatTime(selected.endAt) }}</dd><dt>提交时间</dt><dd>{{ formatTime(selected.submittedAt) }}</dd><dt>申请数量</dt><dd>{{ selected.quantity }}</dd><dt>用途</dt><dd>{{ selected.purpose }}</dd></dl>
    </BookingModal>
    <nav v-if="currentUser && total > size" class="section-heading" aria-label="预约分页">
      <button class="refresh-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
      <span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span>
      <button class="refresh-button" :disabled="(page + 1) * size >= total || loading" @click="changePage(page + 1)">下一页</button>
    </nav>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { getMyReservations, cancelMyReservation } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'

const props = defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested'])
const reservations = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const loading = ref(false)
const errorMessage = ref('')
const selected=ref(null)
const cancelling=ref(null)
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
async function cancel(reservation) {
  if (!window.confirm(`取消 ${reservation.itemName} 的预约？`)) return
  cancelling.value=reservation.id;errorMessage.value=''
  try { await cancelMyReservation(reservation.id); await load(); if(selected.value?.id===reservation.id)selected.value=null }
  catch(e){errorMessage.value=e.message||'取消失败'}
  finally{cancelling.value=null}
}
watch(() => props.currentUser?.id, () => { reservations.value = []; page.value = 0; load() })
onMounted(load)
</script>
<style scoped>.reservation-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.reservation-detail{display:grid;grid-template-columns:100px 1fr;gap:14px;margin:0}.reservation-detail dt{color:#81718f}.reservation-detail dd{margin:0;overflow-wrap:anywhere}</style>
