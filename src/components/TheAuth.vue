<!-- src/components/TheAuth.vue -->
<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h1>{{ isLogin ? '登录' : '注册' }}</h1>
        <p>{{ isLogin ? 'PGFPlotsGenerator' : '创建新账户' }}</p>
      </div>

      <!-- 登录/注册切换 -->
      <div class="auth-tabs">
        <button 
          :class="['tab-button', { active: isLogin && !isAdminLogin }]"
          @click="isLogin = true; isAdminLogin = false"
        >
          登录
        </button>
        <button 
          :class="['tab-button', { active: !isLogin && !isAdminLogin }]"
          @click="isLogin = false; isAdminLogin = false"
        >
          注册
        </button>
        <button 
          :class="['tab-button admin-tab', { active: isAdminLogin }]"
          @click="activateAdminLogin"
        >
          管理员登录
        </button>
      </div>

      <!-- 动态显示登录、注册或管理员登录表单 -->
      <LoginForm v-if="isLogin && !isAdminLogin" @success="handleAuthSuccess" />
      <RegisterForm v-else-if="!isLogin && !isAdminLogin" @success="handleAuthSuccess" />
      <AdminLogin v-else @success="handleAdminSuccess" />
    </div>
  </div>
</template>

<script>
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'
import AdminLogin from './AdminLogin.vue'

export default {
  name: 'TheAuth',
  components: {
    LoginForm,
    RegisterForm,
    AdminLogin
  },
  data() {
    return {
      isLogin: true,  // 默认显示登录表单
      isAdminLogin: false  // 是否显示管理员登录
    }
  },
  methods: {
    handleAuthSuccess(userData) {
      // 认证成功，通知父组件
      this.$emit('auth-success', userData)
    },
    handleAdminSuccess(adminData) {
      // 管理员登录成功
      this.$emit('admin-success', adminData)
    },
    activateAdminLogin() {
      this.isAdminLogin = true
      this.isLogin = true // 保持isLogin为true，以便header显示正确的标题
    }
  }
}
</script>

<style scoped>
.auth-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--brand-gradient);
  padding: 20px;
}

.auth-card {
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  padding: var(--space-9);
  box-shadow: var(--shadow-base);
  width: 100%;
  max-width: 400px;
}

.auth-header {
  text-align: center;
  margin-bottom: var(--space-7);
}

.auth-header h1 {
  color: var(--text-strong);
  margin-bottom: var(--space-2);
  font-size: var(--text-3xl);
}

.auth-header p {
  color: var(--text-muted);
  margin: 0;
}

.auth-tabs {
  display: flex;
  margin-bottom: var(--space-7);
  border-bottom: 1px solid var(--border);
}

.tab-button {
  flex: 1;
  padding: var(--space-3);
  background: none;
  border: none;
  font-size: var(--text-lg);
  cursor: pointer;
  color: var(--text-muted);
  transition: all var(--transition-base);
  border-bottom: 2px solid transparent;
  white-space: nowrap;
}

.tab-button.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
  font-weight: 600;
}

.tab-button.admin-tab.active {
  color: var(--danger);
  border-bottom-color: var(--danger);
}

.tab-button:hover {
  color: var(--brand);
}

.tab-button.admin-tab:hover {
  color: var(--danger);
}
</style>