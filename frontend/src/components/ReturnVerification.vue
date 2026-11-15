<script setup>
import { onMounted, ref } from 'vue'
import { getReturnVerifications, submitReturnVerification } from '../lib/api'
import { createRequestKey } from '../lib/requestKey'
import BookingModal from './BookingModal.vue'

const queue = ref([])
const loading = ref(false)
const error = ref('')
const banner = ref('')
const bannerType = ref('success')
const pendingDecision = ref(null)
const selectedPhoto = ref(null)

// 每张卡片的核验表单状态：原因输入、提交中标记、幂等 key
const reasons = ref({})
const busyId = ref(null)
const idempotencyKeys = ref({})

function formatTime(iso) {
  return new Date(iso).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
}

// 契约里 ReturnPhoto.url 是可选项（受权限控制的图片地址），联调阶段由后端提供。
// 后端未返回 url 或图片加载失败时退回占位图，避免出现破图。
const brokenPhotos = ref(new Set())

function showableUrl(photo) {
  if (!photo.url || brokenPhotos.value.has(photo.id)) return ''
  return photo.url
}

function markBroken(photoId) {
  brokenPhotos.value = new Set(brokenPhotos.value).add(photoId)
}

function keyFor(id) {
  if (!idempotencyKeys.value[id]) idempotencyKeys.value[id] = createRequestKey()
  return idempotencyKeys.value[id]
}

async function loadQueue() {
  loading.value = true
  error.value = ''
  try {
    queue.value = await getReturnVerifications()
  } catch (cause) {
    queue.value = []
    error.value = cause instanceof Error ? cause.message : '读取待核验列表失败。'
  } finally {
    loading.value = false
  }
}

function requestDecision(submission, decision) {
  const reason = (reasons.value[submission.id] ?? '').trim()
  if (decision === 'REJECT' && !reason) return // 按钮已禁用，双保险
  pendingDecision.value = { submission, decision, reason }
}

async function decide() {
  if (!pendingDecision.value) return
  const { submission, decision, reason } = pendingDecision.value
  busyId.value = submission.id
  banner.value = ''
  try {
    await submitReturnVerification(
      submission.id,
      { decision, reason },
      { idempotencyKey: keyFor(submission.id) },
    )
    queue.value = queue.value.filter((entry) => entry.id !== submission.id)
    delete reasons.value[submission.id]
    delete idempotencyKeys.value[submission.id]
    pendingDecision.value = null
    bannerType.value = 'success'
    banner.value = decision === 'ACCEPT'
      ? `「${submission.itemName}」核验通过，实物 ${submission.unitCodes.join('、')} 已恢复可借。`
      : `「${submission.itemName}」的归还已驳回，保持占用并等待用户重新提交。`
  } catch (cause) {
    bannerType.value = 'error'
    banner.value = `处理失败：${cause.message}`
  } finally {
    busyId.value = null
  }
}

onMounted(loadQueue)
</script>

