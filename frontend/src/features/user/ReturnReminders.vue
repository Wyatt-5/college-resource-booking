<script setup>
import { onMounted, onActivated, onDeactivated, onUnmounted, ref, watch } from 'vue'
import { getReturnReminders } from '../../lib/api'
import UserAvatar from '../../components/UserAvatar.vue'
const props = defineProps({ managed: Boolean, inModal: Boolean, currentUser: { type: Object, default: null } })
const rows = ref([]), total = ref(0), page = ref(0), loading = ref(false), error = ref('')
const size = 20
let generation = 0, timer
const fmt = value => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
async function load() {
  const version = ++generation
  if (!props.currentUser) return
  loading.value = true
  try {
    const data = await getReturnReminders({ managed: props.managed, page: page.value, size })
    if (version !== generation) return
    rows.value = data.content; total.value = data.totalElements; error.value = ''
    if (!rows.value.length && page.value > 0) { page.value--; load() }
  } catch (e) { if (version === generation) error.value = e.message || '读取归还提醒失败' }
  finally { if (version === generation) loading.value = false }
}
function start() { clearInterval(timer); timer = setInterval(load, 30000) }
function stop() { clearInterval(timer); generation++ }
watch(() => props.currentUser?.id, () => { rows.value = []; total.value = 0; page.value = 0; load() })
onMounted(() => { load(); start() })
let activated = false
onActivated(() => { if (activated) load(); activated = true; start() })
onDeactivated(stop)
onUnmounted(stop)
</script>
<template>
  <section class="content-section return-reminders">
    <div v-if="!inModal" class="section-heading"><h1>未归还提醒</h1></div>
    <p v-if="error" class="error-card" role="alert">{{ error }} <button class="link-button" @click="load">重试</button></p>
    <p v-if="loading && !rows.length" class="state-card">正在读取提醒…</p>
    <p v-else-if="!rows.length && !error" class="state-card">暂无逾期未归还的物品。</p>
    <div v-else class="reminder-grid">
      <article v-for="row in rows" :key="row.id" class="item-card reminder-entry">
        <div class="reminder-head"><h3>{{ row.resourceName }}</h3><span class="status-badge badge-reject">逾期未归还</span></div>
        <p v-if="managed" class="applicant-line"><span>申请人：</span><UserAvatar :user-id="row.applicantId" :name="row.applicantName" /><span>{{ row.applicantName }}</span></p>
        <p>应归还时间：{{ fmt(row.endAt) }}</p>
        <p>预约编号：{{ row.reservationId }}<template v-if="row.unitCodes"> · 实物编号：{{ row.unitCodes }}</template></p>
        <p v-if="row.handoverStatus === 'RETURN_REJECTED'" class="rejection-reason">归还核验被驳回，等待重新提交。</p>
        <p class="reminder-message">{{ row.message }}</p>
      </article>
    </div>
    <nav v-if="total > size" class="pagination-bar" aria-label="未归还提醒分页"><button class="refresh-button" :disabled="page === 0 || loading" @click="page--;load()">上一页</button><span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span><button class="refresh-button" :disabled="(page + 1) * size >= total || loading" @click="page++;load()">下一页</button></nav>
  </section>
</template>
<style scoped>
.reminder-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,320px),1fr));gap:12px}
.reminder-entry{padding:16px;min-width:0}.reminder-head{display:flex;align-items:start;justify-content:space-between;gap:8px}
.reminder-head h3{margin:0;font-size:16px;overflow-wrap:anywhere}.reminder-head .status-badge{flex-shrink:0}
.reminder-entry p{font-size:13px;line-height:1.6;margin:8px 0 0;overflow-wrap:anywhere}
.applicant-line{display:flex;align-items:center;gap:6px;flex-wrap:wrap}.applicant-line :deep(.user-avatar){width:24px;height:24px;font-size:12px}
.reminder-message{color:#66507c;background:#f5f0fb;border-radius:8px;padding:10px}.rejection-reason{color:#a53e31}
</style>
