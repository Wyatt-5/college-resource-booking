<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { createReservation, getItemAvailability } from '../../lib/api'

const props = defineProps({ item: { type: Object, required: true }, currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested', 'created'])
const dateKey = date => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
const chinaHour = date => Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', hourCycle: 'h23' }).format(date))
const addDays = (date, days) => dateKey(new Date(new Date(`${date}T12:00:00+08:00`).getTime() + days * 86400000))
const hourText = hour => `${String(hour).padStart(2, '0')}:00`
const instant = (date, hour) => new Date(`${date}T${hourText(hour)}:00+08:00`).getTime()
const today = dateKey(new Date())
const first = new Date((Math.floor(Date.now() / 3600000) + 1) * 3600000)
const startDate = ref(dateKey(first))
const startHour = ref(chinaHour(first))
const next = new Date(first.getTime() + 3600000)
const endDate = ref(dateKey(next))
const endHour = ref(chinaHour(next))
const viewDate = ref(startDate.value)
const lastStartDate = computed(() => addDays(today, props.item.rules.advanceDays ?? 0))
const lastEndDate = computed(() => addDays(startDate.value, props.item.rules.maxBorrowDays ?? 0))
const lastViewDate = computed(() => addDays(lastStartDate.value, props.item.rules.maxBorrowDays ?? 0))
const hours = Array.from({ length: 24 }, (_, i) => i)
const bands = Array.from({ length: 12 }, (_, i) => i * 2)
const quantity = ref(1)
const purpose = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const availability = ref(null)
const calendarLoading = ref(false)
const calendarError = ref('')
let requestKey = null
let version = 0
const slot = hour => availability.value?.slots.find(entry => entry.hour === hour)
const past = hour => instant(viewDate.value, hour) <= Date.now()
const selected = hour => instant(viewDate.value, hour) >= instant(startDate.value, startHour.value)
  && instant(viewDate.value, hour) < instant(endDate.value, endHour.value)
const startHours = computed(() => hours.filter(hour => instant(startDate.value, hour) > Date.now()))
const endHours = computed(() => hours.filter(hour => instant(endDate.value, hour) > instant(startDate.value, startHour.value)))

async function loadAvailability() {
  const current = ++version
  calendarLoading.value = true; calendarError.value = ''
  try {
    const result = await getItemAvailability(props.item.id, viewDate.value)
    if (version === current) availability.value = result
  } catch (cause) {
    if (version === current) { calendarError.value = cause.message || '读取时段失败。'; availability.value = null }
  } finally { if (version === current) calendarLoading.value = false }
}
watch(viewDate, loadAvailability)
watch([startDate, startHour], () => {
  const start = instant(startDate.value, startHour.value)
  if (instant(endDate.value, endHour.value) <= start) {
    const following = new Date(start + 3600000)
    endDate.value = dateKey(following); endHour.value = chinaHour(following)
  }
})
watch([startDate, startHour, endDate, endHour, quantity, purpose], () => {
  requestKey = null; successMessage.value = ''; errorMessage.value = ''
})
function chooseHour(hour) {
  startDate.value = viewDate.value; startHour.value = hour
  const following = new Date(instant(viewDate.value, hour) + 3600000)
  endDate.value = dateKey(following); endHour.value = chinaHour(following)
}
async function submit() {
  if (submitting.value || calendarLoading.value || calendarError.value) return
  const start = instant(startDate.value, startHour.value)
  const end = instant(endDate.value, endHour.value)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start <= Date.now() || end <= start) {
    errorMessage.value = '请选择未来的整点开始时间，结束时间须更晚。'; return
  }
  if (props.item.rules.advanceDays == null || props.item.rules.maxBorrowDays == null) {
    errorMessage.value = '请管理员先配置预约规则。'; return
  }
  if (startDate.value > lastStartDate.value || end - start > props.item.rules.maxBorrowDays * 86400000) {
    errorMessage.value = '预约时间超出该物品的提前预约或最长借用限制。'; return
  }
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > props.item.unitSummary.available) {
    errorMessage.value = '预约数量无效。'; return
  }
  if (!requestKey) requestKey = crypto.randomUUID()
  submitting.value = true; errorMessage.value = ''; successMessage.value = ''
  try {
    const reservation = await createReservation({ resourceType: 'ITEM', resourceId: props.item.id,
      startAt: new Date(start).toISOString(), endAt: new Date(end).toISOString(),
      quantity: quantity.value, purpose: purpose.value }, requestKey)
    successMessage.value = reservation.status === 'CONFIRMED'
      ? `预约 #${reservation.id} 已确认。` : `申请 #${reservation.id} 已提交，等待其他审核员处理。`
    emit('created', reservation)
    await loadAvailability()
  } catch (cause) { errorMessage.value = cause.message || '预约提交失败，请稍后重试。'; await loadAvailability() }
  finally { submitting.value = false }
}
onMounted(loadAvailability)
onUnmounted(() => { ++version })
</script>

