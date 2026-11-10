<template>
  <section class="content-section">
    <div v-if="view === 'all'" class="section-heading">
      <div>

        <h1>账号管理</h1>
        <p class="section-sub">查看和修改当前管理员的资料、密码，以及所有管理员名单。</p>
      </div>
    </div>

    <div class="account-grid">
      <div v-if="view !== 'password'" class="account-card">
        <div class="profile-header"><UserAvatar :user-id="profile.id" :name="profile.name" /><div><strong>{{ profile.name || '—' }}</strong><small>{{ profile.account || '—' }} · {{ roleLabels(profile.roles) }}</small></div></div>
        <form @submit.prevent="saveProfile">
          <label class="field"><span>所属部门</span><select v-model="profileForm.department"><option value="">暂不设置</option><option v-if="profileForm.department && !departments.some(entry => entry.name === profileForm.department)" :value="profileForm.department">{{ profileForm.department }}（原有部门）</option><option v-for="entry in departments" :key="entry.id" :value="entry.name">{{ entry.name }}</option></select></label>
          <label class="field"><span>邮箱</span><input v-model.trim="profileForm.email" type="email" maxlength="254"></label>
          <label class="field"><span>手机号</span><input v-model.trim="profileForm.phone" type="tel" maxlength="30"></label>
          <label class="field"><span>更换头像（JPG/PNG，最大 2 MB）</span><input type="file" accept="image/jpeg,image/png" @change="avatarFile = $event.target.files?.[0] || null"></label>
          <p class="profile-note">姓名和账号由管理员维护，用于实名记录。头像在预约与审核中对登录用户可见。</p>
          <Transition name="toast"><p v-if="profileMessage" class="form-ok" role="status">{{ profileMessage }}</p></Transition>
          <Transition name="toast"><p v-if="profileError" class="form-error" role="alert">{{ profileError }}</p></Transition>
          <button class="primary-button" type="submit" :disabled="savingProfile || loadingDetails || !detailsLoaded">{{ savingProfile ? '保存中…' : '保存资料' }}</button>
        </form>
      </div>

      <div v-if="view !== 'profile'" class="account-card">
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
          <p class="field-hint">新密码至少 12 位，且需同时包含字母和数字。修改成功后需要重新登录。</p>

          <Transition name="toast">
            <p v-if="passwordMessage" class="form-ok" role="status">{{ passwordMessage }}</p>
          </Transition>
          <Transition name="toast">
            <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>
          </Transition>
          <button class="primary-button" type="submit" :disabled="savingPassword">
            {{ savingPassword ? '提交中…' : '确认修改' }}
          </button>
        </form>
      </div>


    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { changeMyPassword, getSessionAdmin, getMyDetails, updateMyDetails, uploadMyAvatar, getDepartments } from '../lib/api'
import UserAvatar from './UserAvatar.vue'

const props = defineProps({ view: { type: String, default: 'all' }, allowAdminList: { type: Boolean, default: false } })
const emit = defineEmits(['profile-changed', 'password-changed'])

const ROLE_LABELS = {
  USER: '普通用户',
  REVIEWER: '审核员',
  SYSTEM_ADMIN: '系统管理员',
}

function roleLabels(roles) {
  const list = Array.isArray(roles) && roles.length ? roles : ['USER']
  return list.map((role) => ROLE_LABELS[role] || role).join('、')
}

const profile = ref(getSessionAdmin() || {})
const profileForm = reactive({
  department: '',
  email: profile.value.email || '',
  phone: profile.value.phone || '',
})
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const avatarFile = ref(null)
const departments = ref([])
const loadingDetails = ref(false)
const detailsLoaded = ref(false)

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
  return value.length >= 12 && /[a-zA-Z]/.test(value) && /\d/.test(value)
}

async function saveProfile() {
  savingProfile.value = true
  profileMessage.value = ''
  profileError.value = ''
  try {
    await updateMyDetails(profileForm)
    profileMessage.value = '资料已保存。'
    if (avatarFile.value) {
      try { await uploadMyAvatar(avatarFile.value); avatarFile.value = null }
      catch (error) { profileError.value = `资料已保存，但头像上传失败：${error.message}` }
    }
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
    passwordError.value = '新密码至少 12 位，且需同时包含字母和数字。'
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
    passwordMessage.value = '密码已更新，请使用新密码重新登录。'
    emit('password-changed')
  } catch (error) {
    passwordError.value = error.message
  } finally {
    savingPassword.value = false
  }
}

onMounted(async () => {
  if (props.view === 'password') return
  loadingDetails.value = true
  try { const [details, options] = await Promise.all([getMyDetails(), getDepartments()]); departments.value = options; Object.assign(profileForm, { department: details.department, email: details.email, phone: details.phone }); detailsLoaded.value = true }
  catch (error) { profileError.value = error.status === 404 ? '个人资料接口尚未更新，请重启后端后再试。' : (error.message || '资料读取失败，请稍后重试。') }
  finally { loadingDetails.value = false }
})
</script>
<style scoped>
.account-card{border:0;box-shadow:none;padding:0}.profile-header{display:flex;align-items:center;gap:14px;margin-bottom:22px}.profile-header :deep(.user-avatar){width:56px;height:56px;font-size:22px}.profile-header small{display:block;margin-top:5px;color:#82769a}.profile-intro{margin:-8px 0 18px;color:#82769a;font-size:13px}.profile-details{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin:0}.profile-details div{padding:14px 16px;border:1px solid #ece7f5;border-radius:12px;background:#faf9fd;min-width:0}.profile-details dt{color:#82769a;font-size:12px;margin-bottom:6px}.profile-details dd{margin:0;color:#302348;font-size:14px;font-weight:600;overflow-wrap:anywhere}.profile-note{margin:18px 0 0;color:#82769a;font-size:13px}.toast-enter-active,.toast-leave-active{transition:opacity .25s ease,transform .25s ease}.toast-enter-from,.toast-leave-to{opacity:0;transform:translateY(-4px)}@media(max-width:480px){.profile-details{grid-template-columns:1fr}}
</style>
