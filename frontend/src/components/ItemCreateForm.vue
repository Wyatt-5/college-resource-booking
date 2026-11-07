<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  createItem,
  deleteItemImage,
  FALLBACK_LOCATIONS,
  getLocations,
  uploadItemImage,
  MANAGEMENT_DEMO,
} from '../lib/api'

const MAX_PHOTOS = 6
const MAX_PHOTO_SIZE = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const steps = ['基本信息与库存', '展示照片']
const emit = defineEmits(['created', 'busy-change'])

function emptyForm() {
  return {
    name: '',
    locationId: '',
    description: '',
    isValuable: false,
    status: 'ACTIVE',
    initialStock: 1,
    rules: { maxBorrowDays: 7, advanceDays: 14 },
  }
}

const form = ref(emptyForm())
const createdItem = ref(null)
const submitting = ref(false)
const submitError = ref('')
const fieldErrors = ref({})
// 防重复提交：一次表单会话一个 key，完成上架后重新生成
const idempotencyKey = ref(crypto.randomUUID())
const finishMessage = ref('')

const locations = ref([])
const locationsError = ref('')

const images = ref([])
const photoError = ref('')
const fileInput = ref(null)
let localImageSeq = 0
watch(() => submitting.value || images.value.some(image => image.uploading), busy => emit('busy-change', busy), { immediate: true })


const statusEnabled = computed({
  get: () => form.value.status === 'ACTIVE',
  set: (enabled) => {
    form.value.status = enabled ? 'ACTIVE' : 'INACTIVE'
  },
})

// 基本信息与库存同一次保存，照片上传是第二步。
function stepClass(index) {
  if (!createdItem.value) return index === 0 ? 'active' : ''
  if (index === 0) return 'done'
  return images.value.length ? 'done' : 'active'
}

function locationName(locationId) {
  const match = locations.value.find((item) => item.id === Number(locationId))
  return match?.name ?? `地点 ${locationId}`
}

async function loadLocations() {
  locationsError.value = ''
  try {
    locations.value = (await getLocations()).filter(location => location.status === 'ACTIVE')
  } catch {
    // 后端未启动时使用兜底模拟地点，保证上架表单可以离线演示
    locations.value = MANAGEMENT_DEMO ? FALLBACK_LOCATIONS : []
    locationsError.value = MANAGEMENT_DEMO ? '当前显示演示地点。' : '地点读取失败，请启动后端后重试，不能使用示例地点上架。'
  }
}

