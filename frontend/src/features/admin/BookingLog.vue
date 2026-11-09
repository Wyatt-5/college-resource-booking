<script setup>
import { ref, onMounted, watch } from 'vue'
import { getBookingLogs } from '../../lib/api'
import UserAvatar from '../../components/UserAvatar.vue'
const props=defineProps({ itemId: { type:Number, default:null }, inModal: { type:Boolean, default:false } })
const rows=ref([]), page=ref(0), total=ref(0), error=ref(''), loading=ref(false)
const size=20
const fmt=value=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
const states={CONFIRMED:'已确认',PENDING_APPROVAL:'待审核',CANCELLED:'已取消',REJECTED:'已拒绝',EXPIRED:'已过期'}
const decisions={APPROVED:'审核通过',REJECTED:'审核拒绝',AUTO_CONFIRMED:'系统自动确认'}
async function load(){loading.value=true;error.value='';try{const data=await getBookingLogs({itemId:props.itemId,page:page.value,size});rows.value=data.content;total.value=data.totalElements}catch(e){error.value=e.message||'读取预约记录失败'}finally{loading.value=false}}
watch(()=>props.itemId,()=>{page.value=0;load()})
onMounted(load)
</script>
<template>
  <section class="booking-log" :class="{ 'in-modal': inModal }">
    <div v-if="!inModal" class="section-heading"><h3>{{ itemId ? '该物品的预约记录' : '预约日志' }}</h3><button class="refresh-button" @click="load">刷新</button></div>
    <p v-if="error" class="error-card" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="card-hint">正在读取…</p>
    <p v-else-if="!rows.length" class="card-hint">暂无预约记录。</p>
    <div v-else class="log-list"><article v-for="row in rows" :key="row.id" class="log-entry">
      <div><strong>{{ row.resourceName }}</strong><span> · {{ row.resourceType === 'SPACE' ? '地点' : '物品' }}</span></div>
      <p class="applicant-line"><span>申请人：</span><UserAvatar :user-id="row.applicantId" :name="row.applicantName" /><span>{{ row.applicantName }}（{{ row.loginName }}） · 申请于 {{ fmt(row.submittedAt) }}</span></p>
      <p>使用时间：{{ fmt(row.startAt) }} 至 {{ fmt(row.endAt) }} · {{ row.resourceType === 'ITEM' ? `数量 ${row.quantity} · ` : '' }}{{ states[row.status] || row.status }}</p>
      <p>用途：{{ row.purpose }}</p>
      <p v-if="row.decision" class="decision-line">
        处理结论：{{ decisions[row.decision] || row.decision }} ·
        审核人：{{ row.reviewerName || '系统' }}
        <template v-if="row.reviewedAt"> · {{ fmt(row.reviewedAt) }}</template>
        <template v-if="row.decisionReason"> · 理由：{{ row.decisionReason }}</template>
      </p>
    </article></div>
    <nav v-if="total>size" class="pagination-bar" aria-label="预约日志分页"><button class="refresh-button" :disabled="page===0" @click="page--;load()">上一页</button><span>共 {{ total }} 条 · 第 {{ page+1 }} 页</span><button class="refresh-button" :disabled="(page+1)*size>=total" @click="page++;load()">下一页</button></nav>
  </section>
</template>
<style scoped>
.booking-log.in-modal{margin-top:0}.applicant-line :deep(.user-avatar){width:26px;height:26px;font-size:12px}.applicant-line{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.booking-log{margin-top:24px}.log-list{display:grid;gap:10px}.log-entry{border:1px solid #e6dff1;border-radius:12px;padding:14px 18px;background:#fff}.log-entry p{margin:6px 0 0;color:#736486;font-size:13px;line-height:1.5}.log-entry span{color:#87769b}.log-entry .decision-line{margin-top:10px;padding:8px 10px;border-radius:9px;background:#f5f0fb;color:#5c4378}
</style>
