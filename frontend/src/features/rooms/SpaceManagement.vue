<script setup>
import { onMounted, ref } from 'vue'
import { createSpace, getSpaces, uploadSpaceImage } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'

const spaces = ref([])
const name = ref('')
const description = ref('')
const capacity = ref(20)
const photo = ref(null)
const uploads = ref({})
const saving = ref(false)
const error = ref('')
const formError = ref('')
const message = ref('')
const creating=ref(false)
async function load() {
  try { spaces.value = await getSpaces() }
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
    message.value = photoFailed ? `${space.name}已创建，但照片上传失败，可在下方重新上传。` : `${space.name}已加入地点预约。`
    await load()
  } catch (cause) { formError.value = cause.message || '新增地点失败，请重试。' }
  finally { saving.value = false }
}
async function replacePhoto(space) {
  const file = uploads.value[space.id]
  if (!file) return
  saving.value = true; error.value = ''; message.value = ''
  try { await uploadSpaceImage(space.id, file); uploads.value[space.id] = null; message.value = `${space.name}的照片已更新。`; await load() }
  catch (cause) { error.value = cause.message || '照片上传失败。' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="content-section space-management">
    <div class="section-heading"><div><p class="eyebrow">管理端 · 地点预约</p><h1>地点管理</h1></div><div class="space-actions"><button class="refresh-button" type="button" @click="load">刷新</button><button class="primary-button" type="button" @click="formError=''; creating=true">新增地点</button></div></div>
    <p class="section-sub">新增可预约的会议室或其他活动地点，并上传展示照片。地点申请均进入审核工作台。</p>
    <BookingModal v-if="creating" title="新增地点" :busy="saving" @close="creating=false">
      <form class="space-create" @submit.prevent="submit">
      <div class="field-row"><label class="field"><span>名称</span><input v-model="name" maxlength="100" required placeholder="例如：会议室三"></label>
        <label class="field"><span>容纳人数</span><input v-model.number="capacity" type="number" min="1" max="1000" required></label></div>
      <label class="field"><span>地点介绍</span><textarea v-model="description" maxlength="500" rows="3" placeholder="位置、设备、适合的活动等"></textarea></label>
      <label class="field"><span>展示照片（JPG/PNG，最大 5 MB）</span><input type="file" accept="image/jpeg,image/png" @change="photo = $event.target.files?.[0] || null"></label>
      <p v-if="formError" class="field-error" role="alert">{{ formError }}</p>
      <button class="primary-button" type="submit" :disabled="saving">{{ saving ? '保存中…' : '新增地点' }}</button>
      </form>
    </BookingModal>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <div class="space-manage-grid">
      <article v-for="space in spaces" :key="space.id" class="item-card space-manage-card">
        <img v-if="space.imageUrl" :src="`${space.imageUrl}?v=${space.id}-${message}`" :alt="`${space.name}的照片`">
        <div v-else class="space-photo-empty">尚未上传照片</div>
        <div class="space-manage-body"><h3>{{ space.name }}</h3><p>{{ space.description || '暂无介绍' }}</p><small>可容纳 {{ space.capacity }} 人</small>
          <label class="field"><span>更换照片</span><input type="file" accept="image/jpeg,image/png" @change="uploads[space.id] = $event.target.files?.[0] || null"></label>
          <button class="refresh-button" type="button" :disabled="saving || !uploads[space.id]" @click="replacePhoto(space)">上传照片</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.space-actions{display:flex;gap:10px}.space-create{display:grid;gap:16px}.space-create .field input,.space-create .field textarea{box-sizing:border-box;width:100%;border:1px solid #d4c6ec;border-radius:9px;background:#fcfaff;color:#35254f;padding:12px 14px;font:inherit}.space-create .field input[type=file]{background:#fff}.space-create .primary-button{justify-self:start}.space-manage-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:18px;margin-top:24px}.space-manage-card>img,.space-photo-empty{width:100%;height:170px;object-fit:cover;background:#f2edfa}.space-photo-empty{display:grid;place-items:center;color:#88749e}.space-manage-body{padding:20px;display:grid;gap:10px}.space-manage-body h3,.space-manage-body p{margin:0}.space-manage-body small{color:#746586}.space-manage-body .refresh-button{justify-self:start}
@media(max-width:600px){.space-create .primary-button{width:100%}}
</style>
