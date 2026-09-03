<template>
  <div class="app-card" :class="{ 'app-card--hover': hover }">
    <div v-if="$slots.header || title" class="app-card__header">
      <slot name="header">
        <h3 class="app-card__title">{{ title }}</h3>
      </slot>
    </div>
    <div class="app-card__body" :style="{ padding }">
      <slot />
    </div>
  </div>
</template>

<script>
export default {
  name: 'AppCard',
  props: {
    title: { type: String, default: '' },
    hover: { type: Boolean, default: false },
    padding: { type: String, default: '20px' }
  }
}
</script>

<style scoped>
.app-card {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.app-card--hover {
  transition: box-shadow .2s ease, transform .2s ease;
}
.app-card--hover:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}
.app-card__header {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border);
}
.app-card__title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-strong);
}
.app-card__body {
  color: var(--text-regular);
}
</style>
