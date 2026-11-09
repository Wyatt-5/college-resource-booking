<template>
  <section class="content-section">
    <div class="section-heading">
      <div><h1>账号与权限</h1></div>
      <div class="account-actions">
        <button class="refresh-button" type="button" @click="settingsView = 'profile'">我的资料</button>
        <button class="refresh-button" type="button" @click="settingsView = 'password'">修改密码</button>
        <button v-if="isSystemAdmin" class="primary-button" type="button" @click="openCreate">新增账号</button>
      </div>
    </div>
    <p class="section-sub">查看账号、分配权限，或管理自己的资料和密码。</p>
    <p v-if="successMessage" class="notice" role="status">{{ successMessage }}</p>
    <BookingModal v-if="showCreate" title="新增账号" :busy="submitting" @close="showCreate = false">
    <form class="account-provision-form" @submit.prevent="submit">
      <p class="card-hint">若账号曾停用，填写原姓名可重新启用；历史记录保留，权限需重新分配。</p>
      <label class="field"><span>登录账号</span>
        <input v-model.trim="loginName" type="text" minlength="3" maxlength="40" pattern="[A-Za-z0-9._-]+" required>
      </label>
      <label class="field"><span>姓名或显示名称</span>
        <input v-model.trim="displayName" type="text" maxlength="100" required>
      </label>
      <label class="field"><span>所属部门（选填）</span>
        <select v-model="department"><option value="">暂不设置</option><option v-for="entry in departments" :key="entry.id" :value="entry.name">{{ entry.name }}</option><option :value="null">＋ 新增部门…</option></select>
      </label>
      <div v-if="department === null" class="add-department">
        <label class="field"><span>新部门名称</span><input v-model.trim="newDepartment" type="text" maxlength="100" placeholder="输入部门名称" @keydown.enter.prevent="addDepartment"></label>
        <button class="refresh-button" type="button" :disabled="addingDepartment || !newDepartment" @click="addDepartment">{{ addingDepartment ? '添加中…' : '添加' }}</button>
        <p class="department-hint">添加后，其他用户也可以选择此部门。</p>
      </div>
      <label class="field"><span>初始密码（至少 12 位）</span>
        <input v-model="password" type="password" minlength="12" autocomplete="new-password" required>
      </label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <button class="primary-button" type="submit" :disabled="submitting || addingDepartment || department === null">
        {{ submitting ? '创建中…' : '创建账号' }}
      </button>
    </form>
    </BookingModal>
    <BookingModal v-if="settingsView" compact :title="settingsView === 'password' ? '修改密码' : '我的资料'" @close="settingsView = ''">
      <AccountSettings :view="settingsView" @profile-changed="emit('profile-changed', $event)" @password-changed="emit('password-changed')" />
    </BookingModal>
    <template v-if="isSystemAdmin">
    <div class="section-heading">
      <h3>账号列表</h3>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadAccounts">刷新账号</button>
    </div>
    <p v-if="listError" class="form-error" role="alert">{{ listError }}</p>
    <div class="role-tabs" role="group" aria-label="按身份筛选账号">
      <button v-for="option in roleOptions" :key="option.value" type="button" :class="{active: roleFilter === option.value}" :aria-pressed="roleFilter === option.value" @click="roleFilter=option.value">{{ option.label }}</button>
    </div>
    <p v-if="!filteredAccounts.length && !loading" class="card-hint">该分类暂无账号。</p>
    <div class="reservation-list account-grid-list">
      <article v-for="account in filteredAccounts" :key="account.id" class="item-card reservation-entry account-entry">
        <div>
          <h3>{{ account.displayName }} · {{ account.loginName }}</h3><p class="card-hint">{{ account.department || '未设置部门' }} · {{ roleOptions.find(option => option.value === accountRole(account))?.label }}<span v-if="account.id === currentUser?.id"> · 当前账号</span></p>
          <p v-if="account.id === currentUser?.id" class="card-hint">不能修改自己的授权。</p>
          <template v-else-if="grantForms[account.id]">
            <label class="field"><span>账号身份</span>
              <select v-model="grantForms[account.id].role">
                <option value="USER">普通用户</option>
                <option value="REVIEWER">审核员</option>
                <option value="SYSTEM_ADMIN">系统管理员</option>
              </select>
            </label>
          </template>
        </div>
        <div v-if="account.id !== currentUser?.id" class="account-actions">
        <button class="refresh-button" type="button" :disabled="savingId === account.id" @click="saveGrants(account)">
          {{ savingId === account.id ? '保存中…' : '保存权限' }}
        </button>
        <button class="link-button" type="button" :disabled="savingId !== null" @click="removeAccount(account)">移除账号</button>
        </div>
      </article>
    </div>
    </template>
    <p v-else class="card-hint">账号创建和权限分配由系统管理员负责。</p>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import BookingModal from '../../components/BookingModal.vue'
import AccountSettings from '../../components/AccountSettings.vue'
import { createUserAccount, getUserAccounts, setUserGrants, disableUserAccount, getDepartments, createDepartment } from '../../lib/api'

const props = defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['profile-changed', 'password-changed'])
const isSystemAdmin = computed(() => props.currentUser?.roles?.includes('SYSTEM_ADMIN'))
const showCreate = ref(false)
const settingsView = ref('')
function openCreate() { errorMessage.value = ''; showCreate.value = true; loadDepartments() }