<template>
  <div class="item-booking">
    <h3>选择借用时段</h3>
    <p class="booking-help">开始和结束均按整点选择，支持跨天。跨夜期间也会持续占用库存。</p>
    <p class="booking-help">{{ item.isValuable ? '贵重物品需其他审核员审批，待审申请暂不占用库存。' : '普通物品提交时，系统会检查整个借用期间的可用库存。' }}</p>
    <div class="calendar-toolbar">
      <button class="refresh-button" type="button" aria-label="查看前一天占用" :disabled="viewDate <= today || submitting" @click="viewDate = addDays(viewDate, -1)">‹</button>
      <label class="field"><span>查看当天库存占用</span><input v-model="viewDate" type="date" :min="today" :max="lastViewDate" :disabled="submitting" required></label>
      <button class="refresh-button" type="button" aria-label="查看后一天占用" :disabled="viewDate >= lastViewDate || submitting" @click="viewDate = addDays(viewDate, 1)">›</button>
    </div>
    <p v-if="calendarLoading" role="status" class="booking-help">正在读取时段库存…</p>
    <p v-else-if="calendarError" class="error-card" role="alert">{{ calendarError }} <button class="link-button" @click="loadAvailability">重试</button></p>
    <div v-else-if="availability" class="item-timetable" aria-label="物品单日时段库存">
      <div v-for="band in bands" :key="band" class="item-time-band">
        <span class="time-band-label">{{ hourText(band) }}<small>— {{ hourText(band + 2) }}</small></span>
        <button v-for="hour in [band, band + 1]" :key="hour" type="button" class="item-time-slot"
          :class="{ occupied: slot(hour)?.available < quantity, selected: selected(hour) }"
          :disabled="submitting || past(hour) || viewDate > lastStartDate || slot(hour)?.available < quantity" @click="chooseHour(hour)">
          <strong>{{ hourText(hour) }}–{{ hourText(hour + 1) }}</strong>
          <span>{{ past(hour) ? '已过时' : slot(hour)?.available === 0 ? '已约满' : `余 ${slot(hour)?.available} 件` }}</span>
          <small v-if="slot(hour)?.reserved">已占用 {{ slot(hour).reserved }} 件</small>
          <small v-if="slot(hour)?.pending">待审核 {{ slot(hour).pending }} 件</small>
        </button>
      </div>
    </div>
    <p class="booking-help">两小时一组、每格一小时。点击可用格可快速选择一小时；跨天借用请在下方设置起止日期。</p>
    <p v-if="item.unitSummary.available < 1" class="field-error">目前没有可借实物，请等待管理员恢复库存。</p>
    <div v-else-if="!currentUser"><p>登录后即可提交预约。</p><button class="primary-button" type="button" @click="emit('login-requested')">去登录</button></div>
    <form v-else @submit.prevent="submit">
      <fieldset :disabled="submitting" class="booking-fields">
        <div class="booking-date-row">
          <label class="field"><span>开始日期</span><input v-model="startDate" type="date" :min="today" :max="lastStartDate" required></label>
          <label class="field"><span>开始时间</span><select v-model.number="startHour" aria-label="开始时间" required><option v-for="hour in startHours" :key="hour" :value="hour">{{ hourText(hour) }}</option></select></label>
        </div>
        <div class="booking-date-row">
          <label class="field"><span>结束日期</span><input v-model="endDate" type="date" :min="startDate" :max="lastEndDate" required></label>
          <label class="field"><span>结束时间</span><select v-model.number="endHour" aria-label="结束时间" required><option v-for="hour in endHours" :key="hour" :value="hour">{{ hourText(hour) }}</option></select></label>
        </div>
        <label class="field"><span>数量</span><input v-model.number="quantity" type="number" min="1" :max="Math.min(100,item.unitSummary.available)" required></label>
        <label class="field"><span>借用用途</span><textarea v-model.trim="purpose" maxlength="300" rows="2" required placeholder="例如：学院活动摄影"></textarea></label>
      </fieldset>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="notice" role="status">{{ successMessage }}</p>
      <button class="primary-button" type="submit" :disabled="submitting || calendarLoading || !!calendarError || !!successMessage">{{ submitting ? '提交中…' : '提交预约' }}</button>
    </form>
  </div>
