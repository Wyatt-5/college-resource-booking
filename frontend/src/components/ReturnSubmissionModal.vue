<script setup>
import { onUnmounted, ref, watch } from 'vue'
import BookingModal from './BookingModal.vue'
import { submitReturnSubmission } from '../lib/api'
import { createRequestKey } from '../lib/requestKey'

const props = defineProps({ reservation: { type: Object, required: true } })
const emit = defineEmits(['close', 'submitted'])
const files = ref([])
const previews = ref([])
const remark = ref('')
const error = ref('')
const submitting = ref(false)
let requestKey = null

function clearPreviews() {
  for (const entry of previews.value) URL.revokeObjectURL(entry.url)
  previews.value = []
}

function choose(event) {
  clearPreviews()
  files.value = Array.from(event.target.files || [])
  previews.value = files.value.map(file => ({ file, url: URL.createObjectURL(file) }))
  requestKey = null
  error.value = files.value.length > 6 ? '最多上传6张照片。' : ''
}

watch(remark, () => { requestKey = null })
onUnmounted(clearPreviews)

async function submit() {
  if (!files.value.length || files.value.length > 6) {
    error.value = '请上传1至6张归还照片。'
    return
  }
  if (files.value.some(file => file.size > 5 * 1024 * 1024)) {
    error.value = '单张照片不得超过5MB。'
    return
  }
  if (remark.value.trim().length > 300) {
    error.value = '备注最长300字。'
    return
  }
  if (!requestKey) requestKey = createRequestKey()
  submitting.value = true
  error.value = ''
  try {
    const result = await submitReturnSubmission(props.reservation.id,
      { photos: files.value, remark: remark.value }, requestKey)
    emit('submitted', result)
  } catch (cause) {
    error.value = cause.message || '提交归还失败。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BookingModal :title="`提交归还 · ${reservation.itemName}`" @close="emit('close')">
    <p class="return-help">请拍摄能看清物品整体及主要配件的现场照片。管理员核验通过前，实物不会恢复可借。</p>
    <label class="return-upload">
      归还照片（1–6张，单张不超过5MB）
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="choose">
    </label>
    <div v-if="previews.length" class="return-previews">
      <figure v-for="entry in previews" :key="`${entry.file.name}-${entry.file.lastModified}`">
        <img :src="entry.url" :alt="entry.file.name">
        <figcaption>{{ entry.file.name }}</figcaption>
      </figure>
    </div>
    <label class="return-remark">
      归还备注（可选）
      <textarea v-model="remark" rows="3" maxlength="300" placeholder="例如：配件齐全，机身右侧有轻微划痕"></textarea>
    </label>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <div class="return-actions">
      <button type="button" class="refresh-button" :disabled="submitting" @click="emit('close')">取消</button>
      <button type="button" class="primary-button" :disabled="submitting" @click="submit">
        {{ submitting ? '上传中…' : '提交归还' }}
      </button>
    </div>
  </BookingModal>
</template>

<style scoped>
.return-help{color:#665b7e;line-height:1.7}.return-upload,.return-remark{display:grid;gap:8px;font-weight:700;color:#3d3358}.return-upload input,.return-remark textarea{font:inherit}.return-remark{margin-top:16px}.return-remark textarea{border:1px solid #d3cbe8;border-radius:9px;padding:11px 13px;resize:vertical}.return-previews{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:10px;margin-top:14px}.return-previews figure{margin:0;min-width:0}.return-previews img{width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:9px}.return-previews figcaption{margin-top:5px;font-size:12px;color:#776b91;overflow-wrap:anywhere}.return-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px;flex-wrap:wrap}
</style>
