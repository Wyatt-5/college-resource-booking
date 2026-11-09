<script setup>
import { onMounted, ref } from 'vue'
import { createSpace, getSpaces, uploadSpaceImage, updateSpace, removeSpace } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'
import StorageLocationsManager from './StorageLocationsManager.vue'

const activeTab = ref('spaces')
const spaces = ref([])
const name = ref('')
const description = ref('')
const capacity = ref(20)
const photo = ref(null)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const message = ref('')
const creating=ref(false)
const editing=ref(null)
const editPhoto=ref(null)
function openEdit(space) { formError.value=''; editPhoto.value=null; editing.value={...space} }
async function saveEdit() {
  if (saving.value) return
  saving.value=true; formError.value=''; message.value=''
  try {
    const saved=await updateSpace(editing.value.id, {name:editing.value.name.trim(), description:editing.value.description.trim(), capacity:Number(editing.value.capacity)})
    if (editPhoto.value) {
      try { await uploadSpaceImage(saved.id, editPhoto.value) }
      catch (cause) { formError.value=`地点信息已保存，但照片更新失败：${cause.message}。可重试上传。`; await load(); return }
    }
    editing.value=null; editPhoto.value=null; message.value=`${saved.name}的信息已保存。`; await load()
  } catch(cause) { formError.value=cause.message||'保存地点失败。' }
  finally { saving.value=false }
}
async function remove(space) {
  if (saving.value || !window.confirm(`确定移除“${space.name}”？该地点将不再开放预约，历史记录会保留。`)) return
  saving.value=true; error.value=''; message.value=''
  try { await removeSpace(space.id); message.value=`${space.name}已移除。`; await load() }
  catch(cause) { error.value=cause.message||'移除地点失败。' }
  finally { saving.value=false }
}
async function load() {
  try { spaces.value = await getSpaces(); error.value = '' }
  catch (cause) { error.value = cause.message || '读取地点失败。' }
}
async function submit() {
  saving.value = true; formError.value = ''; message.value = ''
  try {
    const space = await createSpace({ name: name.value.trim(), description: description.value.trim(), capacity: Number(capacity.value) })
    let photoFailed = false
    if (photo.value) {
      try { await uploadSpaceImage(space.id, photo.value) }
      catch { photoFailed = true }
    }
    name.value = ''; description.value = ''; capacity.value = 20; photo.value = null; creating.value=false
    message.value = photoFailed ? `${space.name}已创建，但照片上传失败，可点击编辑地点重新上传。` : `${space.name}已加入地点预约。`
    await load()
  } catch (cause) { formError.value = cause.message || '新增地点失败，请重试。' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="content-section space-management">
    <div class="section-heading"><div><h1>地点管理</h1></div><div v-if="activeTab === 'spaces'" class="space-actions"><button class="refresh-button" type="button" @click="load">刷新</button><button class="primary-button" type="button" @click="formError=''; creating=true">新增地点</button></div></div>
    <div class="location-tabs" aria-label="地点类型">
      <button type="button" :class="{ active: activeTab === 'spaces' }" @click="activeTab='spaces'">预约地点</button>
      <button type="button" :class="{ active: activeTab === 'storage' }" @click="activeTab='storage'">办公室与物资室</button>
    </div>
    <StorageLocationsManager v-if="activeTab === 'storage'" />
    <template v-else>
    <p class="section-sub">新增可预约的会议室或其他活动地点，并上传展示照片。地点申请均进入审核工作台。</p>
    <BookingModal v-if="creating" title="新增地点" :busy="saving" @close="creating=false">
      <form class="space-create" @submit.prevent="submit">
      <div class="field-row"><label class="field"><span>名称</span><input v-model="name" type="text" maxlength="100" required placeholder="例如：会议室三"></label>
        <label class="field"><span>容纳人数</span><input v-model.number="capacity" type="number" min="1" max="1000" required></label></div>
      <label class="field"><span>地点介绍</span><textarea v-model="description" maxlength="500" rows="3" placeholder="位置、设备、适合的活动等"></textarea></label>
      <label class="field"><span>展示照片（JPG/PNG，最大 5 MB）</span><input type="file" accept="image/jpeg,image/png" @change="photo = $event.target.files?.[0] || null"></label>
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中…' : '新增地点' }}</button>
      </form>
    </BookingModal>
    <BookingModal v-if="editing" title="编辑地点" :busy="saving" @close="editing=null">
      <form class="space-create" @submit.prevent="saveEdit">
        <div class="field-row">
          <label class="field"><span>名称</span><input v-model.trim="editing.name" type="text" maxlength="100" required></label>
          <label class="field"><span>容纳人数</span><input v-model.number="editing.capacity" type="number" min="1" max="1000" required></label>
        </div>
        <label class="field"><span>地点介绍</span><textarea v-model="editing.description" maxlength="500" rows="3"></textarea></label>
        <label class="field"><span>更换照片（可选）</span><input type="file" accept="image/jpeg,image/png" @change="editPhoto=$event.target.files?.[0] || null"></label>
        <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
        <button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存修改' }}</button>
      </form>
    </BookingModal>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <div class="space-manage-grid">
      <article v-for="space in spaces" :key="space.id" class="item-card space-manage-card">
        <img v-if="space.imageUrl" :src="`${space.imageUrl}?v=${space.id}-${message}`" :alt="`${space.name}的照片`">
        <div v-else class="space-photo-empty">尚未上传照片</div>
        <div class="space-manage-body"><h3>{{ space.name }}</h3><p>{{ space.description || '暂无介绍' }}</p><small>可容纳 {{ space.capacity }} 人</small>
          <div class="space-actions"><button class="refresh-button" type="button" :disabled="saving" @click="openEdit(space)">编辑地点</button><button class="link-button remove-space" type="button" :disabled="saving" @click="remove(space)">移除地点</button></div>
        </div>
      </article>
    </div>
    </template>
  </section>
</template>

<style scoped>
.location-tabs{display:flex;gap:8px;margin:18px 0 22px}.location-tabs button{border:1px solid #ded5ed;background:#fff;color:#584575;border-radius:10px;padding:10px 16px}.location-tabs button.active{background:#43316d;color:#fff;border-color:#43316d}.space-actions{display:flex;gap:10px;flex-wrap:wrap}.remove-space{color:#a23c36}.space-create{display:grid;gap:16px}.space-create .field input,.space-create .field textarea{box-sizing:border-box;width:100%;border:1px solid #d4c6ec;border-radius:9px;background:#fcfaff;color:#35254f;padding:12px 14px;font:inherit}.space-create .field input[type=file]{background:#fff}.space-create .primary-button{justify-self:start}.space-manage-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:18px;margin-top:24px}.space-manage-card>img,.space-photo-empty{width:100%;height:170px;object-fit:cover;background:#f2edfa}.space-photo-empty{display:grid;place-items:center;color:#88749e}.space-manage-body{padding:20px;display:grid;gap:10px}.space-manage-body h3,.space-manage-body p{margin:0}.space-manage-body small{color:#746586}.space-manage-body .refresh-button{justify-self:start}
@media(max-width:600px){.space-create .primary-button{width:100%}}
</style>
