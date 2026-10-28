<script setup>
import { onMounted, ref } from 'vue'
import { FALLBACK_LOCATIONS, getItemUnits, getItems, getLocations, updateItemUnit } from '../lib/api'

const UNIT_STATUSES = [
  { value: 'AVAILABLE', label: '可借' },
  { value: 'MAINTENANCE', label: '维修中' },
  { value: 'DISABLED', label: '停用' },
  { value: 'LOST', label: '遗失' },
]

const items = ref([])
const loading = ref(false)
const error = ref('')
const locations = ref([])

const expandedId = ref(null)
const unitsByItem = ref({})
const unitsLoading = ref(false)
const unitError = ref('')
const unitSavingId = ref(null)

function locationName(locationId) {
  const match = locations.value.find((item) => item.id === Number(locationId))
  return match?.name ?? `地点 ${locationId}`
}

function unitStatusLabel(status) {
  return UNIT_STATUSES.find((entry) => entry.value === status)?.label ?? status
}

async function loadItems() {
  loading.value = true
  error.value = ''
  try {
    items.value = await getItems()
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
  unitError.value = ''
  unitsLoading.value = true
  try {
    unitsByItem.value = { ...unitsByItem.value, [item.id]: await getItemUnits(item.id) }
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
    // 更新库存摘要
    const units = unitsByItem.value[item.id] ?? []
    item.unitSummary = {
      total: units.length,
      available: units.filter((entry) => entry.status === 'AVAILABLE').length,
    }
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
    locations.value = FALLBACK_LOCATIONS
  }
  loadItems()
})
</script>

<template>
  <section class="item-stock">
    <div class="section-heading">
      <div>
        <p class="eyebrow">管理后台 · 库存管理</p>
        <h2>库存与实物编号</h2>
      </div>
      <button type="button" class="refresh-button" :disabled="loading" @click="loadItems">
        {{ loading ? '读取中…' : '重新读取' }}
      </button>
    </div>

    <p v-if="loading" class="state-card" role="status">正在读取物品库存…</p>
    <div v-else-if="error" class="state-card error-card" role="alert">
      <strong>暂时无法显示库存</strong>
      <p>{{ error }}</p>
      <button type="button" class="retry-button" @click="loadItems">重试</button>
    </div>
    <p v-else-if="items.length === 0" class="state-card">还没有物品，请先在「物品上架」录入。</p>

    <div v-else class="stock-table-wrap">
      <table class="stock-table">
        <thead>
          <tr>
            <th>物品</th>
            <th>归属地点</th>
            <th>类型</th>
            <th>状态</th>
            <th>库存（可借 / 总数）</th>
            <th>实物编号</th>
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
                  {{ expandedId === item.id ? '收起' : '查看编号' }}
                </button>
              </td>
            </tr>
            <tr v-if="expandedId === item.id" class="unit-row">
              <td colspan="6">
                <p v-if="unitsLoading" class="card-hint">正在读取实物编号…</p>
                <template v-else>
                  <p v-if="unitError" class="field-error">{{ unitError }}</p>
                  <ul v-if="unitsByItem[item.id]?.length" class="unit-list">
                    <li v-for="unit in unitsByItem[item.id]" :key="unit.id">
                      <span class="unit-code">{{ unit.unitCode }}</span>
                      <select
                        class="unit-status-select"
                        :value="unit.status"
                        :disabled="unitSavingId === unit.id"
                        :aria-label="`修改 ${unit.unitCode} 状态`"
                        @change="changeUnitStatus(item, unit, $event)"
                      >
                        <option v-for="option in UNIT_STATUSES" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </li>
                  </ul>
                  <p v-else class="card-hint">该物品还没有实物编号，可在「物品上架」录入。</p>
                </template>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </section>
</template>
