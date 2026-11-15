<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
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
const MAX_PHOTOS = 6
const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const isResubmission = computed(() => props.reservation.handoverStatus === 'RETURN_REJECTED')
const modalTitle = computed(() => `${isResubmission.value ? '补拍并重新提交' : '提交归还'} · ${props.reservation.itemName}`)

function clearPreviews() {
  for (const entry of previews.value) URL.revokeObjectURL(entry.url)
  previews.value = []
}

function rebuildPreviews() {
  clearPreviews()
  previews.value = files.value.map(file => ({ file, url: URL.createObjectURL(file) }))
}

function choose(event) {
  const selected = Array.from(event.target.files || [])
  event.target.value = ''
  const invalid = selected.find(file => !ACCEPTED_TYPES.has(file.type))
  if (invalid) { error.value = `「${invalid.name}」格式不支持，请选择 JPG、PNG 或 WebP。`; return }
  const oversized = selected.find(file => file.size > MAX_BYTES)
  if (oversized) { error.value = `「${oversized.name}」超过5MB，请压缩后重试。`; return }
  const known = new Set(files.value.map(file => `${file.name}:${file.size}:${file.lastModified}`))
  const additions = selected.filter(file => !known.has(`${file.name}:${file.size}:${file.lastModified}`))
  if (files.value.length + additions.length > MAX_PHOTOS) { error.value = '最多上传6张照片。'; return }
  files.value = [...files.value, ...additions]
  rebuildPreviews()
  requestKey = null
  error.value = ''
}

function removePhoto(index) {
  files.value = files.value.filter((_, current) => current !== index)
  rebuildPreviews()
  requestKey = null
  error.value = ''
}

watch(remark, () => { requestKey = null })
onUnmounted(clearPreviews)

async function submit() {
  if (!files.value.length || files.value.length > MAX_PHOTOS) {
    error.value = '请上传1至6张归还照片。'
    return
  }
  if (files.value.some(file => file.size > MAX_BYTES)) {
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
      { photos: files.value, remark: remark.value.trim() }, requestKey)
    emit('submitted', result)
  } catch (cause) {
    error.value = cause.message || '提交归还失败。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BookingModal :title="modalTitle" @close="emit('close')">
    <div v-if="isResubmission" class="return-rejection" role="alert">
      <strong>上次核验未通过</strong>
      <p>{{ reservation.lastReturnReason || '请补充更清晰、完整的现场照片。' }}</p>
    </div>
    <p class="return-help">请拍摄能看清物品整体及主要配件的现场照片。管理员核验通过前，实物不会恢复可借。</p>
    <label class="return-upload">
      <span>归还照片（1–6张，单张不超过5MB） <b>{{ files.length }}/6</b></span>
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="choose">
      <small>可分多次选择；手机浏览器可直接拍照。重复文件不会重复加入。</small>
    </label>
    <div v-if="previews.length" class="return-previews">
      <figure v-for="(entry, index) in previews" :key="`${entry.file.name}-${entry.file.lastModified}`">
        <img :src="entry.url" :alt="entry.file.name">
        <button type="button" :aria-label="`移除照片 ${entry.file.name}`" @click="removePhoto(index)">×</button>
        <figcaption><span>{{ entry.file.name }}</span><small>{{ Math.max(1, Math.round(entry.file.size / 1024)) }} KB</small></figcaption>
      </figure>
    </div>
    <label class="return-remark">
      <span>归还备注（可选） <small>{{ remark.length }}/300</small></span>
      <textarea v-model="remark" rows="3" maxlength="300" placeholder="例如：配件齐全，机身右侧有轻微划痕"></textarea>
    </label>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <div class="return-actions">
      <button type="button" class="refresh-button" :disabled="submitting" @click="emit('close')">取消</button>
      <button type="button" class="primary-button" :disabled="submitting" @click="submit">
        {{ submitting ? '上传中…' : (isResubmission ? '提交补拍材料' : '提交归还') }}
      </button>
    </div>
  </BookingModal>
</template>

<style scoped>
.return-rejection{border:1px solid #efc1b4;border-radius:11px;padding:12px 14px;background:#fff5f1;color:#8f3a25}.return-rejection p{margin:5px 0 0;line-height:1.6}
.return-help{color:#665b7e;line-height:1.7}.return-upload,.return-remark{display:grid;gap:8px;font-weight:700;color:#3d3358}.return-upload span,.return-remark span{display:flex;justify-content:space-between;gap:12px}.return-upload b{color:#60438f}.return-upload small,.return-remark small{color:#8b809d;font-size:12px;font-weight:400}.return-upload input,.return-remark textarea{font:inherit}.return-remark{margin-top:16px}.return-remark textarea{border:1px solid #d3cbe8;border-radius:9px;padding:11px 13px;resize:vertical}.return-previews{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:10px;margin-top:14px}.return-previews figure{position:relative;margin:0;min-width:0;border:1px solid #e1daec;border-radius:10px;overflow:hidden;background:#faf8fd}.return-previews img{display:block;width:100%;aspect-ratio:4/3;object-fit:cover}.return-previews figure>button{position:absolute;top:6px;right:6px;display:grid;place-items:center;width:28px;height:28px;border:0;border-radius:50%;background:#2f2350d9;color:#fff;font-size:18px;cursor:pointer}.return-previews figcaption{display:grid;gap:2px;padding:7px 8px;font-size:12px;color:#776b91;overflow-wrap:anywhere}.return-previews figcaption span{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.return-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px;flex-wrap:wrap}
@media(max-width:560px){.return-actions button{flex:1 1 auto}.return-previews{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
