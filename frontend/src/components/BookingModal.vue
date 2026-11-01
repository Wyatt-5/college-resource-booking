<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
defineProps({ title: { type: String, required: true }, busy: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
let previousOverflow
let previousFocus
onMounted(() => {
  previousFocus = document.activeElement
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value.showModal()
})
onUnmounted(() => {
  document.body.style.overflow = previousOverflow
  if (previousFocus?.isConnected) previousFocus.focus()
})
function backdrop(event) {
  if (event.target !== dialog.value) return
  const box = dialog.value.getBoundingClientRect()
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="booking-dialog" :aria-label="title" @cancel.prevent="!busy && emit('close')" @click="!busy && backdrop($event)">
      <header class="booking-dialog-head"><h2>{{ title }}</h2><button class="refresh-button" type="button" :disabled="busy" @click="emit('close')">关闭</button></header>
      <div class="booking-dialog-body"><slot /></div>
    </dialog>
  </Teleport>
</template>

<style scoped>
.booking-dialog{box-sizing:border-box;width:min(850px,calc(100vw - 32px));max-height:90dvh;margin:auto;padding:0;border:1px solid #e5def2;border-radius:22px;background:#fff;color:#2d2148;box-shadow:0 24px 90px #21143740;overflow:auto}
.booking-dialog::backdrop{background:#24173788;backdrop-filter:blur(3px)}
.booking-dialog-head{position:sticky;top:0;z-index:2;display:flex;align-items:center;justify-content:space-between;gap:16px;background:#fff;padding:18px 26px;border-bottom:1px solid #eee8f5}.booking-dialog-head h2{margin:0;font-size:22px}.booking-dialog-body{padding:22px 26px}
@media(max-width:600px){.booking-dialog{width:calc(100vw - 20px);max-height:94dvh;border-radius:16px}.booking-dialog-head{padding:14px 16px}.booking-dialog-head h2{font-size:19px}.booking-dialog-body{padding:16px}}
</style>
