<script setup>
import { ref } from 'vue'
import MyReservations from './MyReservations.vue'
import Feedback from '../communication/Feedback.vue'
import BookingModal from '../../components/BookingModal.vue'
import AccountSettings from '../../components/AccountSettings.vue'
defineProps({ currentUser: { type: Object, default: null } })
const emit = defineEmits(['login-requested'])
const tab = ref('reservations')
const showProfile = ref(false)
</script>
<template>
  <div class="my-page">
    <header class="my-page-header"><div><h1>我的</h1><p>查看预约和反馈进度。</p></div><button v-if="currentUser" type="button" class="refresh-button" @click="showProfile=true">我的资料</button></header>
    <BookingModal v-if="showProfile" title="我的资料" compact @close="showProfile=false"><AccountSettings view="profile" /></BookingModal>
    <div class="my-tabs" role="group" aria-label="个人中心栏目">
      <button type="button" :class="{active:tab==='reservations'}" :aria-pressed="tab==='reservations'" @click="tab='reservations'">我的预约</button>
      <button type="button" :class="{active:tab==='feedback'}" :aria-pressed="tab==='feedback'" @click="tab='feedback'">我的反馈</button>
    </div>
    <MyReservations v-if="tab==='reservations'" :current-user="currentUser" @login-requested="emit('login-requested')" />
    <Feedback v-else :current-user="currentUser" @login-requested="emit('login-requested')" />
  </div>
</template>
<style scoped>
.my-page-header{margin-bottom:24px;display:flex;align-items:center;justify-content:space-between;gap:12px}.my-page-header h1{margin:8px 0;color:#281c47}.my-page-header p:last-child{color:#776d94}.my-tabs{display:flex;gap:8px;margin:0 0 24px}.my-tabs button{border:1px solid #ded5ed;background:#fff;color:#584575;border-radius:10px;padding:10px 16px}.my-tabs button.active{background:#43316d;color:white;border-color:#43316d}
</style>
