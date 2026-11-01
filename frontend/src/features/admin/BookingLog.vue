<script setup>
import { ref, onMounted, watch } from 'vue'
import { getBookingLogs } from '../../lib/api'
const props=defineProps({ itemId: { type:Number, default:null } })
const rows=ref([]), page=ref(0), total=ref(0), error=ref(''), loading=ref(false)
const size=20
const fmt=value=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
const states={CONFIRMED:'已确认',PENDING_APPROVAL:'待审核',CANCELLED:'已取消',REJECTED:'已拒绝',EXPIRED:'已过期'}
async function load(){loading.value=true;error.value='';try{const data=await getBookingLogs({itemId:props.itemId,page:page.value,size});rows.value=data.content;total.value=data.totalElements}catch(e){error.value=e.message||'读取预约记录失败'}finally{loading.value=false}}
watch(()=>props.itemId,()=>{page.value=0;load()})
onMounted(load)
</script>
<template>
  <section class="booking-log">
    <div class="section-heading"><h3>{{ itemId ? '该物品的预约记录' : '预约日志' }}</h3><button class="refresh-button" @click="load">刷新</button></div>
    <p v-if="error" class="error-card" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="card-hint">正在读取…</p>
    <p v-else-if="!rows.length" class="card-hint">暂无预约记录。</p>
    <div v-else class="log-list"><article v-for="row in rows" :key="row.id" class="log-entry">
      <div><strong>{{ row.resourceName }}</strong><span> · {{ row.resourceType === 'SPACE' ? '地点' : '物品' }} #{{ row.id }}</span></div>
      <p>申请人：{{ row.applicantName }}（{{ row.loginName }}） · 申请于 {{ fmt(row.submittedAt) }}</p>
      <p>使用时间：{{ fmt(row.startAt) }} 至 {{ fmt(row.endAt) }} · {{ row.resourceType === 'ITEM' ? `数量 ${row.quantity} · ` : '' }}{{ states[row.status] || row.status }}</p>
      <p>用途：{{ row.purpose }}</p>
    </article></div>
    <nav v-if="total>size" class="section-heading"><button class="refresh-button" :disabled="page===0" @click="page--;load()">上一页</button><span>共 {{ total }} 条 · 第 {{ page+1 }} 页</span><button class="refresh-button" :disabled="(page+1)*size>=total" @click="page++;load()">下一页</button></nav>
  </section>
</template>
<style scoped>
.booking-log{margin-top:24px}.log-list{display:grid;gap:10px}.log-entry{border:1px solid #e6dff1;border-radius:12px;padding:14px 18px;background:#fff}.log-entry p{margin:6px 0 0;color:#736486;font-size:13px;line-height:1.5}.log-entry span{color:#87769b}
</style>
