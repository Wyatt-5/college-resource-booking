<template>
  <section class="content-section">
    <div class="section-heading">
      <div><h1>我的预约</h1></div>
      <button v-if="currentUser" class="refresh-button" :disabled="loading" @click="load">{{ loading ? '读取中…' : '刷新' }}</button>
    </div>
    <p v-if="banner" class="success-banner" role="status">{{ banner }}</p>
    <div v-if="!currentUser" class="state-card"><p>登录后查看自己的预约。</p><button class="primary-button" @click="emit('login-requested')">去登录</button></div>
    <p v-else-if="errorMessage" class="state-card error-card" role="alert">{{ errorMessage }} <button class="link-button" @click="load">重试</button></p>
    <p v-else-if="loading" class="state-card">正在读取预约…</p>
    <p v-else-if="!reservations.length" class="state-card">你还没有提交预约。</p>
    <div v-else class="reservation-list my-reservation-grid">
      <article v-for="reservation in reservations" :key="reservation.id" class="item-card reservation-entry">
        <div class="reservation-entry-main">
          <div class="reservation-entry-head"><h3>{{ reservation.resourceType === 'SPACE' ? '地点 · ' : '物品 · ' }}{{ reservation.itemName }}</h3><span :class="['status-badge', reservationBadgeClass(reservation)]">{{ displayStatus(reservation) }}</span></div>
          <p class="reservation-time">{{ formatTime(reservation.startAt) }} 至 {{ formatTime(reservation.endAt) }}</p>
          <p>{{ reservation.resourceType === 'ITEM' ? `数量 ${reservation.quantity} · ` : '' }}{{ reservation.purpose }}</p>
          <ol v-if="reservation.resourceType === 'ITEM' && reservation.status === 'CONFIRMED' && reservation.handoverStatus" class="handover-progress" aria-label="借还进度">
            <li v-for="(label, index) in HANDOVER_STEPS" :key="label" :class="handoverStepState(reservation.handoverStatus, index)">
              <span class="progress-dot" aria-hidden="true">{{ handoverStepState(reservation.handoverStatus, index) === 'done' ? '✓' : index + 1 }}</span>
              <small>{{ label }}</small>
            </li>
          </ol>
          <p v-if="reservation.resourceType === 'ITEM' && handoverGuidance(reservation.handoverStatus)" class="handover-guidance">{{ handoverGuidance(reservation.handoverStatus) }}</p>
          <p v-if="pickupHint(reservation)" class="pickup-hint">{{ pickupHint(reservation) }}</p>
          <div v-if="reservation.handoverStatus === 'RETURN_REJECTED'" class="rejection-reason" role="alert"><strong>核验未通过</strong><span>{{ reservation.lastReturnReason || '请补充照片后重新提交' }}</span></div>
        </div>
        <div class="reservation-actions">
          <button class="refresh-button" type="button" @click="selected=reservation">查看详情</button>
          <button v-if="showPickup(reservation)" class="primary-button" type="button" :disabled="!canPickup(reservation)" @click="pickupTarget=reservation">确认领取</button>
          <button v-if="canReturn(reservation)" class="primary-button" type="button" @click="returnTarget=reservation">{{ reservation.handoverStatus === 'RETURN_REJECTED' ? '重新提交归还' : '提交归还' }}</button>
          <button v-if="canCancel(reservation)" class="link-button" type="button" :disabled="cancelling===reservation.id" @click="cancel(reservation)">取消预约</button>
        </div>
      </article>
    </div>
    <BookingModal v-if="selected" :title="`预约详情 · ${selected.itemName}`" @close="selected=null">
      <dl class="reservation-detail">
        <dt>资源</dt><dd>{{ selected.itemName }}（{{ selected.resourceType==='SPACE'?'地点':'物品' }}）</dd>
        <dt>预约状态</dt><dd>{{ statusName(selected.status) }}</dd>
        <template v-if="selected.resourceType === 'ITEM'"><dt>借还状态</dt><dd>{{ handoverName(selected.handoverStatus) }}</dd></template>
        <dt>预约时间</dt><dd>{{ formatTime(selected.startAt) }} 至 {{ formatTime(selected.endAt) }}</dd>
        <dt>提交时间</dt><dd>{{ formatTime(selected.submittedAt) }}</dd>
        <dt>申请数量</dt><dd>{{ selected.quantity }}</dd><dt>用途</dt><dd>{{ selected.purpose }}</dd>
        <template v-if="selected.lastReturnReason"><dt>核验意见</dt><dd>{{ selected.lastReturnReason }}</dd></template>
      </dl>
    </BookingModal>
    <PickupConfirmationModal v-if="pickupTarget" :reservation="pickupTarget" @close="pickupTarget=null" @submitted="afterPickup" />
    <ReturnSubmissionModal v-if="returnTarget" :reservation="returnTarget" @close="returnTarget=null" @submitted="afterReturn" />
    <nav v-if="currentUser && total > size" class="pagination-bar" aria-label="预约分页">
      <button class="refresh-button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button><span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span><button class="refresh-button" :disabled="(page + 1) * size >= total || loading" @click="changePage(page + 1)">下一页</button>
    </nav>
  </section>
