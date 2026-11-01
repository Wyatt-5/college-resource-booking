<script setup>
import { onMounted, ref } from 'vue'
import { getItemUnits, getItems, getLocations, updateItemUnit, updateItem, getItemImages, uploadItemImage, deleteItemImage } from '../lib/api'
import ItemCreateForm from './ItemCreateForm.vue'
import BookingModal from './BookingModal.vue'
import BookingLog from '../features/admin/BookingLog.vue'

const UNIT_STATUSES = [
  { value: 'AVAILABLE', label: '可借' },
  { value: 'MAINTENANCE', label: '维修中' },
  { value: 'DISABLED', label: '停用' },
  { value: 'LOST', label: '遗失' },
]

const items = ref([])
const page = ref(0)
const total = ref(0)
const size = 20
const loading = ref(false)
const error = ref('')
const locations = ref([])

const expandedId = ref(null)
const unitsByItem = ref({})
const unitsLoading = ref(false)
const unitError = ref('')
const unitSavingId = ref(null)
const stockTargets = ref({})
const creating = ref(false)
const createBusy = ref(false)
function closeCreate() {
  if (createBusy.value) return
  creating.value = false
  loadItems()
}
function createdItem() {
  creating.value = false
  createBusy.value = false
  loadItems()
}
const edits=ref({}), images=ref({}), editSaving=ref(false), imageFile=ref(null), editMessage=ref('')
async function saveDetails(item) {
  editSaving.value=true;unitError.value='';editMessage.value=''
  try {
    const d=edits.value[item.id]
    const quantity=stockTargets.value[item.id]
    if (!Number.isInteger(quantity) || quantity<0 || quantity>500) throw new Error('库存总数请输入 0 至 500 的整数。')
    const saved=await updateItem(item.id,{name:d.name,description:d.description,locationId:Number(d.locationId),isValuable:d.isValuable,status:d.status,rules:{advanceDays:Number(d.advanceDays),maxBorrowDays:Number(d.maxBorrowDays)},stockQuantity:quantity})
    Object.assign(item,saved)
    editMessage.value='物品信息与库存已保存。'
    try {
      unitsByItem.value[item.id]=await getItemUnits(item.id)
    } catch {
      editMessage.value='物品信息与库存已保存；实物编号暂时无法刷新，请重新读取。'
    }
  }catch(cause){unitError.value=cause.message||'保存失败，物品设置未更改。'}
  finally{editSaving.value=false}
}
async function addPhoto(item){if(!imageFile.value)return;editSaving.value=true;unitError.value='';try{await uploadItemImage(item.id,imageFile.value);images.value[item.id]=await getItemImages(item.id);imageFile.value=null;editMessage.value='照片已上传。'}catch(e){unitError.value=e.message}finally{editSaving.value=false}}
async function removePhoto(item,image){editSaving.value=true;unitError.value='';try{await deleteItemImage(item.id,image.id);images.value[item.id]=await getItemImages(item.id)}catch(e){unitError.value=e.message}finally{editSaving.value=false}}
function locationName(locationId) {
  const match = locations.value.find((item) => item.id === Number(locationId))
  return match?.name ?? `地点 ${locationId}`
}

function unitStatusLabel(status) {
  return UNIT_STATUSES.find((entry) => entry.value === status)?.label ?? status
}

async function loadItems(keepExpanded = false) {
  loading.value = true
  error.value = ''
  try {
    const data = await getItems({ page: page.value, size })
    items.value = data.content
    total.value = data.totalElements
    if (!keepExpanded) expandedId.value = null
  } catch (cause) {
    items.value = []
    error.value = cause instanceof Error ? cause.message : '读取物品失败。'
  } finally {
    loading.value = false
  }
}

