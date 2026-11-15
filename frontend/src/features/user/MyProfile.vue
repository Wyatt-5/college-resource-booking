<script setup>
import AccountSettings from '../../components/AccountSettings.vue'

defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested', 'profile-changed', 'password-changed'])
</script>

<template>
  <section class="content-section profile-page">
    <header class="section-heading"><h1>我的资料</h1></header>
    <div v-if="!currentUser" class="state-card">
      <p>登录后查看和编辑个人资料</p>
      <button class="primary-button" type="button" @click="emit('login-requested')">去登录</button>
    </div>
    <div v-else class="profile-page-form">
      <AccountSettings :key="currentUser.id" view="profile"
        @profile-changed="emit('profile-changed', $event)" @password-changed="emit('password-changed')" />
    </div>
  </section>
</template>

<style scoped>
.profile-page-form{width:100%;max-width:520px;margin-top:8px}
</style>
