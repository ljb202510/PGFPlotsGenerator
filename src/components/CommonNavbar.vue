<!-- src/components/CommonNavbar.vue -->
<template>
  <nav v-if="showNavbar" class="navbar">
    <div class="nav-brand" @click="goHome">
      <h2><el-icon><DataLine /></el-icon> 智能图表生成系统</h2>
    </div>
    <div class="nav-actions">
      <!-- 通知图标 -->
      <div class="notification-container" @click="goToMyNotice">
        <div class="notification-icon">
          <el-icon class="bell-icon"><Bell /></el-icon>
          <!-- 未读消息数气泡 -->
          <span v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </div>
      </div>

      <!-- 用户下拉菜单（用户名 + 三点图标） -->
      <el-dropdown trigger="click" class="user-dropdown" @command="handleCommand">
        <div class="user-trigger">
          <span class="user-name">{{ user?.username }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="/history"><el-icon><Document /></el-icon>历史记录</el-dropdown-item>
            <el-dropdown-item command="/data-upload"><el-icon><Folder /></el-icon>上传数据</el-dropdown-item>
            <el-dropdown-item command="/feedback"><el-icon><ChatDotRound /></el-icon>问题反馈</el-dropdown-item>
            <el-dropdown-item divided command="profile"><el-icon><User /></el-icon>个人信息</el-dropdown-item>
            <el-dropdown-item command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </nav>
</template>

<script>
import { DataLine, Bell, Document, Folder, ChatDotRound, User, SwitchButton } from '@element-plus/icons-vue'

export default {
  name: 'CommonNavbar',
  components: { DataLine, Bell, Document, Folder, ChatDotRound, User, SwitchButton },
  props: {
    // 控制是否显示导航栏
    showNavbar: {
      type: Boolean,
      default: true
    },
    // 用户信息
    user: {
      type: Object,
      default: () => ({})
    },
    // 未读消息数量
    unreadCount: {
      type: Number,
      default: 0
    }
  },
  methods: {
    // 点击品牌回首页
    goHome() {
      this.$router.push('/chart-generator')
    },

    // 下拉菜单命令分发
    handleCommand(cmd) {
      if (cmd === 'logout') return this.$emit('logout')
      if (cmd === 'profile') return this.$emit('user-click')
      this.$router.push(cmd)
    },

    // 跳转到通知页面
    goToMyNotice() {
      this.$router.push('/MyNotice');
    }
  }
}
</script>

<style scoped>
.navbar {
  background: var(--bg-surface);
  padding: 0 20px;
  box-shadow: var(--shadow-card);
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
}

.nav-brand {
  cursor: pointer;
}

.nav-brand h2 {
  color: var(--text-strong);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  margin-right: var(--space-6);
}

/* 用户下拉触发器（用户名 + 三点图标） */
.user-dropdown {
  display: flex;
  align-items: center;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-sm);
  color: var(--text-regular);
  transition: background-color var(--transition-base), color var(--transition-base);
}

.user-trigger:hover {
  background: var(--brand-soft);
  color: var(--brand);
}

.user-name {
  font-size: var(--text-lg);
  color: inherit;
}

/* 通知容器样式 */
.notification-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  transition: background-color var(--transition-base);
  position: relative;
  min-width: 60px;
}

.notification-container:hover {
  background: var(--brand-soft);
}

.notification-icon {
  transition: transform 0.2s ease;
  position: relative;
  font-size: 20px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
}

.notification-container:hover .notification-icon {
  transform: scale(1.12);
}

.bell-icon {
  font-size: 20px;
}

/* 未读消息气泡 */
.badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background-color: var(--danger);
  color: var(--on-brand);
  font-size: var(--text-xs);
  min-width: 18px;
  height: 18px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  font-weight: var(--weight-bold);
}
</style>