async function submitBasic() {
  finishMessage.value = ''
  submitError.value = ''
  fieldErrors.value = {}

  if (!form.value.name.trim()) fieldErrors.value.name = '请输入物品名称'
  if (!form.value.locationId) fieldErrors.value.locationId = '请选择归属地点'
  if (!Number.isInteger(form.value.initialStock) || form.value.initialStock < 0 || form.value.initialStock > 500) fieldErrors.value.initialStock = '请输入0至500的整数'
  if (Object.keys(fieldErrors.value).length > 0) return

  submitting.value = true
  try {
    const payload = {
      ...form.value,
      name: form.value.name.trim(),
      locationId: Number(form.value.locationId),
    }
    createdItem.value = await createItem(payload, { idempotencyKey: idempotencyKey.value })
  } catch (cause) {
    if (cause.code === 'VALIDATION_ERROR' && cause.details) {
      fieldErrors.value = cause.details
    }
    submitError.value = cause.message || '保存失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

function onPickPhotos(event) {
  photoError.value = ''
  const files = Array.from(event.target.files ?? [])
  event.target.value = ''
  if (files.length === 0) return
  if (images.value.length + files.length > MAX_PHOTOS) {
    photoError.value = `最多上传 ${MAX_PHOTOS} 张照片。`
    return
  }
  for (const file of files) {
    if (!ACCEPTED_TYPES.includes(file.type)) {
      photoError.value = `「${file.name}」格式不支持，仅限 JPG / PNG / WebP。`
      continue
    }
    if (file.size > MAX_PHOTO_SIZE) {
      photoError.value = `「${file.name}」超过 5MB，请压缩后再传。`
      continue
    }
    uploadPhoto(file)
  }
}

async function uploadPhoto(file) {
  const entry = reactive({
    localId: (localImageSeq += 1),
    previewUrl: URL.createObjectURL(file),
    fileName: file.name,
    uploading: true,
  })
  images.value.push(entry)
  try {
    const saved = await uploadItemImage(createdItem.value.id, file)
    entry.id = saved.id
  } catch (cause) {
    photoError.value = `「${file.name}」上传失败：${cause.message}`
    images.value = images.value.filter((item) => item !== entry)
    URL.revokeObjectURL(entry.previewUrl)
  } finally {
    entry.uploading = false
  }
}

async function removePhoto(image) {
  if (image.id != null) {
    try {
      await deleteItemImage(createdItem.value.id, image.id)
    } catch (cause) {
      photoError.value = `移除照片失败：${cause.message}`
      return
    }
  }
  URL.revokeObjectURL(image.previewUrl)
  images.value = images.value.filter((item) => item !== image)
}

function resetAll() {
  if (images.value.some(image => image.uploading)) return
  for (const image of images.value) URL.revokeObjectURL(image.previewUrl)
  finishMessage.value = `「${createdItem.value.name}」上架完成，共 ${images.value.length} 张照片、${createdItem.value.unitSummary.total} 件实物。`
  form.value = emptyForm()
  createdItem.value = null
  images.value = []
  idempotencyKey.value = crypto.randomUUID()
  emit('created')
}

onMounted(loadLocations)
onUnmounted(() => { for (const image of images.value) URL.revokeObjectURL(image.previewUrl) })
</script>

<template>
  <section class="item-create">
    <ol class="step-indicator" aria-label="上架步骤">
      <li v-for="(label, index) in steps" :key="label" :class="stepClass(index)">
        <span class="step-num">{{ index + 1 }}</span>{{ label }}
      </li>
    </ol>

    <p v-if="finishMessage" class="success-banner" role="status">{{ finishMessage }}</p>

    <!-- 第一步：基本信息 -->
    <form v-if="!createdItem" class="form-card" novalidate @submit.prevent="submitBasic">
      <div class="field">
        <label for="item-name">物品名称 <em>*</em></label>
        <input
          id="item-name"
          v-model="form.name"
          type="text"
          maxlength="50"
          placeholder="例如：佳能相机 EOS R50"
        >
        <p v-if="fieldErrors.name" class="field-error">{{ fieldErrors.name }}</p>
      </div>

      <div class="field">
        <label for="item-location">归属地点 <em>*</em></label>
        <select id="item-location" v-model="form.locationId">
          <option value="" disabled>请选择存放地点</option>
          <option v-for="location in locations" :key="location.id" :value="location.id">
            {{ location.name }}
          </option>
        </select>
        <p v-if="locationsError" class="locations-notice">
          {{ locationsError }}
          <button type="button" class="link-button" @click="loadLocations">重试</button>
        </p>
        <p v-else-if="fieldErrors.locationId" class="field-error">{{ fieldErrors.locationId }}</p>
      </div>

      <div class="field">
        <label for="item-desc">物品介绍</label>
        <textarea
          id="item-desc"
          v-model="form.description"
          rows="3"
          maxlength="300"
          placeholder="给用户看的借用说明（可选）"
        ></textarea>
      </div>

      <div class="field">
        <label for="initial-stock">初始库存数量 <em>*</em></label>
        <input id="initial-stock" v-model.number="form.initialStock" type="number" min="0" max="500" step="1">
        <p v-if="fieldErrors.initialStock" class="field-error">{{ fieldErrors.initialStock }}</p>
        <p class="card-hint">系统会自动给每件实物生成唯一编号。</p>
      </div>

      <div class="field-row">
        <label class="toggle-field">
          <input v-model="form.isValuable" type="checkbox" class="toggle-input">
          <span class="toggle-track" aria-hidden="true"></span>
          <span>
            <strong>贵重物品</strong>
            <small v-if="form.isValuable" class="toggle-hint">用户预约需审核员审批后才生效</small>
            <small v-else class="toggle-hint">普通物品确认后自动占用名额</small>
          </span>
        </label>

        <label class="toggle-field">
          <input v-model="statusEnabled" type="checkbox" class="toggle-input">
          <span class="toggle-track" aria-hidden="true"></span>
          <span>
            <strong>启用状态</strong>
            <small class="toggle-hint">停用后用户端不可预约</small>
          </span>
        </label>
      </div>

      <fieldset class="rules-fieldset">
        <legend>可借规则</legend>
        <div class="field-row">
          <div class="field">
            <label for="max-days">单次最长借用（天）</label>
            <input id="max-days" v-model.number="form.rules.maxBorrowDays" type="number" min="1" max="90">
            <p v-if="fieldErrors['rules.maxBorrowDays']" class="field-error">{{ fieldErrors['rules.maxBorrowDays'] }}</p>
          </div>
          <div class="field">
            <label for="advance-days">可提前预约（天）</label>
            <input id="advance-days" v-model.number="form.rules.advanceDays" type="number" min="0" max="60">
            <p v-if="fieldErrors['rules.advanceDays']" class="field-error">{{ fieldErrors['rules.advanceDays'] }}</p>
          </div>
        </div>
      </fieldset>

      <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>

      <button type="submit" class="primary-button" :disabled="submitting">
        {{ submitting ? '保存中…' : '保存基本信息' }}
      </button>
      <p class="form-tip">保存后库存和实物编号立即生效，然后可以上传照片。</p>
    </form>

    <template v-else>
      <!-- 已创建物品摘要 -->
      <div class="created-summary">
        <div>
          <p class="eyebrow">已创建</p>
          <h3>{{ createdItem.name }}</h3>
          <p>
            {{ locationName(createdItem.locationId) }} ·
            {{ createdItem.isValuable ? '贵重物品（预约需审批）' : '普通物品' }} ·
            {{ createdItem.status === 'ACTIVE' ? '已启用' : '已停用' }}
          </p>
        </div>
      </div>

      <!-- 第二步：展示照片 -->
      <div class="form-card">
        <div class="card-heading">
          <h3>展示照片</h3>
          <span class="card-hint">JPG / PNG / WebP，单张不超过 5MB，最多 {{ MAX_PHOTOS }} 张</span>
        </div>
        <div class="photo-grid">
          <figure v-for="image in images" :key="image.localId" class="photo-item">
            <img :src="image.previewUrl" :alt="image.fileName">
            <span v-if="image.uploading" class="photo-mask">上传中…</span>
            <button
              v-else
              type="button"
              class="photo-remove"
              :aria-label="`移除照片 ${image.fileName}`"
              @click="removePhoto(image)"
            >×</button>
          </figure>
          <button
            v-if="images.length < MAX_PHOTOS"
            type="button"
            class="photo-add"
            @click="fileInput.click()"
          >
            ＋ 添加照片
          </button>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          hidden
          @change="onPickPhotos"
        >
        <p v-if="photoError" class="field-error photo-error">{{ photoError }}</p>
      </div>

      <button type="button" class="primary-button" :disabled="images.some(image => image.uploading)" @click="resetAll">完成上架</button>
    </template>
  </section>
</template>
