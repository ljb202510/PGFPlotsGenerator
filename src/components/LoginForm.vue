<!-- src/components/LoginForm.vue -->
 <!-- 登录表单 包含username password -->
<template>
  <form @submit.prevent="handleLogin" class="login-form">
    <div class="form-group">
      <label for="email">邮箱</label>
      <div class="input-wrapper">
      <input
        id="email"
        v-model="form.email"
        type="email"
        placeholder="请输入邮箱"
        required
        :class="{ error: errors.email }"
      >
      <span v-if="errors.email" class="error-text">{{ errors.email }}</span>
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
<!--暂时不实现  -->
    <!-- <div class="form-options">
      <label class="remember-me">
        <input type="checkbox" v-model="form.rememberMe">
        记住我
      </label>
      <a href="#" class="forgot-password">忘记密码？</a>
    </div> -->

    <button 
      type="submit" 
      class="submit-button"
      :disabled="loading"
    >
      <span v-if="loading">登录中...</span>
      <span v-else>登录</span>
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
  name: 'LoginForm',
  data() {
    return {
      form: {
        email: '',
        password: '',
        rememberMe: false
      },
      showPassword: false,//默然值的声明
      errors: {},
      loading: false,
      error: ''
    }
  },
  methods: {
    async handleLogin() {
      // 验证表单
      if (!this.validateForm()) return

      this.loading = true
      this.error = ''

      try {
        // 调用后端登录API
        const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            email: this.form.email,
            password: this.form.password
          })
        })

        const data = await response.json()

        if (data.success) {
          // 登录成功（后端统一响应：user/token 位于 data 内）
          const payload = data.data || {}
          if (this.form.rememberMe) {
            localStorage.setItem('token', payload.token)
            localStorage.setItem('user', JSON.stringify(payload.user))
          } else {
            sessionStorage.setItem('token', payload.token)
            sessionStorage.setItem('user', JSON.stringify(payload.user))
          }

          this.$emit('success', payload.user)
        } else {
          this.error = data.message || '登录失败'
        }
      } catch (err) {
        this.error = '网络错误，请检查后端服务'
        console.error('登录错误:', err)
      } finally {
        this.loading = false
      }
    },

    validateForm() {
      this.errors = {}

      // 邮箱验证
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!this.form.email) {
        this.errors.email = '邮箱不能为空'
      } else if (!emailRegex.test(this.form.email)) {
        this.errors.email = '请输入有效的邮箱地址'
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
.login-form {
  display: flex;
  flex-direction: column;
}

.form-group {
  margin-bottom: var(--space-5);
}

label {
  display: block;
  margin-bottom: var(--space-2);
  color: var(--text-strong);
  font-weight: var(--weight-medium);
  font-size: var(--text-sm);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper input {
  width: 100%;
  padding: var(--space-3) 45px var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: var(--text-base);
  color: var(--text-regular);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-sizing: border-box;
}
.input-wrapper input[type="password"]::-ms-reveal {
  display: none;
}

.input-wrapper input::placeholder {
  color: var(--text-muted);
}

.input-wrapper input:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.toggle-password {
  position: absolute;
  right: var(--space-3);
  background: none;
  border: none;
  cursor: pointer;
  font-size: var(--text-lg);
  color: var(--text-muted);
  padding: var(--space-1);
  border-radius: var(--radius-xs);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.toggle-password:hover {
  color: var(--brand);
  background: var(--bg-hover);
}

input.error {
  border-color: var(--danger);
}

.error-text {
  color: var(--danger);
  font-size: var(--text-sm);
  margin-top: var(--space-1);
  display: block;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-6);
}

.remember-me {
  display: flex;
  align-items: center;
  font-size: var(--text-base);
  color: var(--text-muted);
  cursor: pointer;
}

.remember-me input {
  margin-right: var(--space-2);
}

.forgot-password {
  color: var(--brand);
  text-decoration: none;
  font-size: var(--text-base);
}

.forgot-password:hover {
  text-decoration: underline;
}

.submit-button {
  background: var(--brand-gradient);
  color: var(--on-brand);
  border: none;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  cursor: pointer;
  transition: opacity var(--transition-fast), box-shadow var(--transition-fast);
}

.submit-button:hover:not(:disabled) {
  box-shadow: var(--shadow-brand);
}

.submit-button:disabled {
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