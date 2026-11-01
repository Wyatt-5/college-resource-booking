<template>
  <section class="content-section">
    <div class="section-heading">
      <div><p class="eyebrow">管理端 · 预约审核</p><h1>审核工作台</h1></div>
      <button class="refresh-button" type="button" :disabled="loading" @click="load">{{ loading ? '读取中…' : '刷新' }}</button>
    </div>
    <p class="section-sub">这里显示贵重物品和地点预约的真实申请。每笔由一位审核员决定；申请人不能审核自己的申请。地点冲突时辅导员申请优先，同优先级按提交顺序处理。</p>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="state-card" role="status">正在读取待审申请…</p>
    <p v-else-if="!requests.length" class="state-card">当前没有待审申请。</p>
    <div v-else class="reservation-list">
      <article v-for="request in requests" :key="request.id" class="item-card reservation-entry">
        <div>
          <h3>{{ request.resourceType === 'SPACE' ? '地点' : '物品' }} · {{ request.itemName }} · #{{ request.id }}</h3>
          <p>申请人 #{{ request.applicantId }} · {{ formatTime(request.startAt) }} 至 {{ formatTime(request.endAt) }}</p>
          <p v-if="request.resourceType === 'ITEM'">数量 {{ request.quantity }} · 用途：{{ request.purpose }}</p>
          <p v-else>用途：{{ request.purpose }}</p>
          <label class="field"><span>拒绝原因</span><input v-model.trim="reasons[request.id]" maxlength="300" placeholder="拒绝时必填"></label>
        </div>
        <div class="approval-actions">
          <button class="primary-button" type="button" :disabled="savingId === request.id || request.applicantId === currentUser?.id" @click="decide(request, 'APPROVE')">同意</button>
          <button class="refresh-button" type="button" :disabled="savingId === request.id || request.applicantId === currentUser?.id" @click="decide(request, 'REJECT')">拒绝</button>
        </div>
      </article>
    </div>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <nav v-if="total > size" class="section-heading" aria-label="审核分页">
      <button class="refresh-button" :disabled="page === 0 || loading" @click="page--; load()">上一页</button>
      <span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span>
      <button class="refresh-button" :disabled="(page + 1) * size >= total || loading" @click="page++; load()">下一页</button>
    </nav>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getPendingReviews, reviewReservation } from '../../lib/api'

defineProps({ currentUser: { type: Object, default: null } })
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
    message.value = `申请 #${saved.id} 已${saved.status === 'CONFIRMED' ? '批准' : '拒绝'}。`
    await load()
  } catch (cause) { error.value = cause.message || '审核失败，请刷新后重试。' }
  finally { savingId.value = null }
}
onMounted(load)
</script>
