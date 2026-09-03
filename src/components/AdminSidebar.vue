<!-- src/components/AdminSidebar.vue -->
<template>
  <aside class="admin-sidebar" :class="{ 'sidebar-collapsed': isCollapsed, 'mobile-open': mobileOpen }">
    <div class="sidebar-header">
      <h3>管理菜单</h3>
      <div class="sidebar-header__actions">
        <button class="mobile-close" @click="$emit('close')" aria-label="关闭菜单">
          <el-icon><Close /></el-icon>
        </button>
        <button class="toggle-sidebar" @click="toggleSidebar" aria-label="折叠菜单">
          {{ isCollapsed ? '▶' : '◀' }}
        </button>
      </div>
    </div>
    <nav class="sidebar-nav" @click="$emit('navigate')">
      <ul>
        <li>
          <router-link to="/admin" class="nav-item" active-class="active">
            <el-icon class="icon"><House /></el-icon>
            <span class="text">首页</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/user" class="nav-item" active-class="active">
            <el-icon class="icon"><User /></el-icon>
            <span class="text">用户管理</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/feedback" class="nav-item" active-class="active">
            <el-icon class="icon"><ChatDotRound /></el-icon>
            <span class="text">反馈管理</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/log" class="nav-item" active-class="active">
            <el-icon class="icon"><Document /></el-icon>
            <span class="text">系统监控</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/notice" class="nav-item" active-class="active">
            <el-icon class="icon"><Bell /></el-icon>
            <span class="text">通知管理</span>
          </router-link>
        </li>
      </ul>
    </nav>
  </aside>
</template>

<script>
import { House, User, ChatDotRound, Document, Bell, Close } from '@element-plus/icons-vue'

export default {
  name: 'AdminSidebar',
  components: { House, User, ChatDotRound, Document, Bell, Close },
  props: {
    collapsed: {
      type: Boolean,
      default: false
    },
    mobileOpen: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      isCollapsed: this.collapsed
    }
  },
  watch: {
    collapsed(newVal) {
      this.isCollapsed = newVal;
    }
  },
  methods: {
    toggleSidebar() {
      this.isCollapsed = !this.isCollapsed;
      this.$emit('toggle', this.isCollapsed);
    }
  }
}
</script>

<style scoped>
.admin-sidebar {
  width: 250px;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-base);
  padding: var(--space-5);
  transition: width 0.3s ease;
  flex-shrink: 0;
  margin-right: var(--space-5);
}

.sidebar-collapsed {
  width: 90px;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--border);
}

.sidebar-header h3 {
  color: var(--text-strong);
  font-size: 18px;
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-collapsed .sidebar-header h3 {
  display: none;
}

.toggle-sidebar {
  background: none;
  border: none;
  cursor: pointer;
  font-size: var(--text-lg);
  color: var(--text-muted);
  padding: var(--space-1);
  border-radius: var(--radius-xs);
}

.toggle-sidebar:hover {
  background: var(--bg-soft);
}

.sidebar-nav ul {
  list-style: none;
}

.sidebar-nav li {
  margin-bottom: var(--space-2);
}

.nav-item {
  display: flex;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  text-decoration: none;
  color: var(--text-regular);
  border-radius: var(--radius-sm);
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  background: var(--brand-soft);
  color: var(--brand);
}

.nav-item.active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: var(--weight-semibold);
  box-shadow: inset 3px 0 0 var(--brand);
}

.nav-item .icon {
  margin-right: var(--space-3);
  font-size: var(--text-lg);
  flex-shrink: 0;
}

.sidebar-collapsed .nav-item .text {
  display: none;
}

.sidebar-collapsed .nav-item .icon {
  margin-right: 0;
}

/* 移动端：侧边栏抽屉化 */
@media (max-width: 768px) {
  .admin-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 1000;
    width: 250px;
    height: 100vh;
    margin-right: 0;
    border-radius: 0;
    box-shadow: var(--shadow-hover);
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }

  .admin-sidebar.mobile-open {
    transform: translateX(0);
  }

  /* 移动端始终展开（显示文字） */
  .sidebar-collapsed {
    width: 250px;
  }

  .sidebar-collapsed .sidebar-header h3,
  .sidebar-collapsed .nav-item .text {
    display: block;
  }

  .sidebar-collapsed .nav-item .icon {
    margin-right: 12px;
  }

  .toggle-sidebar {
    display: none;
  }

  .mobile-close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: none;
    border: none;
    cursor: pointer;
    font-size: 18px;
    color: var(--text-muted);
    padding: 4px;
    border-radius: 4px;
  }

  .mobile-close:hover {
    background: var(--bg-soft);
    color: var(--brand);
  }
}

@media (min-width: 769px) {
  .mobile-close {
    display: none;
  }
}
</style>
