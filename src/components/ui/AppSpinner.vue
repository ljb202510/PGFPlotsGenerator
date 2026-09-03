<template>
  <div class="app-spinner" :style="{ width: size, height: size }">
    <div class="app-spinner__ring" />
  </div>
</template>

<script>
export default {
  name: 'AppSpinner',
  props: {
    size: { type: String, default: '28px' }
  }
}
</script>

<style scoped>
.app-spinner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.app-spinner__ring {
  width: 100%;
  height: 100%;
  border: 3px solid var(--border);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: app-spinner-rotate .8s linear infinite;
}
@keyframes app-spinner-rotate { to { transform: rotate(360deg); } }
</style>
