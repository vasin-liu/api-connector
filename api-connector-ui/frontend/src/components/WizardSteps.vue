<script setup>
defineProps({
  step: { type: Number, required: true },
  code3rd: { type: String, default: '' },
});
</script>

<template>
  <div class="steps">
    <span :class="['step', step === 1 && 'active']">① 基础</span>
    <span :class="['step', step === 2 && 'active']">② 认证</span>
    <span :class="['step', step === 3 && 'active']">③ 接口</span>
    <router-link
      v-if="code3rd"
      :class="['step', step === 4 && 'active']"
      :to="`/trial/${code3rd}`"
    >
      ④ 试调
    </router-link>
    <span v-else :class="['step', step === 4 && 'active']">④ 试调</span>
  </div>
</template>
