<!-- src/components/AdminNavbar.vue -->
<template>
  <nav class="admin-navbar">
    <div class="navbar-container">
      <div class="navbar-left">
        <div class="logo">
          <el-icon class="logo-icon"><Setting /></el-icon>
          <span class="logo-text">PGFPlots Admin</span>
        </div>
      </div>

      <div class="navbar-right">
        <div class="admin-info">
          <span class="admin-name">{{ admin.username }}</span>
          <span class="admin-badge">管理员</span>
        </div>
        <button class="logout-btn" @click="logout">
          <span>退出</span>
          <el-icon class="logout-icon"><SwitchButton /></el-icon>
        </button>
      </div>
    </div>
  </nav>
</template>

<script>
import { Setting, SwitchButton } from '@element-plus/icons-vue'

export default {
  name: 'AdminNavbar',
  components: { Setting, SwitchButton },
  props: {
    admin: {
      type: Object,
      required: true
    }
  },
  methods: {
    logout() {
      this.$emit('logout')
    }
  }
}
</script>

<style scoped>
.admin-navbar {
  background: var(--brand-gradient);
  color: white;
  padding: 0 20px;
  box-shadow: var(--shadow-card);
  height: 60px;
  display: flex;
  align-items: center;
}

.navbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.admin-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: rgba(255, 255, 255, 0.1);
  padding: var(--space-2) 15px;
  border-radius: var(--radius-full);
}

.admin-name {
  font-weight: 500;
}

.admin-badge {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
}

.logout-btn {
  background: rgba(255, 255, 255, 0.15);
  color: var(--on-brand);
  border: 1px solid rgba(255, 255, 255, 0.3);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  transition: all var(--transition-base);
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

.logout-icon {
  font-size: 14px;
}
</style>
