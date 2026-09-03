<!-- src/components/AdminLogin.vue -->
<template>
  <form @submit.prevent="handleAdminLogin" class="admin-login-form">
    <div class="form-group">
      <label for="adminAccount">管理员账号</label>
      <div class="input-wrapper">
        <input
          id="adminAccount"
          v-model="form.adminAccount"
          type="text"
          placeholder="请输入管理员账号"
          required
          :class="{ error: errors.adminAccount }"
        >
        <span v-if="errors.adminAccount" class="error-text">{{ errors.adminAccount }}</span>
      </div>
    </div>

    <div class="form-group">
      <label for="password">密码</label>
      <div class="input-wrapper">
        <input
          id="password"
          v-model="form.password"
          :type="showPassword ? 'text' : 'password'"
          placeholder="请输入密码"
          required
          :class="{ error: errors.password }"
        >
        <button
          type="button"
          class="toggle-password"
          @click="showPassword = !showPassword"
        >
          <el-icon v-if="showPassword"><Hide /></el-icon>
          <el-icon v-else><View /></el-icon>
        </button>
      </div>
      <div v-if="errors.password" class="error-text">{{ errors.password }}</div>
    </div>

    <button 
      type="submit" 
      class="submit-button admin-submit"
      :disabled="loading"
    >
      <span v-if="loading">登录中...</span>
      <span v-else>管理员登录</span>
    </button>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>
  </form>
</template>

<script>
import { API_BASE_URL } from '@/config';
import { View, Hide } from '@element-plus/icons-vue'

export default {
  components: { View, Hide },
  name: 'AdminLogin',
  data() {
    return {
      form: {
        adminAccount: '',
        password: ''
      },
      showPassword: false,
      errors: {},
      loading: false,
      error: ''
    }
  },
  methods: {
    async handleAdminLogin() {
      // 验证表单
      if (!this.validateForm()) return

      this.loading = true
      this.error = ''

      try {
        // 调用后端管理员登录API
        const response = await fetch(`${API_BASE_URL}/api/auth/admin/login`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            adminAccount: this.form.adminAccount,
            password: this.form.password
          })
        })

        const data = await response.json()

        if (data.success) {
          // 管理员登录成功，存储token和用户信息
          localStorage.setItem('adminToken', data.token)
          localStorage.setItem('adminUser', JSON.stringify(data.user))
          
          // 通知父组件登录成功
          this.$emit('success', {
            ...data.user,
            token: data.token, // 确保传递了 token
            isAdmin: true
          })
        } else {
          this.error = data.message || '管理员登录失败'
        }
      } catch (err) {
        this.error = '网络错误，请检查后端服务'
        console.error('管理员登录错误:', err)
      } finally {
        this.loading = false
      }
    },

    validateForm() {
      this.errors = {}

      // 管理员账号验证
      if (!this.form.adminAccount) {
        this.errors.adminAccount = '管理员账号不能为空'
      }

      // 密码验证（登录只做非空校验，格式在注册时已约束）
      if (!this.form.password) {
        this.errors.password = '密码不能为空'
      }

      return Object.keys(this.errors).length === 0
    }
  }
}
</script>

<style scoped>
.admin-login-form {
  display: flex;
  flex-direction: column;
}

.form-group {
  margin-bottom: 20px;
}

label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-strong);
  font-weight: 500;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper input {
  width: 100%;
  padding: 12px 45px 12px 15px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 16px;
  transition: border-color 0.3s;
  box-sizing: border-box;
}
.input-wrapper input[type="password"]::-ms-reveal {
  display: none;
}

    .input-wrapper input:focus {
      outline: none;
      border-color: var(--brand);
    }

.toggle-password {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  padding: 4px;
}

input.error {
  border-color: var(--danger);
}

.error-text {
  color: var(--danger);
  font-size: 14px;
  margin-top: 5px;
  display: block;
}

    .admin-submit {
      background: var(--brand-gradient);
      color: var(--on-brand);
      border: none;
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-sm);
      font-size: var(--text-base);
      font-weight: var(--weight-semibold);
      cursor: pointer;
      transition: opacity var(--transition-fast);
    }

.admin-submit:hover:not(:disabled) {
  opacity: 0.9;
}

.admin-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

    .error-message {
      background: var(--danger-soft);
      color: var(--danger);
      padding: var(--space-3);
      border-radius: var(--radius-sm);
      margin-top: var(--space-4);
      font-size: var(--text-base);
      text-align: center;
    }
</style>