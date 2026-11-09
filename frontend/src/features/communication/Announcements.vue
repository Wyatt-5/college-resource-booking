<script setup>
import { onMounted, ref } from 'vue'
import { getAnnouncements, createAnnouncement, updateAnnouncement } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'
const props=defineProps({ managed:{type:Boolean,default:false}, inModal:{type:Boolean,default:false} })
const rows=ref([]),page=ref(0),total=ref(0),loading=ref(false),saving=ref(false),error=ref(''),formError=ref(''),notice=ref(''),form=ref(null)
const size=10
const format=value=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
async function load(){loading.value=true;error.value='';try{const data=await getAnnouncements({managed:props.managed,page:page.value,size});rows.value=data.content;total.value=data.totalElements}catch(e){error.value=e.message||'读取公告失败'}finally{loading.value=false}}
function edit(row){formError.value='';form.value=row?{id:row.id,title:row.title,content:row.content,status:row.status}:{title:'',content:'',status:'PUBLISHED'}}
async function save(){if(saving.value)return;saving.value=true;formError.value='';notice.value='';try{const payload={title:form.value.title.trim(),content:form.value.content.trim(),status:form.value.status};if(form.value.id)await updateAnnouncement(form.value.id,payload);else await createAnnouncement(payload);form.value=null;notice.value='公告已保存。';await load()}catch(e){formError.value=e.message||'保存失败'}finally{saving.value=false}}
async function toggle(row){if(saving.value)return;const status=row.status==='PUBLISHED'?'WITHDRAWN':'PUBLISHED';if(!window.confirm(`确定${status==='WITHDRAWN'?'撤下':'重新发布'}“${row.title}”？`))return;saving.value=true;error.value='';notice.value='';try{await updateAnnouncement(row.id,{title:row.title,content:row.content,status});notice.value=status==='WITHDRAWN'?'公告已撤下。':'公告已重新发布。';await load()}catch(e){error.value=e.message||'操作失败'}finally{saving.value=false}}
onMounted(load)
</script>
<template>
  <section class="content-section announcements">
    <div class="section-heading"><div><h1 v-if="!inModal">{{ managed ? '公告管理' : '公告栏' }}</h1><p class="section-sub">{{ managed ? '发布和维护学院通知。' : '查看学院发布的最新通知。' }}</p></div><div class="actions"><button class="refresh-button" type="button" :disabled="loading" @click="load">刷新</button><button v-if="managed" class="primary-button" type="button" @click="edit(null)">发布公告</button></div></div>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p><p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <p v-if="loading" class="state-card">正在读取公告…</p><p v-else-if="!rows.length && !error" class="state-card">暂无公告。</p>
    <div v-else class="announcement-list"><article v-for="row in rows" :key="row.id" class="announcement-card">
      <div class="card-head"><h2>{{ row.title }}</h2><span v-if="managed" class="status-badge" :class="row.status==='PUBLISHED'?'badge-ok':'badge-off'">{{ row.status==='PUBLISHED'?'已发布':'已撤下' }}</span></div>
      <p class="announcement-content">{{ row.content }}</p><p class="card-hint">发布人：{{ row.authorName }} · 更新于 {{ format(row.updatedAt) }}</p>
      <div v-if="managed" class="actions"><button class="refresh-button" type="button" :disabled="saving" @click="edit(row)">编辑</button><button class="link-button" type="button" :disabled="saving" @click="toggle(row)">{{ row.status==='PUBLISHED'?'撤下':'重新发布' }}</button></div>
    </article></div>
    <nav v-if="total>size" class="pagination-bar" aria-label="公告分页"><button class="refresh-button" :disabled="page===0||loading" @click="page--;load()">上一页</button><span>第 {{ page+1 }} 页 · 共 {{ total }} 条</span><button class="refresh-button" :disabled="(page+1)*size>=total||loading" @click="page++;load()">下一页</button></nav>
    <BookingModal v-if="form" :title="form.id?'编辑公告':'发布公告'" :busy="saving" @close="form=null"><form @submit.prevent="save"><label class="field"><span>标题</span><input v-model.trim="form.title" type="text" maxlength="100" required></label><label class="field"><span>内容</span><textarea v-model.trim="form.content" rows="7" maxlength="5000" required></textarea></label><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><button class="primary-button" type="submit" :disabled="saving">{{ saving?'保存中…':form.id?'保存修改':'确认发布' }}</button></form></BookingModal>
  </section>
</template>
<style scoped>
.actions{display:flex;gap:10px;flex-wrap:wrap}.announcement-list{display:grid;gap:14px}.announcement-card{background:white;border:1px solid #e6dff2;border-radius:16px;padding:20px}.card-head{display:flex;justify-content:space-between;gap:12px;align-items:start}.card-head h2{font-size:19px;margin:0;overflow-wrap:anywhere}.announcement-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75;color:#4d4260}.announcement-card .actions{margin-top:12px}
</style>
