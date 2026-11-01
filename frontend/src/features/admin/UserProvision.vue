<template>
  <section class="content-section">
    <div class="section-heading">
      <div><p class="eyebrow">系统管理员</p><h2>账号与权限</h2></div>
    </div>
    <p class="section-sub">用户不需要管理权限。将账号和初始密码通过可靠方式单独告知本人。</p>
    <form class="account-provision-form" @submit.prevent="submit">
      <label class="field"><span>登录账号</span>
        <input v-model.trim="loginName" type="text" minlength="3" maxlength="40" pattern="[A-Za-z0-9._-]+" required>
      </label>
      <label class="field"><span>姓名或显示名称</span>
        <input v-model.trim="displayName" type="text" maxlength="100" required>
      </label>
      <label class="field"><span>初始密码（至少 12 位）</span>
        <input v-model="password" type="password" minlength="12" autocomplete="new-password" required>
      </label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="notice" role="status">{{ successMessage }}</p>
      <button class="primary-button" type="submit" :disabled="submitting">
        {{ submitting ? '创建中…' : '创建账号' }}
      </button>
    </form>
    <div class="section-heading">
      <h3>已有账号的管理和审核权限</h3>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadAccounts">刷新账号</button>
    </div>
    <p v-if="listError" class="form-error" role="alert">{{ listError }}</p>
    <div class="reservation-list">
      <article v-for="account in accounts" :key="account.id" class="item-card reservation-entry">
        <div>
          <h3>{{ account.displayName }} · {{ account.loginName }}</h3>
          <p v-if="account.id === currentUser?.id" class="card-hint">不能修改自己的授权。</p>
          <template v-else-if="grantForms[account.id]">
            <div class="field-row">
              <label><input v-model="grantForms[account.id].systemAdmin" type="checkbox"> 系统管理员</label>
              <label><input v-model="grantForms[account.id].counselorAdmin" type="checkbox"> 辅导员管理员</label>
              <label><input v-model="grantForms[account.id].reviewer" type="checkbox"> 审核员</label>
            </div>
            <p class="card-hint">资源管理员负责的地点：</p>
            <div class="field-row">
              <label v-for="location in locations" :key="location.id">
                <input v-model="grantForms[account.id].managerLocations" type="checkbox" :value="location.id"> {{ location.name }}{{ location.status === 'ACTIVE' ? '' : '（停用）' }}
              </label>
            </div>
          </template>
        </div>
        <button v-if="account.id !== currentUser?.id" class="refresh-button" type="button" :disabled="savingId === account.id" @click="saveGrants(account)">
          {{ savingId === account.id ? '保存中…' : '保存权限' }}
        </button>
      </article>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { createUserAccount, getLocations, getUserAccounts, setUserGrants } from '../../lib/api'

defineProps({ currentUser: { type: Object, default: null } })

const loginName = ref('')
const displayName = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const accounts = ref([])
const locations = ref([])
const grantForms = ref({})
const listError = ref('')
const loading = ref(false)
const savingId = ref(null)
async function loadAccounts() {
  loading.value = true
  listError.value = ''
  try {
    const [users, places] = await Promise.all([getUserAccounts(), getLocations()])
    accounts.value = users
    locations.value = places
    grantForms.value = Object.fromEntries(users.map(user => [user.id, {
      systemAdmin: user.grants.some(grant => grant.role === 'SYSTEM_ADMIN'),
      counselorAdmin: user.grants.some(grant => grant.role === 'COUNSELOR_ADMIN'),
      managerLocations: user.grants.filter(grant => grant.role === 'RESOURCE_MANAGER').map(grant => grant.locationId),
      reviewer: user.reviewer,
    }]))
  } catch (error) { listError.value = error.message || '账号读取失败。' }
  finally { loading.value = false }
}
async function saveGrants(account) {
  const form = grantForms.value[account.id]
  if (!form || savingId.value != null) return
  const grants = [
    ...(form.systemAdmin ? [{ role: 'SYSTEM_ADMIN', locationId: null }] : []),
    ...(form.counselorAdmin ? [{ role: 'COUNSELOR_ADMIN', locationId: null }] : []),
    ...form.managerLocations.map(locationId => ({ role: 'RESOURCE_MANAGER', locationId })),
  ]
  savingId.value = account.id
  listError.value = ''
  try {
    await setUserGrants(account.id, grants, form.reviewer)
    successMessage.value = `${account.displayName} 的权限已保存。`
    await loadAccounts()
  } catch (error) { listError.value = error.message || '保存权限失败。' }
  finally { savingId.value = null }
}
async function submit() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const created = await createUserAccount({
      loginName: loginName.value,
      displayName: displayName.value,
      password: password.value,
    })
    successMessage.value = `账号 ${created.loginName} 已创建，可以在用户端登录。`
    loginName.value = ''
    displayName.value = ''
    password.value = ''
    await loadAccounts()
  } catch (error) {
    errorMessage.value = error.message || '创建账号失败。'
  } finally {
    submitting.value = false
  }
}
onMounted(loadAccounts)
</script>
