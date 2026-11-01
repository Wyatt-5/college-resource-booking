<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { createReservation, getSpaces, getSpaceSlots } from '../../lib/api'
import BookingModal from '../../components/BookingModal.vue'

const props = defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested'])
const spaces = ref([])
const selectedSpace = ref(null)
const selectedDate = ref('')
const slots = ref([])
const loading = ref(false)
const slotLoading = ref(false)
const slotError = ref('')
let slotVersion = 0
const saving = ref(false)
const error = ref('')
const formMessage = ref('')
const purpose = ref('')
const startHour = ref(8)
const endHour = ref(9)
const bands = [8, 10, 12, 14, 16, 18, 20]
const dateKey = (date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
const todayKey = dateKey(new Date())
const currentHour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', hourCycle: 'h23' }).format(new Date()))
const days = Array.from({ length: 8 }, (_, offset) => {
  const date = new Date(`${todayKey}T12:00:00+08:00`)
  date.setUTCDate(date.getUTCDate() + offset)
  return { key: dateKey(date), day: new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', day: 'numeric' }).format(date),
    month: new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: 'numeric' }).format(date),
    weekday: offset === 0 ? '今天' : new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', weekday: 'short' }).format(date) }
})
selectedDate.value = currentHour >= 21 ? days[1].key : todayKey
const hourText = (hour) => `${String(hour).padStart(2, '0')}:00`
const slotStatus = (hour) => slots.value.find((slot) => slot.hour === hour)?.status
const startOptions = computed(() => Array.from({ length: 14 }, (_, n) => n + 8).filter((hour) => selectedDate.value !== todayKey || hour > currentHour))
const endOptions = computed(() => Array.from({ length: 14 }, (_, n) => n + 9).filter((hour) => hour > startHour.value))
watch(startHour, (hour) => { if (endHour.value <= hour) endHour.value = Math.min(22, hour + 1) })

async function loadSpaces() {
  loading.value = true; error.value = ''
  try {
    spaces.value = await getSpaces()
    if (selectedSpace.value) selectedSpace.value = spaces.value.find((space) => space.id === selectedSpace.value.id) || null
  } catch (cause) { error.value = cause.message || '地点读取失败。' }
  finally { loading.value = false }
}
async function loadSlots() {
  if (!selectedSpace.value) return
  const version = ++slotVersion
  slotLoading.value = true; slotError.value = ''
  try { const result = await getSpaceSlots(selectedSpace.value.id, selectedDate.value); if (version === slotVersion) slots.value = result }
  catch (cause) { if (version === slotVersion) { slotError.value = cause.message || '预约表读取失败。'; slots.value = [] } }
  finally { if (version === slotVersion) slotLoading.value = false }
}
function closeSpace() { if (saving.value) return; ++slotVersion; selectedSpace.value = null }
onUnmounted(() => { ++slotVersion })
async function selectSpace(space) {
  selectedSpace.value = space
  selectedDate.value = currentHour >= 21 ? days[1].key : todayKey
  startHour.value = selectedDate.value === todayKey ? Math.max(8, currentHour + 1) : 8
  endHour.value = startHour.value + 1
  formMessage.value = ''
  purpose.value = ''
  await loadSlots()
}
async function selectDate(date) {
  selectedDate.value = date; formMessage.value = ''
  startHour.value = date === todayKey ? Math.max(8, currentHour + 1) : 8
  endHour.value = Math.min(22, startHour.value + 1)
  await loadSlots()
}
function chooseHour(hour) { startHour.value = hour; endHour.value = hour + 1; formMessage.value = '' }
async function submit() {
  if (saving.value || slotLoading.value || slotError.value) return
  if (!props.currentUser) { emit('login-requested'); return }
  if (!selectedSpace.value || !startOptions.value.includes(startHour.value) || !endOptions.value.includes(endHour.value)) {
    formMessage.value = '请选择有效的整点时段。'; return
  }
  if (Array.from({ length: endHour.value - startHour.value }, (_, i) => i + startHour.value)
    .some((hour) => slotStatus(hour) === 'CONFIRMED')) { formMessage.value = '选择的时段包含已预约时间，请更换。'; return }
  saving.value = true; formMessage.value = ''
  try {
    const result = await createReservation({ resourceType: 'SPACE', resourceId: selectedSpace.value.id,
      startAt: `${selectedDate.value}T${hourText(startHour.value)}:00+08:00`,
      endAt: `${selectedDate.value}T${hourText(endHour.value)}:00+08:00`, purpose: purpose.value, quantity: 1 }, crypto.randomUUID())
    formMessage.value = `申请 #${result.id} 已提交，等待审核。可在“我的预约”查看状态。`
    purpose.value = ''
    await loadSlots()
  } catch (cause) { formMessage.value = cause.message || '提交失败，请重试。' }
  finally { saving.value = false }
}
onMounted(loadSpaces)
</script>

