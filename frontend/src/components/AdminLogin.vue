<template>
  <section class="content-section login-section">
    <div class="login-card">
      <p class="eyebrow">管理端</p>
      <h1>管理员登录</h1>
      <p class="section-sub">物品上架、库存与归还核验仅供管理员使用，请使用学院分配的账号登录。</p>

      <form class="login-form" @submit.prevent="submit">
        <label class="field">
          <span>账号</span>
          <input v-model.trim="account" type="text" autocomplete="username" required>
        </label>
        <label class="field">
          <span>密码</span>
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
import { ref } from 'vue'
import { loginAdmin } from '../lib/api'

const emit = defineEmits(['logged-in'])

const account = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  submitting.value = true
  errorMessage.value = ''
  try {
    const { user } = await loginAdmin(account.value, password.value)
    password.value = ''
    emit('logged-in', user)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}
</script>
