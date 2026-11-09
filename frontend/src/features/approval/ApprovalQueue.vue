<template>
  <section class="content-section">
    <div class="section-heading">
      <div><h1>审核工作台</h1></div>
      <div class="approval-header-actions">
        <button v-if="canViewLogs" class="refresh-button" type="button" @click="showLog = true">预约日志</button>
        <button class="refresh-button" type="button" :disabled="loading" @click="load">{{ loading ? '读取中…' : '刷新' }}</button>
      </div>
    </div>
    <BookingModal v-if="showLog" title="预约日志" @close="showLog = false"><BookingLog in-modal /></BookingModal>
    <p class="section-sub">这里显示贵重物品和地点预约的真实申请。符合条件的用户均可提交，批给谁由审核员决定；申请人不能审核自己的申请。批准后系统会记录审核人和处理结论。已超过预约开始时间的待审申请不再显示在此处，可在预约日志中查看。</p>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="state-card" role="status">正在读取待审申请…</p>
    <p v-else-if="!requests.length" class="state-card">当前没有待审申请。</p>
    <div v-else class="reservation-list approval-grid">
      <article v-for="request in requests" :key="request.id" class="item-card reservation-entry approval-entry">
        <div>
          <h3>
            {{ request.resourceType === 'SPACE' ? '地点' : '物品' }} · {{ request.itemName }}
          </h3>
          <div class="applicant-line"><span>申请人：</span><UserAvatar :user-id="request.applicantId" :name="request.applicantName" /><span>{{ request.applicantName || ('账号 ' + request.applicantId) }}</span></div>
          <p class="approval-time">{{ formatTime(request.startAt) }} 至 {{ formatTime(request.endAt) }}</p>
          <p v-if="request.resourceType === 'ITEM'">数量 {{ request.quantity }} · 用途：{{ request.purpose }}</p>
          <p v-else>用途：{{ request.purpose }}</p>
          <p v-if="request.competingPending > 0" class="competing-hint">
            同时段还有 {{ request.competingPending }} 个待审申请；批准本单后，其余申请会因时段已被占用而无法再批准。
          </p>
          <label class="field"><span>拒绝原因</span><input v-model.trim="reasons[request.id]" type="text" maxlength="300" placeholder="拒绝时必填"></label>
        </div>
        <div class="approval-actions">
          <button class="primary-button" type="button" :disabled="savingId === request.id || request.applicantId === currentUser?.id" @click="decide(request, 'APPROVE')">同意</button>
          <button class="refresh-button" type="button" :disabled="savingId === request.id || request.applicantId === currentUser?.id" @click="decide(request, 'REJECT')">拒绝</button>
        </div>
      </article>
    </div>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <nav v-if="total > size" class="pagination-bar" aria-label="审核分页">
      <button class="refresh-button" :disabled="page === 0 || loading" @click="page--; load()">上一页</button>
      <span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span>
      <button class="refresh-button" :disabled="(page + 1) * size >= total || loading" @click="page++; load()">下一页</button>
    </nav>
  </section>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import BookingModal from '../../components/BookingModal.vue'
import UserAvatar from '../../components/UserAvatar.vue'
import BookingLog from '../admin/BookingLog.vue'
import { getPendingReviews, reviewReservation } from '../../lib/api'

const props = defineProps({ currentUser: { type: Object, default: null } })
const canViewLogs = computed(() => props.currentUser?.roles?.some(role => ['SYSTEM_ADMIN', 'COUNSELOR_ADMIN', 'RESOURCE_MANAGER', 'REVIEWER'].includes(role)))
const showLog = ref(false)
const requests = ref([])
const reasons = ref({})
const page = ref(0)
const size = 20
const total = ref(0)
const loading = ref(false)
const savingId = ref(null)
const error = ref('')
const message = ref('')
const formatTime = value => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await getPendingReviews({ page: page.value, size })
    requests.value = result.content
    total.value = result.totalElements
  } catch (cause) { error.value = cause.message || '读取审核队列失败。' }
  finally { loading.value = false }
}
async function decide(request, decision) {
  if (savingId.value != null) return
  const reason = reasons.value[request.id] || ''
  if (decision === 'REJECT' && !reason.trim()) { error.value = '拒绝时请填写原因。'; return }
  savingId.value = request.id
  error.value = ''
  message.value = ''
  try {
    const saved = await reviewReservation(request.id, { decision, reason })
    message.value = `申请已${saved.status === 'CONFIRMED' ? '批准' : '拒绝'}。`
    await load()
  } catch (cause) { error.value = cause.message || '审核失败，请刷新后重试。' }
  finally { savingId.value = null }
}
let activatedOnce = false
onActivated(() => { if (activatedOnce) load(); activatedOnce = true })
onMounted(load)
</script>

<style scoped>
.approval-grid{grid-template-columns:repeat(auto-fill,minmax(min(100%,360px),1fr));align-items:stretch}.approval-entry{display:flex;flex-direction:column;align-items:stretch;gap:12px;padding:18px}.approval-entry h3{font-size:16px;overflow-wrap:anywhere}.approval-entry p{font-size:13px;line-height:1.6;overflow-wrap:anywhere}.approval-entry .field{margin:12px 0 0}.approval-entry .approval-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:auto}.approval-entry .approval-actions button{flex:0 1 auto}.applicant-line{display:flex;align-items:center;gap:8px;margin:8px 0;min-width:0}.applicant-line span{min-width:0;overflow-wrap:anywhere}.approval-time{color:#776d94}.approval-header-actions{display:flex;gap:10px;flex-wrap:wrap}
.priority-badge{display:inline-block;margin-left:8px;padding:2px 9px;border-radius:999px;background:#f3e7ff;color:#5f3a8a;font-size:12px;font-weight:600;vertical-align:middle}
.competing-hint{margin-top:6px;padding:8px 10px;border-radius:9px;background:#fff6e6;color:#8a5a17;font-size:13px;line-height:1.6}
</style>