<template>
  <section class="return-verify">
    <BookingModal v-if="selectedPhoto" :title="selectedPhoto.fileName || '归还现场照片'" @close="selectedPhoto = null">
      <img class="photo-lightbox" :src="showableUrl(selectedPhoto)" :alt="`归还照片：${selectedPhoto.fileName}`">
    </BookingModal>
    <BookingModal v-if="pendingDecision" :title="pendingDecision.decision === 'ACCEPT' ? '确认通过核验' : '确认驳回归还'" compact :busy="busyId !== null" @close="pendingDecision = null">
      <div class="decision-summary">
        <strong>{{ pendingDecision.submission.itemName }}</strong>
        <p>实物编号：<span class="unit-code">{{ pendingDecision.submission.unitCodes.join('、') }}</span></p>
        <p v-if="pendingDecision.decision === 'ACCEPT'">通过后，本次借还将完成，以上实物立即恢复可借。</p>
        <template v-else><p>驳回后实物继续保持占用，用户需补拍并重新提交。</p><p class="decision-reason">驳回原因：{{ pendingDecision.reason }}</p></template>
      </div>
      <div class="decision-actions">
        <button type="button" class="refresh-button" :disabled="busyId !== null" @click="pendingDecision = null">返回检查</button>
        <button type="button" :class="pendingDecision.decision === 'ACCEPT' ? 'primary-button' : 'danger-button'" :disabled="busyId !== null" @click="decide">
          {{ busyId !== null ? '处理中…' : (pendingDecision.decision === 'ACCEPT' ? '确认通过并恢复库存' : '确认驳回') }}
        </button>
      </div>
    </BookingModal>
    <div class="section-heading">
      <div>

        <h2>归还核验</h2>
        <p class="section-sub">逐张核对现场照片和备注；通过后库存立即恢复，驳回后等待用户补拍。</p>
      </div>
      <button type="button" class="refresh-button" :disabled="loading" @click="loadQueue">
        {{ loading ? '读取中…' : '重新读取' }}
      </button>
    </div>

    <p v-if="banner" :class="bannerType === 'error' ? 'state-card error-card' : 'success-banner'" :role="bannerType === 'error' ? 'alert' : 'status'">{{ banner }}</p>

    <p v-if="loading" class="state-card" role="status">正在读取待核验的归还申请…</p>
    <div v-else-if="error" class="state-card error-card" role="alert">
      <strong>暂时无法显示待核验列表</strong>
      <p>{{ error }}</p>
      <button type="button" class="retry-button" @click="loadQueue">重试</button>
    </div>
    <p v-else-if="queue.length === 0" class="state-card">当前没有待核验的归还申请。</p>
    <p v-else class="queue-summary">待核验 {{ queue.length }} 项，请先确认照片、实物编号和归还备注。</p>

    <article v-for="submission in queue" :key="submission.id" class="verify-card">
      <div class="verify-main">
        <div class="verify-heading">
          <h3>{{ submission.itemName }}</h3>
          <span class="status-badge badge-pending">待核验</span>
        </div>
        <p class="verify-meta">
          借用人：{{ submission.borrower }} · 实物编号：<span class="unit-code">{{ submission.unitCodes.join('、') }}</span>
          · 提交时间：{{ formatTime(submission.submittedAt) }}
        </p>
        <p v-if="submission.remark" class="verify-remark">备注：{{ submission.remark }}</p>

        <div class="verify-photos" aria-label="归还现场照片">
          <figure
            v-for="photo in submission.photos"
            :key="photo.id"
            :class="['photo-placeholder', { 'photo-has-image': showableUrl(photo) }]"
          >
            <button v-if="showableUrl(photo)" type="button" class="photo-preview-button" :aria-label="`放大查看 ${photo.fileName}`" @click="selectedPhoto = photo">
              <img :src="showableUrl(photo)" :alt="`归还照片：${photo.fileName}`" class="verify-photo" loading="lazy" @error="markBroken(photo.id)">
              <span>点击放大</span>
            </button>
            <span v-else class="photo-icon" aria-hidden="true">照片暂不可用</span>
            <figcaption class="photo-name">{{ photo.fileName }}</figcaption>
          </figure>
        </div>

        <label class="verify-reason" :for="`reason-${submission.id}`">
          核验意见（驳回时必填，通过时可选）
        </label>
        <textarea
          :id="`reason-${submission.id}`"
          v-model="reasons[submission.id]"
          rows="2"
          maxlength="200"
          placeholder="例如：实物与照片一致 / 配件缺失，需补充归还"
        ></textarea>

        <div class="verify-actions">
          <button
            type="button"
            class="primary-button"
            :disabled="busyId === submission.id"
            @click="requestDecision(submission, 'ACCEPT')"
          >
            通过核验
          </button>
          <button
            type="button"
            class="danger-button"
            :disabled="busyId === submission.id || !(reasons[submission.id] ?? '').trim()"
            @click="requestDecision(submission, 'REJECT')"
          >
            驳回
          </button>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.queue-summary{margin:0 0 14px;color:#665b7e;font-size:13px}.photo-preview-button{position:relative;display:flex;flex:1 1 auto;width:100%;min-height:0;padding:0;border:0;background:#f3f0fb;cursor:zoom-in;overflow:hidden}.photo-preview-button span{position:absolute;right:5px;bottom:5px;border-radius:20px;padding:3px 7px;background:#2f2350d9;color:#fff;font-size:10px}.photo-lightbox{display:block;width:100%;max-height:70vh;object-fit:contain;border-radius:10px;background:#f5f2f9}.decision-summary{display:grid;gap:9px}.decision-summary>strong{font-size:17px;color:#2f2350}.decision-summary p{margin:0;color:#5d5478;line-height:1.65}.decision-reason{border-radius:9px;padding:10px 12px;background:#fff3ee;color:#8f3a25!important}.decision-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px;flex-wrap:wrap}.photo-placeholder{height:auto;min-height:150px;aspect-ratio:auto}.photo-name{min-height:20px;word-break:normal;overflow-wrap:anywhere}.photo-icon{padding:20px 8px;font-size:12px}.verify-remark{border-left:3px solid #d5c6ed;padding-left:9px;line-height:1.6}
@media(max-width:560px){.decision-actions button{flex:1 1 100%}.verify-photos{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.photo-placeholder{width:auto;min-height:130px}}
</style>
