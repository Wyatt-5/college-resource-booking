<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
const props = defineProps({ title: { type: String, required: true }, busy: Boolean, compact: Boolean, disableBackdropClose: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
const closing = ref(false)
let previousOverflow
let previousFocus
let previousPaddingRight
let closeTimer
// 呼出弹窗时锁定背景滚动会隐藏滚动条，导致页面宽度突变（"蠕动"）。
// 测量滚动条宽度并用 padding-right 补偿，保持布局宽度不变。
function lockScroll() {
  const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
  previousOverflow = document.body.style.overflow
  previousPaddingRight = document.body.style.paddingRight
  document.body.style.overflow = 'hidden'
  if (scrollbarWidth > 0) document.body.style.paddingRight = `${scrollbarWidth}px`
}
function unlockScroll() {
  document.body.style.overflow = previousOverflow
  document.body.style.paddingRight = previousPaddingRight
}
onMounted(() => {
  previousFocus = document.activeElement
  lockScroll()
  dialog.value.showModal()
  // 触发进入过渡：下一帧切换显示态，让 CSS transition 生效
  requestAnimationFrame(() => { dialog.value?.classList.add('is-open') })
})
onUnmounted(() => {
  clearTimeout(closeTimer)
  unlockScroll()
  if (previousFocus?.isConnected) previousFocus.focus()
})
function backdrop(event) {
  if (props.disableBackdropClose || event.target !== dialog.value) return
  const box = dialog.value.getBoundingClientRect()
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) requestClose()
}
// 统一关闭入口：先移除进入态播放退出动画，动画结束后再真正关闭
function requestClose() {
  if (closing.value || props.busy) return
  closing.value = true
  dialog.value?.classList.remove('is-open')
  closeTimer = setTimeout(() => emit('close'), 270)
}
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="booking-dialog" :class="{ compact, closing }" :aria-label="title" @cancel.prevent="!busy && requestClose()" @click="!busy && backdrop($event)">
      <header class="booking-dialog-head"><h2>{{ title }}</h2><button class="refresh-button" type="button" :disabled="busy" @click="requestClose()">关闭</button></header>
      <div class="booking-dialog-body"><slot /></div>
    </dialog>
  </Teleport>
</template>

<style scoped>
/* 呼出式卡片：从下方中间浮起，关闭时沿原路径沉回（从哪里来、回哪里去） */
.booking-dialog{box-sizing:border-box;width:min(850px,calc(100vw - 32px));max-height:90dvh;margin:auto;padding:0;border:1px solid #e5def2;border-radius:22px;background:#fff;color:#2d2148;box-shadow:0 24px 90px #21143740;overflow:auto;overscroll-behavior:contain;opacity:0;transform:translateY(48px) scale(.92);transition:opacity .26s cubic-bezier(.4,0,.2,1),transform .26s cubic-bezier(.4,0,.2,1)}
.booking-dialog.is-open{opacity:1;transform:translateY(0) scale(1)}
.booking-dialog.closing{opacity:0;transform:translateY(48px) scale(.92)}
.booking-dialog.compact{width:min(460px,calc(100vw - 32px));height:auto;max-height:90dvh;margin:auto;border:1px solid #e5def2;border-radius:18px;inset:0}
.booking-dialog::backdrop{background:#24173788;backdrop-filter:blur(3px);animation:backdrop-in .26s ease}
.booking-dialog.closing::backdrop{animation:backdrop-out .26s ease forwards}
@keyframes backdrop-in{from{opacity:0}to{opacity:1}}
@keyframes backdrop-out{from{opacity:1}to{opacity:0}}
.booking-dialog-head{position:sticky;top:0;z-index:2;display:flex;align-items:center;justify-content:space-between;gap:16px;background:#fff;padding:18px 26px;border-bottom:1px solid #eee8f5}.booking-dialog-head h2{margin:0;font-size:22px}.booking-dialog-body{padding:22px 26px}
/* 手机端把预约弹窗变成整屏面板：内容多时整页一起滚，不再出现弹窗里套时间表的两层滚动条 */
@media(max-width:600px){
  .booking-dialog{width:100%;max-width:none;height:100dvh;max-height:100dvh;margin:0;border:0;border-radius:0;inset:0;transform:translateY(100%);transition:opacity .26s cubic-bezier(.4,0,.2,1),transform .26s cubic-bezier(.4,0,.2,1)}
  .booking-dialog.is-open{transform:translateY(0)}
  .booking-dialog.closing{transform:translateY(100%)}
  .booking-dialog-head{padding:calc(12px + env(safe-area-inset-top)) 16px 12px;gap:12px}
  .booking-dialog-head h2{font-size:18px;line-height:1.35;overflow-wrap:anywhere}
  .booking-dialog-head .refresh-button{flex:0 0 auto;min-height:40px;padding:8px 16px}
  .booking-dialog-body{padding:16px 16px calc(24px + env(safe-area-inset-bottom))}
}
</style>
