<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { createItem, getLocations, uploadItemImage, MANAGEMENT_DEMO, FALLBACK_LOCATIONS } from '../lib/api'
import { createRequestKey } from '../lib/requestKey'
const MAX_PHOTOS = 6, MAX_PHOTO_SIZE = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const steps = ['基本信息与库存', '展示照片']
const emit = defineEmits(['created', 'busy-change'])
const form = ref({ name:'', locationId:'', description:'', isValuable:false, status:'ACTIVE', initialStock:1, rules:{maxBorrowDays:7,advanceDays:14} })
const step = ref(0), createdItem = ref(null), submitting = ref(false), submitError = ref(''), fieldErrors = ref({})
const locations = ref([]), locationsError = ref(''), images = ref([]), photoError = ref(''), fileInput = ref(null)
const idempotencyKey = createRequestKey()
let localImageSeq = 0
watch(submitting, busy => emit('busy-change', busy), { immediate:true })
const statusEnabled = computed({get:()=>form.value.status==='ACTIVE',set:enabled=>{form.value.status=enabled?'ACTIVE':'INACTIVE'}})
function stepClass(index) { return index === step.value ? 'active' : index < step.value ? 'done' : '' }
function locationName(id) { return locations.value.find(location => location.id === Number(id))?.name ?? `地点 ${id}` }
async function loadLocations() {
  try { locations.value=(await getLocations()).filter(location=>location.status==='ACTIVE'); locationsError.value='' }
  catch { locations.value=MANAGEMENT_DEMO?FALLBACK_LOCATIONS:[]; locationsError.value='地点读取失败，请检查网络后重试。' }
}
function submitBasic() {
  submitError.value='';fieldErrors.value={}
  if(!form.value.name.trim())fieldErrors.value.name='请输入物品名称'
  if(!form.value.locationId)fieldErrors.value.locationId='请选择归属地点'
  if(!Number.isInteger(form.value.initialStock)||form.value.initialStock<0||form.value.initialStock>500)fieldErrors.value.initialStock='请输入0至500的整数'
  if(Object.keys(fieldErrors.value).length===0)step.value=1
}
function onPickPhotos(event) {
  photoError.value='';const files=Array.from(event.target.files??[]);event.target.value=''
  if(images.value.length+files.length>MAX_PHOTOS){photoError.value=`最多上传 ${MAX_PHOTOS} 张照片。`;return}
  for(const file of files){
    if(!ACCEPTED_TYPES.includes(file.type)){photoError.value=`「${file.name}」格式不支持，仅限 JPG / PNG / WebP。`;continue}
    if(file.size>MAX_PHOTO_SIZE){photoError.value=`「${file.name}」超过 5MB，请压缩后再传。`;continue}
    images.value.push({localId:++localImageSeq,file,previewUrl:URL.createObjectURL(file),fileName:file.name,uploaded:false})
  }
}
function removePhoto(image){if(createdItem.value && image.uploaded){photoError.value='已上传的照片请在库存管理中编辑。';return} URL.revokeObjectURL(image.previewUrl);images.value=images.value.filter(entry=>entry!==image)}
async function finish() {
  if(submitting.value)return
  submitting.value=true;submitError.value='';photoError.value=''
  try {
    if(!createdItem.value)createdItem.value=await createItem({...form.value,name:form.value.name.trim(),locationId:Number(form.value.locationId)},{idempotencyKey})
    for(const image of images.value){if(!image.uploaded){await uploadItemImage(createdItem.value.id,image.file);image.uploaded=true}}
    emit('created')
  }catch(cause){
    if(!createdItem.value && cause.code==='VALIDATION_ERROR' && cause.details){fieldErrors.value=cause.details;step.value=0}
    submitError.value=createdItem.value ? `物品已保存，但照片上传未完成：${cause.message}。可重试；关闭后可在库存管理中继续添加照片。` : (cause.message||'保存失败，请重试。')
  }finally{submitting.value=false}
}
onMounted(loadLocations)
onUnmounted(()=>{for(const image of images.value)URL.revokeObjectURL(image.previewUrl)})
</script>

<template>
  <section class="item-create">
    <ol class="step-indicator" aria-label="上架步骤">
      <li v-for="(label, index) in steps" :key="label" :class="stepClass(index)">
        <span class="step-num">{{ index + 1 }}</span>{{ label }}
      </li>
    </ol>



    <!-- 第一步：基本信息 -->
    <form v-if="step === 0" class="form-card" novalidate @submit.prevent="submitBasic">
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
        下一步：选择照片
      </button>
      <p class="form-tip">下一步可选择照片，最后点击完成上架才会创建物品。</p>
    </form>

    <template v-else>
      <!-- 已创建物品摘要 -->
      <div class="created-summary">
        <div>
          <p class="eyebrow">待确认</p>
          <h3>{{ form.name }}</h3>
          <p>
            {{ locationName(form.locationId) }} ·
            {{ form.isValuable ? '贵重物品（预约需审批）' : '普通物品' }} ·
            {{ form.status === 'ACTIVE' ? '已启用' : '已停用' }}
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

            <button
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

      <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>
      <div class="field-row">
        <button type="button" class="refresh-button" :disabled="submitting || !!createdItem" @click="step = 0">上一步</button>
        <button type="button" class="primary-button" :disabled="submitting" @click="finish">{{ submitting ? '正在保存…' : (createdItem ? '重试上传照片' : '确认并完成上架') }}</button>
      </div>
    </template>
  </section>
</template>
