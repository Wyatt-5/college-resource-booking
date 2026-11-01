<template>
  <div class="reservation-panel">
    <h3>预约这件物品</h3>
    <p v-if="item.isValuable" class="section-sub">贵重物品提交后进入待审核，审核通过前不占用库存。</p>
    <p v-else class="section-sub">普通物品有时段库存时立即确认；同时提交按服务端处理顺序判定。</p>
    <p v-if="item.unitSummary.available < 1" class="field-error">目前没有可借实物，请等待管理员录入或恢复库存。</p>
    <template v-else-if="!currentUser">
      <p class="section-sub">请先登录，再提交预约。</p>
      <button type="button" class="primary-button" @click="emit('login-requested')">去登录</button>
    </template>
    <form v-else @submit.prevent="submit" @input="onInput">
      <div class="field-row">
        <label class="field"><span>开始时间</span>
          <input v-model="startAt" type="datetime-local" required>
        </label>
        <label class="field"><span>结束时间</span>
          <input v-model="endAt" type="datetime-local" required>
        </label>
      </div>
      <label class="field"><span>数量</span>
        <input v-model.number="quantity" type="number" min="1" :max="item.unitSummary.available" required>
      </label>
      <label class="field"><span>借用用途</span>
        <textarea v-model.trim="purpose" maxlength="300" rows="2" required placeholder="例如：学院活动摄影"></textarea>
      </label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="notice" role="status">{{ successMessage }}</p>
      <button class="primary-button" type="submit" :disabled="submitting || !!successMessage">
        {{ submitting ? '提交中…' : '提交预约' }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { createReservation } from '../../lib/api'

const props = defineProps({ item: { type: Object, required: true }, currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested', 'created'])
const startAt = ref('')
const endAt = ref('')
const quantity = ref(1)
const purpose = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
let requestKey = null
function onInput() {
  requestKey = null
  successMessage.value = ''
}

async function submit() {
  if (submitting.value) return
  const start = new Date(startAt.value)
  const end = new Date(endAt.value)
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
    errorMessage.value = '请选择有效的开始和结束时间。'
    return
  }
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > props.item.unitSummary.available) {
    errorMessage.value = '预约数量无效。'
    return
  }
  if (!requestKey) requestKey = crypto.randomUUID()
  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const reservation = await createReservation({
      resourceType: 'ITEM',
      resourceId: props.item.id,
      startAt: start.toISOString(),
      endAt: end.toISOString(),
      quantity: quantity.value,
      purpose: purpose.value,
    }, requestKey)
    successMessage.value = reservation.status === 'CONFIRMED'
      ? `预约 #${reservation.id} 已确认，可在“我的预约”查看。`
      : `申请 #${reservation.id} 已提交，等待审核。`
    requestKey = null
    emit('created', reservation)
  } catch (error) {
    errorMessage.value = error.message || '预约提交失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>