async function toggleUnits(item) {
  if (expandedId.value === item.id) {
    expandedId.value = null
    return
  }
  expandedId.value = item.id
  stockTargets.value[item.id] = item.unitSummary.total
  edits.value[item.id]={name:item.name,description:item.description,locationId:item.locationId,isValuable:item.isValuable,status:item.status,advanceDays:item.rules.advanceDays,maxBorrowDays:item.rules.maxBorrowDays}
  editMessage.value=''
  unitError.value = ''
  unitsLoading.value = true
  try {
    const [units,photos]=await Promise.all([getItemUnits(item.id),getItemImages(item.id)])
    unitsByItem.value = { ...unitsByItem.value, [item.id]: units }
    images.value[item.id]=photos
  } catch (cause) {
    unitError.value = cause instanceof Error ? cause.message : '读取实物编号失败。'
  } finally {
    unitsLoading.value = false
  }
}

async function changeUnitStatus(item, unit, event) {
  const status = event.target.value
  if (status === unit.status) return
  unitSavingId.value = unit.id
  unitError.value = ''
  try {
    const saved = await updateItemUnit(item.id, unit.id, { status })
    unit.status = saved.status
    // Re-read the server summary instead of inventing a second stock calculation.
    await loadItems(true)
  } catch (cause) {
    unitError.value = cause instanceof Error ? cause.message : '更新状态失败。'
    event.target.value = unit.status
  } finally {
    unitSavingId.value = null
  }
}

onMounted(async () => {
  try {
    locations.value = await getLocations()
  } catch {
    locations.value = []
  }
  loadItems()
})
</script>

