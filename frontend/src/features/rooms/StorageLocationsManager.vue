<script setup>
import { useReloadOnReturn } from '../../lib/useReloadOnReturn'
import { computed, onMounted, reactive, ref } from 'vue'
import { createLocation, getLocations, uploadLocationImage, updateLocation, removeLocation } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'
const props=defineProps({type:{type:String,default:'OFFICE'}})
const label=computed(()=>props.type==='OFFICE'?'办公室':'物资室')
const visibleLocations=computed(()=>locations.value.filter(x=>x.type===props.type&&x.status==='ACTIVE'))
function openCreate(){editing.value={id:null};form.name='';form.type=props.type;modalError.value=''}
const locations=ref([]), files=ref({}), busy=ref(null), error=ref(''), message=ref(''), imageVersion=ref({})
const editing=ref(null), removing=ref(null), modalError=ref('')
const form=reactive({name:'',type:'OFFICE'})
async function load(){try{locations.value=await getLocations()}catch(e){error.value=e.message||'地点读取失败'}}
async function upload(location){
  const file=files.value[location.id];if(!file||busy.value!==null)return
  busy.value=location.id;error.value='';message.value=''
  try{await uploadLocationImage(location.id,file);files.value[location.id]=null;imageVersion.value[location.id]=Date.now();await load();message.value=`${location.name}的照片已更新。`}
  catch(e){error.value=e.message||'照片上传失败'}finally{busy.value=null}
}
function openEdit(location){editing.value=location;form.name=location.name;form.type=location.type;modalError.value=''}
function openRemove(location){removing.value=location;modalError.value=''}
async function save(){
  if(busy.value!==null)return
  busy.value=editing.value.id ?? 'new';modalError.value=''
  try{await (editing.value.id ? updateLocation(editing.value.id,{name:form.name,type:form.type}) : createLocation({name:form.name,type:form.type}));editing.value=null;await load();message.value='地点信息已更新。'}catch(e){modalError.value=e.message||'保存失败'}finally{busy.value=null}
}
async function remove(){
  if(busy.value!==null)return
  busy.value=removing.value.id;modalError.value=''
  try{await removeLocation(removing.value.id);removing.value=null;await load();message.value='地点已移除，历史记录仍会保留。'}catch(e){modalError.value=e.message||'移除失败'}finally{busy.value=null}
}
onMounted(load)
useReloadOnReturn(load)
</script>
<template>
  <div class="storage-locations">
    <div class="section-heading"><h2>{{ label }}</h2><button class="primary-button" type="button" @click="openCreate">新增{{ label }}</button></div>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p><p v-if="message" class="notice" role="status">{{ message }}</p>
    <p v-if="visibleLocations.length === 0" class="state-card">暂无{{ label }}，可点击右上方新增。</p>
    <div class="storage-grid">
      <article v-for="location in visibleLocations" :key="location.id" class="storage-card">
        <div class="storage-photo"><img v-if="location.imageUrl" :src="`${location.imageUrl}?thumbnail=true&v=${imageVersion[location.id] || ''}`" loading="lazy" decoding="async" :alt="location.name"><span v-else>暂无照片</span></div>
        <div class="storage-body"><strong>{{ location.name }}</strong><small>{{ location.type === 'OFFICE' ? '办公室' : '物资室' }}</small>
          <label class="field"><span>展示照片（JPG/PNG，最大 5 MB）</span><input type="file" accept="image/jpeg,image/png" @change="files[location.id]=$event.target.files?.[0] || null"></label>
          <div class="storage-actions"><button class="refresh-button" type="button" :disabled="!files[location.id] || busy !== null" @click="upload(location)">{{ busy === location.id ? '上传中…' : '保存照片' }}</button>
          <button class="refresh-button" type="button" @click="openEdit(location)">编辑</button><button class="link-button remove-button" type="button" @click="openRemove(location)">移除</button></div>
        </div>
      </article>
    </div>
    <BookingModal v-if="editing" :title="editing.id ? '编辑存放地点' : `新增${label}`" compact :busy="busy !== null" @close="editing=null">
      <form @submit.prevent="save">
        <label class="field"><span>地点名称</span><input v-model.trim="form.name" type="text" maxlength="100" required></label>
        <label class="field"><span>地点类型</span><select v-model="form.type"><option value="OFFICE">办公室</option><option value="STORAGE">物资室</option></select></label>
        <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
        <button class="primary-button" type="submit" :disabled="busy !== null">{{ busy !== null ? '保存中…' : (editing.id ? '保存修改' : '新增地点') }}</button>
      </form>
    </BookingModal>
    <BookingModal v-if="removing" title="移除存放地点" compact :busy="busy !== null" @close="removing=null">
      <p>移除后，用户端将不再显示此地点，历史记录会保留。</p>
      <p class="remove-warning">如果地点内还有物品或未完成的预约、借还，系统会阻止移除。请先转移或移除物品并处理借还。</p>
      <p><strong>确定移除“{{ removing.name }}”？</strong></p>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <button class="primary-button" type="button" :disabled="busy !== null" @click="remove">{{ busy !== null ? '处理中…' : '确认移除' }}</button>
    </BookingModal>
  </div>
</template>
<style scoped>
.storage-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));gap:16px}.storage-card{background:#fff;border:1px solid #e5def2;border-radius:16px;overflow:hidden}.storage-photo{height:145px;background:#f2edfa;display:grid;place-items:center;color:#88749e;overflow:hidden}.storage-photo img{width:100%;height:145px;object-fit:cover;display:block}.storage-body{padding:18px;display:grid;gap:8px}.storage-body small{color:#786b91}.storage-body .field{margin:4px 0}.storage-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.remove-button{color:#a24352}.remove-warning{padding:12px;border-radius:10px;background:#fff4ed;color:#845536;line-height:1.6;font-size:13px}
</style>
