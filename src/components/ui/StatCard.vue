<template>
  <div class="stat-card">
    <div v-if="icon" class="stat-card__icon">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-card__body">
      <div class="stat-card__value">{{ value }}</div>
      <div class="stat-card__label">{{ label }}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'StatCard',
  props: {
    icon: { type: [String, Object, Function], default: null },
    value: { type: [String, Number], default: '' },
    label: { type: String, default: '' }
  }
}
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-base);
  transition: box-shadow var(--transition-base), transform var(--transition-base);
}
.stat-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
.stat-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--brand-soft);
  color: var(--brand);
}
.stat-card__value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-strong);
}
.stat-card__label {
  margin-top: 2px;
  font-size: var(--text-base);
  color: var(--text-muted);
}
</style>