<template>
  <section class="content-section space-page">
    <div class="section-heading">
      <div><p class="eyebrow">用户端 · 地点预约</p><h1>预约活动地点</h1></div>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadSpaces">刷新</button>
    </div>
    <p class="section-sub">查看照片与介绍，选择未来一周内 08:00–22:00 的整点时段。地点申请均需审核。</p>
    <p v-if="error" class="state-card error-card" role="alert">{{ error }}</p>
    <p v-if="loading" class="state-card" role="status">正在读取地点…</p>
    <p v-else-if="!spaces.length && !error" class="state-card">暂时没有开放预约的地点。</p>
    <div v-else class="space-cards">
      <article v-for="space in spaces" :key="space.id" class="space-card" :class="{ chosen: selectedSpace?.id === space.id }">
        <div class="space-cover"><img v-if="space.imageUrl" :src="space.imageUrl" :alt="`${space.name}的照片`"><span v-else aria-hidden="true">⌂</span></div>
        <div class="space-card-body">
          <div class="space-card-top"><h2>{{ space.name }}</h2><span>可容纳 {{ space.capacity }} 人</span></div>
          <p>{{ space.description || '暂无详细介绍。' }}</p>
          <button class="primary-button" type="button" @click="selectSpace(space)">查看时段并预约</button>
        </div>
      </article>
    </div>
    <BookingModal v-if="selectedSpace" :title="`${selectedSpace.name} · 预约`" :busy="saving" @close="closeSpace">
      <div class="space-booking-head"><div><p class="eyebrow">{{ selectedSpace.name }}</p><h2>选择日期与时间</h2></div><span>最早 08:00 · 最晚 22:00</span></div>
      <div class="space-calendar" role="group" aria-label="未来一周日期">
        <button v-for="day in days" :key="day.key" type="button" :disabled="saving" :class="{ active: selectedDate === day.key }" @click="selectDate(day.key)">
          <small>{{ day.weekday }}</small><strong>{{ day.day }}</strong><span>{{ day.month }}</span>
        </button>
      </div>
      <p class="space-hint">下方每行是两小时，每格是一小时。已确认的时段不可选；待审核时段仍可申请，由审核员按优先级处理。</p>
      <p v-if="slotLoading" class="space-hint" role="status">正在读取预约表…</p>
      <p v-else-if="slotError" class="error-card" role="alert">{{ slotError }} <button class="link-button" @click="loadSlots">重试</button></p>
      <div v-else class="space-timetable">
        <div v-for="band in bands" :key="band" class="space-band">
          <div class="space-band-label"><strong>{{ hourText(band) }}</strong><span>— {{ hourText(band + 2) }}</span></div>
          <button v-for="hour in [band, band + 1]" :key="hour" type="button" class="space-slot"
            :class="{ occupied: slotStatus(hour) === 'CONFIRMED', pending: slotStatus(hour) === 'PENDING_APPROVAL', selected: startHour === hour && endHour === hour + 1 }"
            :disabled="slotStatus(hour) === 'CONFIRMED' || (selectedDate === todayKey && hour <= currentHour)" @click="chooseHour(hour)">
            <strong>{{ hourText(hour) }}–{{ hourText(hour + 1) }}</strong>
            <span>{{ slotStatus(hour) === 'CONFIRMED' ? '已预约' : slotStatus(hour) === 'PENDING_APPROVAL' ? '待审核' : selectedDate === todayKey && hour <= currentHour ? '已过时' : '可申请' }}</span>
          </button>
        </div>
      </div>
      <form class="space-form" @submit.prevent="submit">
        <div class="field-row">
          <label class="field"><span>开始时间</span><select v-model.number="startHour" required><option v-for="hour in startOptions" :key="hour" :value="hour">{{ hourText(hour) }}</option></select></label>
          <label class="field"><span>结束时间</span><select v-model.number="endHour" required><option v-for="hour in endOptions" :key="hour" :value="hour">{{ hourText(hour) }}</option></select></label>
        </div>
        <label class="field"><span>活动用途</span><textarea v-model.trim="purpose" maxlength="300" rows="2" required placeholder="例如：青年志愿者协会例会"></textarea></label>
        <p v-if="formMessage" class="notice" role="status">{{ formMessage }}</p>
        <button class="primary-button" type="submit" :disabled="saving || slotLoading || !!slotError">{{ saving ? '提交中…' : '提交地点预约申请' }}</button>
      </form>
    </BookingModal>
  </section>