</template>

<style scoped>
.item-booking input[type=date]{width:100%;min-height:48px;border:1px solid #d4c6ec;background:#fcfaff;color:#35254f;border-radius:9px;padding:10px 12px;font:inherit;box-sizing:border-box}
.item-time-slot strong,.item-time-slot span{align-self:center}.item-time-slot span{justify-self:start}
.item-booking{border-top:1px solid #e9e2f3;padding-top:10px}.booking-help{color:#81718e;font-size:13px;line-height:1.7}.calendar-toolbar{display:flex;gap:12px;align-items:end;margin:18px 0}.calendar-toolbar .field{flex:1;margin:0}.calendar-toolbar button{padding:12px 18px}.item-timetable{max-height:330px;overflow-y:auto;border:1px solid #e6dff0;border-radius:13px}.item-time-band{display:grid;grid-template-columns:90px repeat(2,minmax(0,1fr));gap:1px;background:#e9e2f3;border-bottom:1px solid #e9e2f3}.item-time-band:last-child{border:0}.time-band-label{background:#faf8fd;padding:12px;color:#625174;display:flex;flex-direction:column;justify-content:center;gap:4px;font-size:13px}.time-band-label small{color:#9686a5}.item-time-slot{border:0;background:#fff;color:#49365e;padding:12px;display:grid;grid-template-columns:1fr auto;gap:7px;text-align:left;cursor:pointer;min-height:70px}.item-time-slot strong{font-size:13px}.item-time-slot span{font-size:12px;background:#eee8f8;color:#63418e;padding:3px 8px;border-radius:20px}.item-time-slot small{font-size:11px;color:#8d799d;grid-column:1/-1}.item-time-slot.selected{background:#f1ebfa}.item-time-slot:disabled{cursor:not-allowed;color:#978ba5;background:#f7f5f9}.item-time-slot.occupied span{background:#e7e3ec;color:#83788f}.booking-fields{border:0;padding:0;margin:22px 0;min-width:0;display:grid;gap:16px}.booking-date-row{display:grid;grid-template-columns:1fr 1fr;gap:16px}.booking-date-row .field{min-width:0;margin:0}.booking-date-row input{min-width:0;max-width:100%;box-sizing:border-box}
@media(max-width:600px){.item-time-band{grid-template-columns:62px repeat(2,minmax(0,1fr))}.time-band-label{padding:7px;font-size:11px}.item-time-slot{grid-template-columns:1fr;padding:8px;gap:4px}.item-time-slot strong{font-size:11px}.item-time-slot span{justify-self:start}.booking-date-row{gap:10px;grid-template-columns:1.25fr 1fr}.calendar-toolbar{gap:8px}.calendar-toolbar button{padding:12px}}
</style>
