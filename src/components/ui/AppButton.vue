<template>
  <button
    class="app-btn"
    :class="[`app-btn--${variant}`, { 'app-btn--block': block }]"
    :disabled="loading || disabled"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="app-btn__spinner" />
    <slot />
  </button>
</template>

<script>
export default {
  name: 'AppButton',
  props: {
    variant: { type: String, default: 'primary' }, // primary | secondary | danger | ghost
    block: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false }
  },
  emits: ['click']
}
</script>

<style scoped>
.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  line-height: 1.2;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  cursor: pointer;
  color: var(--on-brand);
  background: var(--brand);
  transition: background var(--transition-fast), box-shadow var(--transition-fast), opacity var(--transition-fast);
}
.app-btn:hover { background: var(--brand-hover); }
.app-btn:active { background: var(--brand-active); }
.app-btn--secondary {
  background: var(--bg-soft);
  color: var(--text-regular);
  border-color: var(--border);
}
.app-btn--secondary:hover { background: var(--bg-hover); }
.app-btn--danger { background: var(--danger); }
.app-btn--danger:hover { opacity: .9; }
.app-btn--ghost {
  background: transparent;
  color: var(--brand);
  border-color: var(--brand);
}
.app-btn--ghost:hover { background: var(--brand-soft); }
.app-btn--block { width: 100%; }
.app-btn:disabled { opacity: .55; cursor: not-allowed; }
.app-btn__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, .5);
  border-top-color: #fff;
  border-radius: 50%;
  animation: app-btn-spin .8s linear infinite;
}
@keyframes app-btn-spin { to { transform: rotate(360deg); } }
</style>
