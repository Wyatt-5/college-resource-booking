<script setup>
import { onMounted, ref } from 'vue'
import { getReturnVerifications, submitReturnVerification } from '../lib/api'

const queue = ref([])
const loading = ref(false)
const error = ref('')
const banner = ref('')

// 每张卡片的核验表单状态：原因输入、提交中标记、幂等 key
const reasons = ref({})
const busyId = ref(null)
const idempotencyKeys = ref({})

function formatTime(iso) {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
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
  if (!idempotencyKeys.value[id]) idempotencyKeys.value[id] = crypto.randomUUID()
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

async function decide(submission, decision) {
  const reason = (reasons.value[submission.id] ?? '').trim()
  if (decision === 'REJECT' && !reason) return // 按钮已禁用，双保险
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
    banner.value = decision === 'ACCEPT'
      ? `「${submission.itemName}」核验通过，实物 ${submission.unitCode} 已恢复可借。`
      : `「${submission.itemName}」的归还已驳回，保持占用并等待用户重新提交。`
  } catch (cause) {
    banner.value = `处理失败：${cause.message}`
  } finally {
    busyId.value = null
  }
}

onMounted(loadQueue)
</script>

<template>
  <section class="return-verify">
    <div class="section-heading">
      <div>
        <p class="eyebrow">管理后台 · 借还管理</p>
        <h2>归还核验</h2>
      </div>
      <button type="button" class="refresh-button" :disabled="loading" @click="loadQueue">
        {{ loading ? '读取中…' : '重新读取' }}
      </button>
    </div>

    <p v-if="banner" class="success-banner" role="status">{{ banner }}</p>

    <p v-if="loading" class="state-card" role="status">正在读取待核验的归还申请…</p>
    <div v-else-if="error" class="state-card error-card" role="alert">
      <strong>暂时无法显示待核验列表</strong>
      <p>{{ error }}</p>
      <button type="button" class="retry-button" @click="loadQueue">重试</button>
    </div>
    <p v-else-if="queue.length === 0" class="state-card">当前没有待核验的归还申请。</p>

    <article v-for="submission in queue" :key="submission.id" class="verify-card">
      <div class="verify-main">
        <div class="verify-heading">
          <h3>{{ submission.itemName }}</h3>
          <span class="status-badge badge-pending">待核验</span>
        </div>
        <p class="verify-meta">
          借用人：{{ submission.borrower }} · 实物编号：<span class="unit-code">{{ submission.unitCode }}</span>
          · 提交时间：{{ formatTime(submission.submittedAt) }}
        </p>
        <p v-if="submission.remark" class="verify-remark">备注：{{ submission.remark }}</p>

        <div class="verify-photos" aria-label="归还现场照片">
          <figure
            v-for="photo in submission.photos"
            :key="photo.id"
            :class="['photo-placeholder', { 'photo-has-image': showableUrl(photo) }]"
          >
            <img
              v-if="showableUrl(photo)"
              :src="showableUrl(photo)"
              :alt="`归还照片：${photo.fileName}`"
              class="verify-photo"
              loading="lazy"
              @error="markBroken(photo.id)"
            >
            <span v-else class="photo-icon" aria-hidden="true">▧</span>
            <figcaption class="photo-name">{{ photo.fileName }}</figcaption>
          </figure>
        </div>

        <label class="verify-reason" :for="`reason-${submission.id}`">
          核验备注（驳回时必填）
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
            @click="decide(submission, 'ACCEPT')"
          >
            通过核验
          </button>
          <button
            type="button"
            class="danger-button"
            :disabled="busyId === submission.id || !(reasons[submission.id] ?? '').trim()"
            @click="decide(submission, 'REJECT')"
          >
            驳回
          </button>
        </div>
      </div>
    </article>
  </section>
</template>