<template>
  <section class="item-stock">
    <BookingModal v-if="creating" title="新增物品" :busy="createBusy" @close="closeCreate">
      <ItemCreateForm @created="createdItem" @busy-change="createBusy = $event" />
    </BookingModal>
    <div class="section-heading">
      <div>
        <p class="eyebrow">管理后台 · 库存管理</p>
        <h2>库存与实物编号</h2>
      </div>
      <div class="unit-input-row">
        <button type="button" class="refresh-button" :disabled="loading" @click="loadItems()">{{ loading ? '读取中…' : '重新读取' }}</button>
        <button type="button" class="primary-button" @click="creating = true">新增物品</button>
      </div>
    </div>

    <p v-if="loading" class="state-card" role="status">正在读取物品库存…</p>
    <div v-else-if="error" class="state-card error-card" role="alert">
      <strong>暂时无法显示库存</strong>
      <p>{{ error }}</p>
      <button type="button" class="retry-button" @click="loadItems">重试</button>
    </div>
    <p v-else-if="items.length === 0" class="state-card">还没有物品，点击右上角「新增物品」。</p>

    <div v-else class="stock-table-wrap">
      <table class="stock-table">
        <thead>
          <tr>
            <th>物品</th>
            <th>归属地点</th>
            <th>类型</th>
            <th>状态</th>
            <th>库存（可借 / 总数）</th>
            <th>管理物品</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="item in items" :key="item.id">
            <tr>
              <td>
                <strong>{{ item.name }}</strong>
                <small v-if="item.description" class="stock-desc">{{ item.description }}</small>
              </td>
              <td>{{ locationName(item.locationId) }}</td>
              <td>
                <span class="status-badge" :class="item.isValuable ? 'badge-valuable' : 'badge-normal'">
                  {{ item.isValuable ? '贵重' : '普通' }}
                </span>
              </td>
              <td>
                <span class="status-badge" :class="item.status === 'ACTIVE' ? 'badge-ok' : 'badge-off'">
                  {{ item.status === 'ACTIVE' ? '启用' : '停用' }}
                </span>
              </td>
              <td>{{ item.unitSummary.available }} / {{ item.unitSummary.total }}</td>
              <td>
                <button type="button" class="link-button" @click="toggleUnits(item)">
                  {{ expandedId === item.id ? '收起' : '编辑物品' }}
                </button>
              </td>
            </tr>
            <tr v-if="expandedId === item.id" class="unit-row">
              <td colspan="6">
                <p v-if="unitsLoading" class="card-hint">正在读取实物编号…</p>
                <template v-else>
                  <div class="item-edit-grid">
                    <label class="field"><span>物品名称</span><input v-model.trim="edits[item.id].name" maxlength="50" required></label>
                    <label class="field"><span>存放地点</span><select v-model.number="edits[item.id].locationId"><option v-for="place in locations.filter(entry=>entry.status==='ACTIVE')" :key="place.id" :value="place.id">{{ place.name }}</option></select></label>
                    <label class="field"><span>物品介绍</span><textarea v-model.trim="edits[item.id].description" maxlength="300" rows="2"></textarea></label>
                    <label class="field"><span>物品类型</span><select v-model="edits[item.id].isValuable"><option :value="false">普通物品</option><option :value="true">贵重物品（需审核）</option></select></label>
                    <label class="field"><span>状态</span><select v-model="edits[item.id].status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
                    <label class="field"><span>最多提前预约天数</span><input v-model.number="edits[item.id].advanceDays" type="number" min="0" required></label>
                    <label class="field"><span>最长借用天数</span><input v-model.number="edits[item.id].maxBorrowDays" type="number" min="1" required></label>
                    <label class="field"><span>库存总数</span><input v-model.number="stockTargets[item.id]" type="number" min="0" max="500" required></label>
                  </div>
                  <button class="primary-button" type="button" :disabled="editSaving" @click="saveDetails(item)">{{ editSaving ? '保存中…' : '保存物品设置' }}</button>
                  <p class="card-hint">实物编号自动生成；已借出或待核验的实物不能直接移除。</p>
                  <p v-if="editMessage" class="notice" role="status">{{ editMessage }}</p>
                  <div class="item-edit-photos"><h3>展示照片</h3><div v-for="image in images[item.id]" :key="image.id" class="item-edit-photo"><img :src="image.url" :alt="image.fileName"><button class="link-button" type="button" @click="removePhoto(item,image)">删除</button></div><input type="file" accept="image/jpeg,image/png,image/webp" @change="imageFile=$event.target.files?.[0]||null"><button class="refresh-button" type="button" :disabled="!imageFile||editSaving" @click="addPhoto(item)">上传照片</button></div>
                  <h3>实物编号与状态</h3>
                  <div class="unit-input-row">
                    <span class="card-hint">需要增加或减少实物时，在上方修改“库存总数”并保存。</span>
                  </div>
                  <p v-if="unitError" class="field-error">{{ unitError }}</p>
                  <ul v-if="unitsByItem[item.id]?.length" class="unit-list">
                    <li v-for="unit in unitsByItem[item.id]" :key="unit.id">
                      <span class="unit-code">{{ unit.unitCode }}</span>
                      <select
                        class="unit-status-select"
                        :value="unit.status"
                        :disabled="unitSavingId === unit.id || ['CHECKED_OUT', 'RETURN_SUBMITTED'].includes(unit.status)"
                        :aria-label="`修改 ${unit.unitCode} 状态`"
                        @change="changeUnitStatus(item, unit, $event)"
                      >
                        <option v-if="['CHECKED_OUT', 'RETURN_SUBMITTED'].includes(unit.status)" :value="unit.status">{{ unit.status === 'CHECKED_OUT' ? '已借出' : '待归还核验' }}</option>
                        <option v-for="option in UNIT_STATUSES" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </li>
                  </ul>
                  <p v-else class="card-hint">当前库存为0，可在上方调整库存数量。</p>
                  <BookingLog :item-id="item.id" />
                </template>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <nav class="section-heading" aria-label="库存分页">
      <button class="refresh-button" :disabled="loading || page === 0" @click="page--; loadItems()">上一页</button>
      <span>共 {{ total }} 项 · 第 {{ page + 1 }} 页</span>
      <button class="refresh-button" :disabled="loading || (page + 1) * size >= total" @click="page++; loadItems()">下一页</button>
    </nav>
  </section>
</template>
<style scoped>
.item-edit-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-bottom:18px}.item-edit-grid .field{min-width:0}.item-edit-photos{margin:22px 0;border-top:1px solid #e7e0f1;padding-top:15px;display:flex;align-items:center;flex-wrap:wrap;gap:12px}.item-edit-photos h3{width:100%;margin:0}.item-edit-photo{display:flex;align-items:center;gap:6px}.item-edit-photo img{width:72px;height:62px;object-fit:cover;border-radius:8px}
@media(max-width:700px){.item-edit-grid{grid-template-columns:1fr}}
</style>
