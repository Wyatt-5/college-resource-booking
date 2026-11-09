<template>
  <section class="content-section login-section">
    <div class="login-card">

      <h2>登录共用资源系统</h2>
      <p class="section-sub">使用学院分配的账号。登录后可预约物品和地点；有管理权限的账号还可进入管理工作台。</p>
      <form class="login-form" @submit.prevent="submit">
        <label class="field"><span>账号</span>
          <input v-model.trim="account" type="text" autocomplete="username" required>
        </label>
        <label class="field"><span>密码</span>
          <input v-model="password" type="password" autocomplete="current-password" required>
        </label>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-button login-submit" type="submit" :disabled="submitting || !account || !password">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>
    </div>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { loginUser } from '../../lib/api'

const emit = defineEmits(['logged-in', 'busy-change'])
const account = ref('')
const password = ref('')
const submitting = ref(false)
watch(submitting, value => emit('busy-change', value), { immediate: true })
const errorMessage = ref('')

async function submit() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const { user } = await loginUser(account.value, password.value)
    password.value = ''
    emit('logged-in', user)
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>
<style scoped>
.login-section{padding:0;min-height:0}.login-card{box-shadow:none;border:0;padding:0;width:100%;max-width:none}.login-card h2{margin-top:0}
</style>
