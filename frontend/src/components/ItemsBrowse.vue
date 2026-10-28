<template>
  <section class="content-section">
    <div class="section-heading">
      <div>
        <p class="eyebrow">用户端 · 物品预约</p>
        <h1>可借物品</h1>
        <p class="section-sub">浏览学院在架物品，确认库存与规则后再提交预约申请。</p>
      </div>
      <button class="refresh-button" :disabled="loading" @click="loadItems">{{ loading ? '刷新中…' : '刷新' }}</button>
    </div>

    <div class="filter-bar">
      <input v-model.trim="filters.keyword" type="search" placeholder="搜索物品名称或介绍" @input="applyFilters">
      <select v-model="filters.locationId" @change="applyFilters">
        <option value="">全部地点</option>
        <option v-for="loc in locations" :key="loc.id" :value="String(loc.id)">{{ loc.name }}</option>
      </select>
      <label class="filter-toggle">
        <input v-model="filters.onlyAvailable" type="checkbox" @change="applyFilters"> 只看有库存
      </label>
    </div>

    <p v-if="loadError" class="state-card error-card">{{ loadError }}</p>
    <p v-else-if="loading" class="state-card">正在加载物品…</p>
    <p v-else-if="visibleItems.length === 0" class="state-card">没有符合条件的物品。</p>

    <div v-else class="items-grid">
      <article v-for="item in visibleItems" :key="item.id" class="item-card">
        <div class="item-photo">
          <span aria-hidden="true">📦</span>
          <span v-if="(item.photoIds || []).length > 0" class="photo-count">{{ item.photoIds.length }} 张照片</span>
        </div>
        <div class="item-body">
          <div class="item-title-row">
            <h3>{{ item.name }}</h3>
            <span v-if="item.isValuable" class="status-badge badge-valuable">贵重</span>
          </div>
          <p class="item-location">{{ locationName(item.locationId) }}</p>
          <p class="item-rules">单次最长 {{ item.rules.maxBorrowDays }} 天 · 提前 {{ item.rules.advanceDays }} 天可约</p>
          <div class="item-footer">
            <span :class="['stock-text', item.unitSummary.available > 0 ? '' : 'stock-out']">
              可借 {{ item.unitSummary.available }} / 共 {{ item.unitSummary.total }} 件
            </span>
            <button class="link-button" @click="openDetail(item)">查看详情</button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="detail" class="modal-mask" @click.self="detail = null">
      <div class="modal-box" role="dialog" aria-modal="true" :aria-label="detail.name">
        <div class="modal-head">
          <h2>{{ detail.name }}</h2>
          <button class="link-button" @click="detail = null">关闭</button>
        </div>
        <div class="badge-row">
          <span v-if="detail.isValuable" class="status-badge badge-valuable">贵重 · 预约需审批</span>
          <span class="status-badge badge-normal">{{ locationName(detail.locationId) }}</span>
          <span :class="['status-badge', detail.unitSummary.available > 0 ? 'badge-ok' : 'badge-valuable']">
            可借 {{ detail.unitSummary.available }} / 共 {{ detail.unitSummary.total }} 件
          </span>
        </div>
        <p class="modal-desc">{{ detail.description || '暂无介绍。' }}</p>
        <dl class="rule-list">
          <div><dt>单次最长借用</dt><dd>{{ detail.rules.maxBorrowDays }} 天</dd></div>
          <div><dt>可提前预约</dt><dd>{{ detail.rules.advanceDays }} 天</dd></div>
          <div><dt>归还要求</dt><dd>归还时需拍照，待管理员核验通过后才完成归还。</dd></div>
        </dl>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { FALLBACK_LOCATIONS, getLocations, getPublicItems } from '../lib/api';

const items = ref([]);
const visibleItems = ref([]);
const locations = ref(FALLBACK_LOCATIONS);
const loading = ref(false);
const loadError = ref('');
const detail = ref(null);
const filters = reactive({ keyword: '', locationId: '', onlyAvailable: false });

function locationName(locationId) {
  return locations.value.find((loc) => loc.id === locationId)?.name || '未知地点';
}

function applyFilters() {
  const keyword = filters.keyword.toLowerCase();
  visibleItems.value = items.value.filter((item) => {
    if (filters.locationId && String(item.locationId) !== filters.locationId) return false;
    if (filters.onlyAvailable && item.unitSummary.available <= 0) return false;
    if (keyword && !`${item.name} ${item.description || ''}`.toLowerCase().includes(keyword)) return false;
    return true;
  });
}

function openDetail(item) {
  detail.value = item;
}

async function loadItems() {
  loading.value = true;
  loadError.value = '';
  try {
    const [locationData, itemData] = await Promise.all([
      getLocations().catch(() => FALLBACK_LOCATIONS),
      getPublicItems(),
    ]);
    locations.value = locationData;
    items.value = itemData;
    applyFilters();
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '读取物品失败。';
  } finally {
    loading.value = false;
  }
}

onMounted(loadItems);
</script>
