<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  createItem,
  createItemUnit,
  deleteItemImage,
  FALLBACK_LOCATIONS,
  getLocations,
  uploadItemImage,
} from '../lib/api'

const MAX_PHOTOS = 6
const MAX_PHOTO_SIZE = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const steps = ['基本信息', '展示照片', '实物编号']

function emptyForm() {
  return {
    name: '',
    locationId: '',
    description: '',
    isValuable: false,
    status: 'ACTIVE',
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

const unitInput = ref('')
const units = ref([])
const unitError = ref('')
const unitSaving = ref(false)

const statusEnabled = computed({
  get: () => form.value.status === 'ACTIVE',
  set: (enabled) => {
    form.value.status = enabled ? 'ACTIVE' : 'INACTIVE'
  },
})

// 步骤条同一时刻只能有一个 active：第 2、3 步同时可见，因此按“是否已有照片、
// 是否已有实物编号”判断进度，而不是创建完成后把所有后续步骤都标成 active。
function stepClass(index) {
  if (!createdItem.value) return index === 0 ? 'active' : ''
  if (index === 0) return 'done'
  if (index === 1) return images.value.length ? 'done' : 'active'
  if (units.value.length) return 'done'
  return images.value.length ? 'active' : ''
}

function locationName(locationId) {
  const match = locations.value.find((item) => item.id === Number(locationId))
  return match?.name ?? `地点 ${locationId}`
}

async function loadLocations() {
  locationsError.value = ''
  try {
    locations.value = await getLocations()
  } catch {
    // 后端未启动时使用兜底模拟地点，保证上架表单可以离线演示
    locations.value = FALLBACK_LOCATIONS
    locationsError.value = '暂时无法获取地点列表，当前显示示例地点。'
  }
}

async function submitBasic() {
  finishMessage.value = ''
  submitError.value = ''
  fieldErrors.value = {}

  if (!form.value.name.trim()) fieldErrors.value.name = '请输入物品名称'
  if (!form.value.locationId) fieldErrors.value.locationId = '请选择归属地点'
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
  const entry = {
    localId: (localImageSeq += 1),
    previewUrl: URL.createObjectURL(file),
    fileName: file.name,
    uploading: true,
  }
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

async function addUnit() {
  const code = unitInput.value.trim()
  if (!code || unitSaving.value) return
  unitError.value = ''
  if (units.value.some((unit) => unit.unitCode === code)) {
    unitError.value = '该编号已录入，请勿重复。'
    return
  }
  unitSaving.value = true
  try {
    const unit = await createItemUnit(createdItem.value.id, code)
    units.value.push(unit)
    unitInput.value = ''
  } catch (cause) {
    unitError.value = cause.message || '录入失败，请重试。'
  } finally {
    unitSaving.value = false
  }
}

function resetAll() {
  for (const image of images.value) URL.revokeObjectURL(image.previewUrl)
  finishMessage.value = `「${createdItem.value.name}」上架完成，共 ${images.value.length} 张照片、${units.value.length} 件实物。`
  form.value = emptyForm()
  createdItem.value = null
  images.value = []
  units.value = []
  unitInput.value = ''
  idempotencyKey.value = crypto.randomUUID()
}

onMounted(loadLocations)
</script>

<template>
  <section class="item-create">
    <div class="section-heading">
      <div>
        <p class="eyebrow">管理后台 · 物品管理</p>
        <h2>物品上架</h2>
      </div>
      <ol class="step-indicator" aria-label="上架步骤">
        <li v-for="(label, index) in steps" :key="label" :class="stepClass(index)">
          <span class="step-num">{{ index + 1 }}</span>{{ label }}
        </li>
      </ol>
    </div>

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
          </div>
          <div class="field">
            <label for="advance-days">可提前预约（天）</label>
            <input id="advance-days" v-model.number="form.rules.advanceDays" type="number" min="0" max="60">
          </div>
        </div>
      </fieldset>

      <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>

      <button type="submit" class="primary-button" :disabled="submitting">
        {{ submitting ? '保存中…' : '保存基本信息' }}
      </button>
      <p class="form-tip">保存基本信息后，才能上传照片和录入实物编号。</p>
    </form>

    <template v-else>
      <!-- 已创建物品摘要 -->
      <div class="created-summary">
        <div>
          <p class="eyebrow">已创建 · #{{ createdItem.id }}</p>
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

      <!-- 第三步：实物编号 -->
      <div class="form-card">
        <div class="card-heading">
          <h3>实物编号</h3>
          <span class="card-hint">每件库存一个唯一编号，交接时按编号绑定</span>
        </div>
        <div class="unit-input-row">
          <input
            v-model="unitInput"
            type="text"
            maxlength="40"
            placeholder="例如 CAM-2026-001"
            @keyup.enter.prevent="addUnit"
          >
          <button
            type="button"
            class="refresh-button"
            :disabled="unitSaving || !unitInput.trim()"
            @click="addUnit"
          >
            {{ unitSaving ? '录入中…' : '录入编号' }}
          </button>
        </div>
        <p v-if="unitError" class="field-error">{{ unitError }}</p>
        <ul v-if="units.length" class="unit-list">
          <li v-for="unit in units" :key="unit.id">
            <span class="unit-code">{{ unit.unitCode }}</span>
            <span class="unit-status">可借</span>
          </li>
        </ul>
        <p v-else class="card-hint">还没有录入实物编号，可以稍后在「库存管理」中补录。</p>
      </div>

      <button type="button" class="primary-button" @click="resetAll">完成上架，继续下一件</button>
    </template>
  </section>
</template>