const loginName = ref('')
const displayName = ref('')
const password = ref('')
const department = ref(''), departments = ref([]), newDepartment = ref(''), addingDepartment = ref(false)
async function loadDepartments() { try { departments.value = await getDepartments() } catch (error) { errorMessage.value = error.message || '部门列表读取失败' } }
async function addDepartment() {
  if (addingDepartment.value || !newDepartment.value) return
  addingDepartment.value = true; errorMessage.value = ''
  try { const result = await createDepartment(newDepartment.value); await loadDepartments(); department.value = result.name; newDepartment.value = '' }
  catch (error) { errorMessage.value = error.message || '添加部门失败' }
  finally { addingDepartment.value = false }
}
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const accounts = ref([])
const roleFilter = ref('ALL')
const roleOptions = [{value:'ALL',label:'全部'}, {value:'SYSTEM_ADMIN',label:'系统管理员'}, {value:'REVIEWER',label:'审核员'}, {value:'USER',label:'普通用户'}]
const accountRole = user => user.grants.some(grant => grant.role === 'SYSTEM_ADMIN') ? 'SYSTEM_ADMIN' : (user.reviewer || user.grants.some(grant => ['COUNSELOR_ADMIN','RESOURCE_MANAGER'].includes(grant.role)) ? 'REVIEWER' : 'USER')
const filteredAccounts = computed(() => accounts.value.filter(user => user.status === 'ACTIVE' && (roleFilter.value === 'ALL' || accountRole(user) === roleFilter.value)))
const grantForms = ref({})
const listError = ref('')
const loading = ref(false)
const savingId = ref(null)
async function loadAccounts() {
  loading.value = true
  listError.value = ''
  try {
    const users = await getUserAccounts()
    accounts.value = users
    grantForms.value = Object.fromEntries(users.map(user => [user.id, {
      role: user.grants.some(grant => grant.role === 'SYSTEM_ADMIN') ? 'SYSTEM_ADMIN'
        : (user.reviewer || user.grants.some(grant => ['COUNSELOR_ADMIN','RESOURCE_MANAGER'].includes(grant.role)) ? 'REVIEWER' : 'USER'),
    }]))
  } catch (error) { listError.value = error.message || '账号读取失败。' }
  finally { loading.value = false }
}
async function saveGrants(account) {
  const form = grantForms.value[account.id]
  if (!form || savingId.value != null) return
  const grants = form.role === 'SYSTEM_ADMIN' ? [{ role: 'SYSTEM_ADMIN', locationId: null }] : []
  savingId.value = account.id
  listError.value = ''
  try {
    await setUserGrants(account.id, grants, form.role === 'REVIEWER')
    successMessage.value = `${account.displayName} 的权限已保存。`
    await loadAccounts()
  } catch (error) { listError.value = error.message || '保存权限失败。' }
  finally { savingId.value = null }
}
async function removeAccount(account) {
  if (savingId.value !== null || !window.confirm(`确定移除账号“${account.displayName}”？账号将停用，历史预约记录会保留。`)) return
  savingId.value=account.id; listError.value=''; successMessage.value=''
  try { await disableUserAccount(account.id); successMessage.value=`${account.displayName}的账号已停用。`; await loadAccounts() }
  catch (error) { listError.value=error.message || '移除账号失败。' }
  finally { savingId.value=null }
}
async function submit() {
  if (submitting.value || addingDepartment.value || department.value === null) return
  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const created = await createUserAccount({
      loginName: loginName.value,
      displayName: displayName.value,
      password: password.value,
      department: department.value,
    })
    successMessage.value = `账号 ${created.loginName} 已保存，可以在用户端登录。`
    loginName.value = ''
    displayName.value = ''
    password.value = ''
    department.value = ''
    showCreate.value = false
    await loadAccounts()
  } catch (error) {
    errorMessage.value = error.message || '创建账号失败。'
  } finally {
    submitting.value = false
  }
}
onMounted(() => { if (isSystemAdmin.value) loadAccounts() })
</script>

<style scoped>
.account-grid-list{grid-template-columns:repeat(auto-fill,minmax(min(100%,340px),1fr));align-items:stretch}.account-entry{display:flex;flex-direction:column;align-items:stretch;gap:12px;padding:18px}.account-entry h3{overflow-wrap:anywhere;font-size:16px}.account-entry .field{margin:12px 0 0}.account-entry .account-actions{margin-top:auto;justify-content:flex-start}.account-entry .account-actions button{flex:0 1 auto}.add-department{display:flex;align-items:end;gap:10px;flex-wrap:wrap}.department-hint{width:100%;margin:0;color:#817696;font-size:12px}.add-department .field{flex:1;min-width:0;margin:0}.add-department button{flex:0 0 auto}.role-tabs{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0 18px}.role-tabs button{padding:8px 14px;border:1px solid #ded5ed;border-radius:9px;background:#fff;color:#584575}.role-tabs button.active{background:#43316d;color:white}.account-actions{display:flex;gap:10px;flex-wrap:wrap;justify-content:flex-end}.account-provision-form{display:grid;gap:14px}.account-provision-form .primary-button{justify-self:start}
@media(max-width:600px){.account-actions{justify-content:flex-start}.account-actions button{flex:1 1 auto}}
</style>