</template>

<style scoped>
.space-cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:20px;margin:26px 0 32px}
.space-card{background:#fff;border:1px solid #e5def2;border-radius:20px;overflow:hidden;box-shadow:0 8px 28px #33224f0a;transition:transform .2s,border-color .2s}.space-card:hover,.space-card.chosen{transform:translateY(-2px);border-color:#a891d5}
.space-cover{height:180px;background:linear-gradient(135deg,#f1edfa,#e2dbf2);display:grid;place-items:center;color:#755b9b;font-size:64px}.space-cover img{width:100%;height:100%;object-fit:cover}
.space-card-body{padding:20px}.space-card-top{display:flex;align-items:start;justify-content:space-between;gap:12px}.space-card h2{font-size:22px;margin:0;color:#281c47}.space-card-top span{font-size:13px;color:#786693;white-space:nowrap}.space-card p{color:#6b5f80;min-height:48px;line-height:1.6}.space-card .primary-button{width:100%}
.space-booking{background:#fff;border:1px solid #e4dcf1;border-radius:22px;padding:clamp(18px,3vw,32px);box-shadow:0 8px 28px #33224f0a}.space-booking-head{display:flex;justify-content:space-between;gap:16px;align-items:end}.space-booking-head h2{margin:6px 0 0;color:#281c47}.space-booking-head>span,.space-hint{font-size:14px;color:#796b8b}
.space-calendar{display:grid;grid-template-columns:repeat(8,minmax(65px,1fr));gap:8px;overflow-x:auto;padding:20px 0 8px}.space-calendar button{border:1px solid #e5def2;border-radius:13px;background:#faf8fd;color:#514269;min-height:78px;display:grid;place-items:center;cursor:pointer}.space-calendar button.active{background:#594284;color:#fff;border-color:#594284}.space-calendar button strong{font-size:20px}.space-calendar button small,.space-calendar button span{font-size:12px}
.space-hint{margin:12px 0 16px;line-height:1.6}.space-timetable{border:1px solid #eee8f5;border-radius:15px;overflow:hidden}.space-band{display:grid;grid-template-columns:110px repeat(2,minmax(0,1fr));gap:1px;background:#eee8f5;border-bottom:1px solid #eee8f5}.space-band:last-child{border-bottom:0}.space-band-label{background:#faf8fd;color:#77668d;display:flex;flex-direction:column;justify-content:center;padding:10px 16px}.space-band-label strong{color:#392852}
.space-slot{border:0;background:#fff;min-height:69px;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:12px 18px;text-align:left;color:#47365d;cursor:pointer}.space-slot span{border-radius:99px;background:#f0eafa;color:#5a4382;padding:5px 9px;font-size:12px;white-space:nowrap}.space-slot.occupied,.space-slot:disabled{background:#f6f4f8;color:#9b94a7;cursor:not-allowed}.space-slot.occupied span{background:#e8e4ed;color:#7f778c}.space-slot.pending span{background:#fff1df;color:#9b642e}.space-slot.selected{background:#f1ebfa}
.space-form{margin-top:24px;display:grid;gap:16px}.space-form .primary-button{justify-self:start}.space-form select{cursor:pointer}
@media(max-width:700px){.space-calendar{grid-template-columns:repeat(8,72px)}.space-booking-head{display:block}.space-band{grid-template-columns:75px repeat(2,minmax(0,1fr))}.space-band-label{padding:8px;font-size:12px}.space-slot{display:grid;justify-content:start;padding:8px;min-height:73px;font-size:12px}.space-slot span{padding:3px 6px}.space-form .primary-button{width:100%}}
</style>
