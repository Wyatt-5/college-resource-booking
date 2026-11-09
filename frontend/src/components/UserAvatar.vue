<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
const props=defineProps({ userId:{type:Number,default:null}, name:{type:String,default:''} })
const failed=ref(false), version=ref(Date.now())
function refresh(){failed.value=false;version.value=Date.now()}
watch(()=>props.userId,refresh)
onMounted(()=>window.addEventListener('avatar-updated',refresh))
onUnmounted(()=>window.removeEventListener('avatar-updated',refresh))
</script>
<template><span class="user-avatar" aria-hidden="true"><img v-if="userId && !failed" :src="`/api/v1/users/${userId}/avatar?v=${version}`" alt="" @error="failed=true"><span v-else>{{ (name || '用').slice(0,1) }}</span></span></template>
<style scoped>.user-avatar{display:inline-grid;place-items:center;flex:0 0 auto;width:34px;height:34px;border-radius:50%;overflow:hidden;background:#e8e1f5;color:#59447f;font-size:14px;font-weight:700;vertical-align:middle}.user-avatar img{width:100%;height:100%;object-fit:cover}</style>
