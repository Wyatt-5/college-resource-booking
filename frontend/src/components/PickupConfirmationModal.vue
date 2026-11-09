<script setup>
import { ref, watch } from 'vue'
import BookingModal from './BookingModal.vue'
import { pickupReservation } from '../lib/api'
import { createRequestKey } from '../lib/requestKey'

const props = defineProps({ reservation: { type: Object, required: true } })
const emit = defineEmits(['close', 'submitted'])
const unitCodes = ref(Array.from({ length: props.reservation.quantity }, () => ''))
const submitting = ref(false)
const error = ref('')
let requestKey = null

watch(unitCodes, () => { requestKey = null; error.value = '' }, { deep: true })

async function submit() {
  const codes = unitCodes.value.map(value => value.trim())
  if (codes.some(code => !code) || new Set(codes).size !== codes.length) {
    error.value = '请填写与领取数量一致且不重复的实物编号。'
    return
  }
  if (!requestKey) requestKey = createRequestKey()
  submitting.value = true
  error.value = ''
  try {
    const result = await pickupReservation(props.reservation.id, codes, requestKey)
    emit('submitted', result)
  } catch (cause) {
    error.value = cause.message || '确认领取失败。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BookingModal :title="`确认领取 · ${reservation.itemName}`" @close="emit('close')">
    <p class="pickup-help">请按实物标签填写编号。预约数量为 {{ reservation.quantity }}，提交后编号将与本次预约绑定。</p>
    <div class="pickup-fields">
      <label v-for="(_, index) in unitCodes" :key="index">
        第 {{ index + 1 }} 件实物编号
        <input v-model="unitCodes[index]" type="text" maxlength="100" autocomplete="off" :placeholder="`例如 ITEM-${reservation.resourceId}-${index + 1}`">
      </label>
    </div>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <div class="pickup-actions">
      <button type="button" class="refresh-button" :disabled="submitting" @click="emit('close')">取消</button>
      <button type="button" class="primary-button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认已经领到' }}
      </button>
    </div>
  </BookingModal>
</template>

<style scoped>
.pickup-help{color:#665b7e;line-height:1.7}.pickup-fields{display:grid;gap:14px}.pickup-fields label{display:grid;gap:7px;font-weight:700;color:#3d3358}.pickup-fields input{border:1px solid #d3cbe8;border-radius:9px;padding:11px 13px;font:inherit}.pickup-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px;flex-wrap:wrap}
</style>
