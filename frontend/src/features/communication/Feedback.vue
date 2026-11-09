<script setup>
import { onMounted, ref, watch } from 'vue'
import { getFeedback, createFeedback, handleFeedback } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'
const props=defineProps({ currentUser:{type:Object,default:null},managed:{type:Boolean,default:false} })
const emit=defineEmits(['login-requested'])
const rows=ref([]),page=ref(0),total=ref(0),loading=ref(false),saving=ref(false),error=ref(''),formError=ref(''),notice=ref(''),showForm=ref(false),content=ref(''),editing=ref(null)
const size=10, labels={OPEN:'待处理',IN_PROGRESS:'处理中',RESOLVED:'已处理'}
const format=value=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
async function load(){if(!props.currentUser){rows.value=[];return}loading.value=true;error.value='';try{const data=await getFeedback({managed:props.managed,page:page.value,size});rows.value=data.content;total.value=data.totalElements}catch(e){error.value=e.message||'读取反馈失败'}finally{loading.value=false}}
async function submit(){if(saving.value)return;saving.value=true;formError.value='';try{await createFeedback(content.value.trim());content.value='';showForm.value=false;page.value=0;notice.value='反馈已提交，可在这里查看处理进度。';await load()}catch(e){formError.value=e.message||'提交失败'}finally{saving.value=false}}
function openHandle(row){formError.value='';editing.value={id:row.id,content:row.content,userName:row.userName,status:row.status,response:row.response||''}}
async function save(){if(saving.value)return;saving.value=true;formError.value='';try{await handleFeedback(editing.value.id,{status:editing.value.status,response:editing.value.response.trim()});editing.value=null;notice.value='处理结果已保存。';await load()}catch(e){formError.value=e.message||'保存失败'}finally{saving.value=false}}
watch(()=>props.currentUser?.id,()=>{page.value=0;load()})
onMounted(load)
</script>
<template>
  <section class="content-section feedback-page">
    <div class="section-heading"><div><h1>{{ managed?'用户反馈':'我的反馈' }}</h1><p class="section-sub">{{ managed?'查看并回复用户提交的问题和建议。':'提交问题或建议，并查看处理进度。' }}</p></div><div class="actions"><button v-if="currentUser" class="refresh-button" type="button" :disabled="loading" @click="load">刷新</button><button v-if="!managed" class="primary-button" type="button" @click="currentUser ? (showForm=true) : emit('login-requested')">提交反馈</button></div></div>
    <p v-if="!currentUser" class="state-card">登录后可提交和查看自己的反馈。</p>
    <template v-else><p v-if="notice" class="notice" role="status">{{ notice }}</p><p v-if="error" class="state-card error-card" role="alert">{{ error }}</p><p v-if="loading" class="state-card">正在读取反馈…</p><p v-else-if="!rows.length && !error" class="state-card">暂无反馈。</p>
      <div v-else class="feedback-list"><article v-for="row in rows" :key="row.id" class="feedback-card"><div class="card-head"><strong>{{ managed?row.userName:'我的反馈' }}</strong><span class="status-badge" :class="row.status==='RESOLVED'?'badge-ok':'badge-normal'">{{ labels[row.status] || row.status }}</span></div><p class="feedback-content">{{ row.content }}</p><p class="card-hint">提交于 {{ format(row.createdAt) }}</p><div v-if="row.response" class="reply"><strong>处理回复</strong><p>{{ row.response }}</p><small v-if="row.handlerName">处理人：{{ row.handlerName }}<template v-if="row.handledAt"> · {{ format(row.handledAt) }}</template></small></div><button v-if="managed" class="refresh-button" type="button" @click="openHandle(row)">查看并处理</button></article></div>
      <nav v-if="total>size" class="pagination-bar" aria-label="反馈分页"><button class="refresh-button" :disabled="page===0||loading" @click="page--;load()">上一页</button><span>第 {{ page+1 }} 页 · 共 {{ total }} 条</span><button class="refresh-button" :disabled="(page+1)*size>=total||loading" @click="page++;load()">下一页</button></nav>
    </template>
    <BookingModal v-if="showForm" title="提交反馈" :busy="saving" @close="showForm=false"><form @submit.prevent="submit"><label class="field"><span>问题或建议</span><textarea v-model.trim="content" rows="6" maxlength="2000" required placeholder="请描述遇到的问题或你的建议"></textarea></label><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><button class="primary-button" :disabled="saving" type="submit">{{ saving?'提交中…':'提交反馈' }}</button></form></BookingModal>
    <BookingModal v-if="editing" title="处理反馈" :busy="saving" @close="editing=null"><form @submit.prevent="save"><p><strong>{{ editing.userName }}</strong> 的反馈</p><p class="feedback-content">{{ editing.content }}</p><label class="field"><span>处理状态</span><select v-model="editing.status"><option value="OPEN">待处理</option><option value="IN_PROGRESS">处理中</option><option value="RESOLVED">已处理</option></select></label><label class="field"><span>回复内容{{ editing.status==='RESOLVED'?'（必填）':'' }}</span><textarea v-model.trim="editing.response" rows="5" maxlength="2000" :required="editing.status==='RESOLVED'"></textarea></label><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><button class="primary-button" :disabled="saving" type="submit">{{ saving?'保存中…':'保存处理结果' }}</button></form></BookingModal>
  </section>
</template>
<style scoped>
.actions{display:flex;gap:10px;flex-wrap:wrap}.feedback-list{display:grid;gap:14px}.feedback-card{background:#fff;border:1px solid #e6dff2;border-radius:16px;padding:20px}.card-head{display:flex;justify-content:space-between;gap:10px}.feedback-content,.reply p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;color:#4d4260}.reply{margin:12px 0;padding:14px;border-radius:10px;background:#f5f1fa}.reply p{margin:6px 0}.reply small{color:#776b8e}
</style>