</template>

<script setup>
import { onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { getMyReservations, cancelMyReservation } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'
import PickupConfirmationModal from '../../components/PickupConfirmationModal.vue'
import ReturnSubmissionModal from '../../components/ReturnSubmissionModal.vue'
import { HANDOVER_STEPS, handoverGuidance, handoverStepState, reservationBadgeClass } from '../../lib/handoverState'

const props = defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested'])
const reservations = ref([]), total = ref(0), page = ref(0), loading = ref(false)
const errorMessage = ref(''), banner = ref(''), selected = ref(null), pickupTarget = ref(null), returnTarget = ref(null), cancelling = ref(null)
const size = 20
const now = ref(Date.now())
let clockTimer
const labels = { CONFIRMED: '已确认', PENDING_APPROVAL: '待审核', REJECTED: '已拒绝', CANCELLED: '已取消', EXPIRED: '已过期' }
const handoverLabels = { NOT_PICKED_UP: '等待领取', CHECKED_OUT: '使用中', RETURN_SUBMITTED: '等待归还核验', RETURN_REJECTED: '归还被驳回', COMPLETED: '借还完成' }
const statusName = status => labels[status] || status
const handoverName = status => handoverLabels[status] || '未进入借还流程'
const displayStatus = reservation => reservation.status === 'CONFIRMED' && reservation.handoverStatus ? handoverName(reservation.handoverStatus) : statusName(reservation.status)
const formatTime = value => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
const showPickup = reservation => reservation.resourceType === 'ITEM' && reservation.status === 'CONFIRMED' && reservation.handoverStatus === 'NOT_PICKED_UP'
const canPickup = reservation => reservation.resourceType === 'ITEM' && reservation.status === 'CONFIRMED' && reservation.handoverStatus === 'NOT_PICKED_UP' && new Date(reservation.startAt).getTime() <= now.value && new Date(reservation.endAt).getTime() > now.value
const pickupHint = reservation => {
  if (reservation.resourceType !== 'ITEM' || reservation.status !== 'CONFIRMED' || reservation.handoverStatus !== 'NOT_PICKED_UP') return ''
  if (new Date(reservation.startAt).getTime() > now.value) return '预约开始后可以确认领取，请在使用时段内领取。'
  if (new Date(reservation.endAt).getTime() <= now.value) return '预约时间已结束，无法确认领取，请联系管理员。'
  return ''
}
const canReturn = reservation => reservation.resourceType === 'ITEM' && ['CHECKED_OUT', 'RETURN_REJECTED'].includes(reservation.handoverStatus)
const canCancel = reservation => ['CONFIRMED', 'PENDING_APPROVAL'].includes(reservation.status) && (!reservation.handoverStatus || reservation.handoverStatus === 'NOT_PICKED_UP') && new Date(reservation.startAt) > new Date()

async function load() {
  if (!props.currentUser) return
  loading.value = true; errorMessage.value = ''
  try { const result = await getMyReservations({ page: page.value, size }); reservations.value = result.content; total.value = result.totalElements }
  catch (error) { errorMessage.value = error.message || '读取预约失败。' }
  finally { loading.value = false }
}
function changePage(next) { page.value = next; load() }
async function cancel(reservation) {
  if (!window.confirm(`取消 ${reservation.itemName} 的预约？`)) return
  cancelling.value = reservation.id; errorMessage.value = ''
  try { await cancelMyReservation(reservation.id); await load(); if (selected.value?.id === reservation.id) selected.value = null }
  catch (error) { errorMessage.value = error.message || '取消失败' }
  finally { cancelling.value = null }
}
async function afterPickup(result) { pickupTarget.value = null; banner.value = `领取确认成功，已绑定实物 ${result.unitCodes.join('、')}。`; await load() }
async function afterReturn() { returnTarget.value = null; banner.value = '归还材料已提交，请等待管理员核验。'; await load() }
watch(() => props.currentUser?.id, () => { reservations.value = []; page.value = 0; load() })
let activatedOnce = false
onActivated(() => { if (activatedOnce) load(); activatedOnce = true })
onMounted(() => { load(); clockTimer = window.setInterval(() => { now.value = Date.now() }, 30000) })
onUnmounted(() => window.clearInterval(clockTimer))
</script>
<style scoped>
.my-reservation-grid{grid-template-columns:repeat(auto-fill,minmax(min(100%,340px),1fr));align-items:stretch}
.pickup-hint{color:#665b7e;line-height:1.6}
.rejection-reason{display:grid;gap:3px;border:1px solid #efc1b4;border-radius:10px;padding:10px 12px;background:#fff5f1;color:#8f3a25}
.rejection-reason strong{font-size:13px}.rejection-reason span{font-size:12px;line-height:1.6}
.handover-guidance{color:#65567e!important;font-size:12px!important}
.handover-progress{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0;list-style:none;margin:10px 0 4px;padding:0}
.handover-progress li{position:relative;display:grid;justify-items:center;gap:5px;color:#9a90ad;text-align:center}
.handover-progress li:not(:last-child)::after{content:'';position:absolute;left:calc(50% + 13px);right:calc(-50% + 13px);top:11px;height:2px;background:#e3ddef}
.handover-progress .progress-dot{position:relative;z-index:1;display:grid;place-items:center;width:24px;height:24px;border-radius:50%;background:#eeeaf5;color:#716782;font-size:11px;font-weight:800}
.handover-progress li.done::after{background:#7a62ad}.handover-progress li.done .progress-dot{background:#6a509d;color:#fff}
.handover-progress li.current .progress-dot{box-shadow:0 0 0 3px #ddd3f0;background:#fff;color:#51377f;border:2px solid #6a509d}
.handover-progress li.rejected .progress-dot{box-shadow:0 0 0 3px #fae5df;background:#fff;color:#9b3d29;border:2px solid #bd5942}
.handover-progress li.current small{color:#51377f;font-weight:700}.handover-progress li.rejected small{color:#9b3d29;font-weight:700}
.handover-progress small{font-size:10px;line-height:1.3}
/* 我的预约：标题与状态同一行，时间与用途分层，操作按钮独立成块，手机上整行可点 */
.reservation-entry{display:flex;flex-direction:column;align-items:stretch;gap:14px;padding:18px}
.reservation-entry-main{display:flex;flex-direction:column;gap:6px;min-width:0}
.reservation-entry-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}
.reservation-entry-head h3{margin:0;font-size:16px;line-height:1.45;overflow-wrap:anywhere}
.reservation-entry-head .status-badge{flex:0 0 auto;margin-top:2px}
.reservation-entry p{margin:0;color:#5d5478;font-size:13px;line-height:1.6;overflow-wrap:anywhere}
.reservation-entry .reservation-time{color:#776d94;font-size:12px}
.reservation-actions{margin-top:auto;display:flex;gap:10px;flex-wrap:wrap}
.reservation-actions button{flex:0 1 auto;min-width:0;padding:9px 13px}
.cancel-button{border-color:#dda694;color:#a03d2a}
.cancel-button:hover:not(:disabled){background:#fdf3ee}
.reservation-detail{display:grid;grid-template-columns:110px 1fr;gap:12px 14px;margin:0}
.reservation-detail dt{color:#81718f;font-size:13px}
.reservation-detail dd{margin:0;font-size:14px;overflow-wrap:anywhere}
@media(max-width:560px){
  .reservation-entry{padding:16px}
  .reservation-entry-head{flex-wrap:wrap}
  .reservation-actions button{flex:1 1 100%}
  .reservation-detail{grid-template-columns:1fr;gap:4px}
  .reservation-detail dt{margin-top:9px}
  .handover-progress small{font-size:9px}
}
</style>
