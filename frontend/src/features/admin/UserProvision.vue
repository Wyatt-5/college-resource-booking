<template>
  <section class="content-section">
    <div class="section-heading">
      <div><p class="eyebrow">系统管理员</p><h2>开通普通用户账号</h2></div>
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
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { createUserAccount } from '../../lib/api'

const loginName = ref('')
const displayName = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
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
  } catch (error) {
    errorMessage.value = error.message || '创建账号失败。'
  } finally {
    submitting.value = false
  }
}
</script>
