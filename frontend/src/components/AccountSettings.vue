<template>
  <section class="content-section">
    <div class="section-heading">
      <div>
        <p class="eyebrow">管理端 · 账号管理</p>
        <h1>账号管理</h1>
        <p class="section-sub">查看和修改当前管理员的资料、密码，以及所有管理员名单。</p>
      </div>
    </div>

    <div class="account-grid">
      <div class="account-card">
        <h2>我的资料</h2>
        <form @submit.prevent="saveProfile">
          <div class="profile-row">
            <span class="profile-label">账号</span>
            <span class="profile-value">{{ profile.account }}</span>
          </div>
          <div class="profile-row">
            <span class="profile-label">角色</span>
            <span class="profile-value">{{ roleLabels(profile.roles) }}</span>
          </div>
          <div class="profile-row">
            <span class="profile-label">所属部门</span>
            <span class="profile-value">{{ profile.department || '—' }}</span>
          </div>
          <div class="profile-row">
            <span class="profile-label">加入时间</span>
            <span class="profile-value">{{ profile.joinedAt || '—' }}</span>
          </div>

          <label class="field">
            <span>姓名</span>
            <input v-model.trim="profileForm.name" type="text" required>
          </label>
          <label class="field">
            <span>邮箱</span>
            <input v-model.trim="profileForm.email" type="email">
          </label>
          <label class="field">
            <span>手机号</span>
            <input v-model.trim="profileForm.phone" type="tel">
          </label>

          <p v-if="profileMessage" class="form-ok">{{ profileMessage }}</p>
          <p v-if="profileError" class="form-error">{{ profileError }}</p>
          <button class="primary-button" type="submit" :disabled="savingProfile">
            {{ savingProfile ? '保存中…' : '保存修改' }}
          </button>
        </form>
      </div>

      <div class="account-card">
        <h2>修改密码</h2>
        <form @submit.prevent="savePassword">
          <label class="field">
            <span>旧密码</span>
            <input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" required>
          </label>
          <label class="field">
            <span>新密码</span>
            <input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" required>
          </label>
          <label class="field">
            <span>确认新密码</span>
            <input v-model="passwordForm.confirm" type="password" autocomplete="new-password" required>
          </label>
          <p class="field-hint">新密码至少 8 位，需同时包含字母和数字。修改成功后需要重新登录。</p>

          <p v-if="passwordMessage" class="form-ok">{{ passwordMessage }}</p>
          <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
          <button class="primary-button" type="submit" :disabled="savingPassword">
            {{ savingPassword ? '提交中…' : '确认修改' }}
          </button>
        </form>
      </div>

      <div class="account-card account-card-wide">
        <h2>管理员名单</h2>
        <p v-if="adminsLoading" class="state-card">读取中…</p>
        <table v-else class="admin-table">
          <thead>
            <tr><th>姓名</th><th>账号</th><th>角色</th><th>所属部门</th><th>最近登录</th></tr>
          </thead>
          <tbody>
            <tr v-for="admin in admins" :key="admin.id">
              <td>
                {{ admin.name }}
                <span v-if="admin.account === profile.account" class="status-badge badge-ok">当前登录</span>
              </td>
              <td class="mono">{{ admin.account }}</td>
              <td>{{ roleLabels(admin.roles) }}</td>
              <td>{{ admin.department || '—' }}</td>
              <td>{{ formatTime(admin.lastLoginAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { changeMyPassword, getAdminList, getSessionAdmin, updateMyProfile } from '../lib/api'

const emit = defineEmits(['profile-changed', 'password-changed'])

const ROLE_LABELS = {
  USER: '普通用户',
  COUNSELOR_ADMIN: '辅导员管理员',
  RESOURCE_MANAGER: '资源管理员',
  REVIEWER: '审核员',
  SYSTEM_ADMIN: '系统管理员',
}

function roleLabels(roles) {
  const list = Array.isArray(roles) && roles.length ? roles : ['USER']
  return list.map((role) => ROLE_LABELS[role] || role).join('、')
}

const profile = ref(getSessionAdmin() || {})
const profileForm = reactive({
  name: profile.value.name || '',
  email: profile.value.email || '',
  phone: profile.value.phone || '',
})
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const admins = ref([])
const adminsLoading = ref(true)

const savingProfile = ref(false)
const profileMessage = ref('')
const profileError = ref('')
const savingPassword = ref(false)
const passwordMessage = ref('')
const passwordError = ref('')

function formatTime(value) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function validPassword(value) {
  return value.length >= 8 && /[a-zA-Z]/.test(value) && /\d/.test(value)
}

async function saveProfile() {
  savingProfile.value = true
  profileMessage.value = ''
  profileError.value = ''
  try {
    profile.value = await updateMyProfile(profileForm)
    profileMessage.value = '资料已保存。'
    emit('profile-changed', profile.value)
  } catch (error) {
    profileError.value = error.message
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  passwordMessage.value = ''
  passwordError.value = ''
  if (!validPassword(passwordForm.newPassword)) {
    passwordError.value = '新密码至少 8 位，且需同时包含字母和数字。'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirm) {
    passwordError.value = '两次输入的新密码不一致。'
    return
  }
  savingPassword.value = true
  try {
    await changeMyPassword(passwordForm)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirm = ''
    emit('password-changed')
  } catch (error) {
    passwordError.value = error.message
  } finally {
    savingPassword.value = false
  }
}

onMounted(async () => {
  try {
    admins.value = await getAdminList()
  } finally {
    adminsLoading.value = false
  }
})
</script>
