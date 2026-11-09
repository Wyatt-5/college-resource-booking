<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getLocations, uploadLocationImage, updateLocation, removeLocation } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'
const locations=ref([]), files=ref({}), busy=ref(null), error=ref(''), message=ref(''), imageVersion=ref(Date.now())
const editing=ref(null), removing=ref(null), confirmName=ref(''), modalError=ref('')
const form=reactive({name:'',type:'OFFICE'})
async function load(){try{locations.value=await getLocations()}catch(e){error.value=e.message||'地点读取失败'}}
async function upload(location){
  const file=files.value[location.id];if(!file||busy.value!==null)return
  busy.value=location.id;error.value='';message.value=''
  try{await uploadLocationImage(location.id,file);files.value[location.id]=null;imageVersion.value=Date.now();await load();message.value=`${location.name}的照片已更新。`}
  catch(e){error.value=e.message||'照片上传失败'}finally{busy.value=null}
}
function openEdit(location){editing.value=location;form.name=location.name;form.type=location.type;modalError.value=''}
function openRemove(location){removing.value=location;confirmName.value='';modalError.value=''}
async function save(){
  if(busy.value!==null)return
  busy.value=editing.value.id;modalError.value=''
  try{await updateLocation(editing.value.id,{name:form.name,type:form.type});editing.value=null;await load();message.value='地点信息已更新。'}catch(e){modalError.value=e.message||'保存失败'}finally{busy.value=null}
}
async function remove(){
  if(busy.value!==null||confirmName.value!==removing.value.name)return
  if(!window.confirm(`确定移除“${removing.value.name}”吗？移除后用户端将不再显示。`))return
  busy.value=removing.value.id;modalError.value=''
  try{await removeLocation(removing.value.id);removing.value=null;await load();message.value='地点已移除，历史记录仍会保留。'}catch(e){modalError.value=e.message||'移除失败'}finally{busy.value=null}
}
onMounted(load)
</script>
<template>
  <div class="storage-locations">
    <p class="section-sub">管理办公室和物资室的信息与展示照片。</p>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p><p v-if="message" class="notice" role="status">{{ message }}</p>
    <p v-if="locations.length === 0" class="state-card">暂无存放地点。</p>
    <div class="storage-grid">
      <article v-for="location in locations.filter(entry => entry.status === 'ACTIVE')" :key="location.id" class="storage-card">
        <div class="storage-photo"><img v-if="location.imageUrl" :src="`${location.imageUrl}?v=${imageVersion}`" :alt="location.name"><span v-else>暂无照片</span></div>
        <div class="storage-body"><strong>{{ location.name }}</strong><small>{{ location.type === 'OFFICE' ? '办公室' : '物资室' }}</small>
          <label class="field"><span>展示照片（JPG/PNG，最大 5 MB）</span><input type="file" accept="image/jpeg,image/png" @change="files[location.id]=$event.target.files?.[0] || null"></label>
          <div class="storage-actions"><button class="refresh-button" type="button" :disabled="!files[location.id] || busy !== null" @click="upload(location)">{{ busy === location.id ? '上传中…' : '保存照片' }}</button>
          <button class="refresh-button" type="button" @click="openEdit(location)">编辑</button><button class="link-button remove-button" type="button" @click="openRemove(location)">移除</button></div>
        </div>
      </article>
    </div>
    <BookingModal v-if="editing" title="编辑存放地点" compact :busy="busy !== null" @close="editing=null">
      <form @submit.prevent="save">
        <label class="field"><span>地点名称</span><input v-model.trim="form.name" type="text" maxlength="100" required></label>
        <label class="field"><span>地点类型</span><select v-model="form.type"><option value="OFFICE">办公室</option><option value="STORAGE">物资室</option></select></label>
        <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
        <button class="primary-button" type="submit" :disabled="busy !== null">{{ busy !== null ? '保存中…' : '保存修改' }}</button>
      </form>
    </BookingModal>
    <BookingModal v-if="removing" title="移除存放地点" compact :busy="busy !== null" @close="removing=null">
      <p>移除后，用户端将不再显示此地点，历史记录会保留。</p>
      <p class="remove-warning">如果地点内还有物品或未完成的预约、借还，系统会阻止移除。请先转移或移除物品并处理借还。</p>
      <label class="field"><span>请输入地点名称“{{ removing.name }}”以确认</span><input v-model="confirmName" type="text" autocomplete="off"></label>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <button class="primary-button" type="button" :disabled="confirmName !== removing.name || busy !== null" @click="remove">{{ busy !== null ? '处理中…' : '确认移除' }}</button>
    </BookingModal>
  </div>
</template>
<style scoped>
.storage-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));gap:16px}.storage-card{background:#fff;border:1px solid #e5def2;border-radius:16px;overflow:hidden}.storage-photo{height:145px;background:#f2edfa;display:grid;place-items:center;color:#88749e;overflow:hidden}.storage-photo img{width:100%;height:145px;object-fit:cover;display:block}.storage-body{padding:18px;display:grid;gap:8px}.storage-body small{color:#786b91}.storage-body .field{margin:4px 0}.storage-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.remove-button{color:#a24352}.remove-warning{padding:12px;border-radius:10px;background:#fff4ed;color:#845536;line-height:1.6;font-size:13px}
</style>
