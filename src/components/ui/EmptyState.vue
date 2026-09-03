<template>
  <div class="empty-state">
    <div class="empty-state__icon">
      <slot name="icon">
        <el-icon :size="48" color="var(--text-muted)"><Box /></el-icon>
      </slot>
    </div>
    <p class="empty-state__text">{{ text }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty-state__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script>
import { Box } from '@element-plus/icons-vue'

export default {
  name: 'EmptyState',
  components: { Box },
  props: {
    text: { type: String, default: '暂无内容' },
    description: { type: String, default: '' }
  }
}
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}
.empty-state__icon { margin-bottom: 12px; }
.empty-state__text {
  margin: 0;
  font-size: var(--text-base);
  color: var(--text-regular);
}
.empty-state__desc {
  margin: var(--space-1) 0 0;
  font-size: var(--text-sm);
  color: var(--text-muted);
}
.empty-state__action { margin-top: var(--space-4); }
</style>
