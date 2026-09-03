<!-- src/components/RegisterForm.vue -->
<template>
  <form @submit.prevent="handleRegister" class="register-form"  >
    <!-- 注意，在最后使用时需要把这个加在上一行。目前为了测试方便就去掉了autocomplete="off" -->
    
    <div class="form-group">
      <label for="username">用户名</label>
      <input
        id="username"
        v-model="form.username"
        type="text"
        placeholder="请输入用户名"
        required
        :class="{ error: errors.username }"
      >
      <span v-if="errors.username" class="error-text">{{ errors.username }}</span>
    </div>

    <div class="form-group">
      <label for="email">邮箱</label>
      <input
        id="email"
        v-model="form.email"
        type="email"
        placeholder="请输入邮箱"
        required
        :class="{ error: errors.email }"
        @blur="checkEmail"
      >
      <span v-if="errors.email" class="error-text">{{ errors.email }}</span>
    </div>

    <!-- 新增验证码输入区域 -->
    <div class="form-group">
      <label for="verificationCode">验证码</label>
      <div class="verification-code-wrapper">
        <input
          id="verificationCode"
          v-model="form.verificationCode"
          type="text"
          placeholder="请输入验证码"
          autocomplete="one-time-code"
          required
          :class="{ error: errors.verificationCode }"
          maxlength="6"
        >
        <button
          type="button"
          class="send-code-btn"
          @click="sendVerificationCode"
          :disabled="sendBtnDisabled || !isEmailValid"
        >
          {{ sendBtnText }}
        </button>
      </div>
      <span v-if="errors.verificationCode" class="error-text">{{ errors.verificationCode }}</span>
      <span v-if="sendCodeError" class="error-text">{{ sendCodeError }}</span>
    </div>

    

    <div class="form-group">
      <label for="password">密码</label>
      <div class="input-wrapper">
        <input  
          id="password"
          v-model="form.password"
          type="text"
        placeholder="字母或数字，长度1-8位"
        autocomplete="new-password"
          required
          :class="{ 
            error: errors.password,
            'password-mode': !showPassword
           }"
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
      <span v-if="errors.password" class="error-text">{{ errors.password }}</span>
      <span class="hint-text">密码只能包含字母和数字，长度 1-8 位</span>
    </div>

    <div class="form-group">
      <label for="confirmPassword">确认密码</label>
      <div class="input-wrapper">
        <input
          id="confirmPassword"
          v-model="form.confirmPassword"
          type="text"
          placeholder="再次输入密码"
          autocomplete="new-password"
          
          required
          :class="{ 
            error: errors.confirmPassword,
            'password-mode': !showPassword }"
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
      <span v-if="errors.confirmPassword" class="error-text">{{ errors.confirmPassword }}</span>
    </div>

    <button 
      type="submit" 
      class="submit-button"
      :disabled="loading"
    >
      <span v-if="loading">注册中...</span>
      <span v-else>注册</span>
    </button>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-if="success" class="success-message">
      注册成功！正在跳转...
    </div>
  </form>
</template>

<script>
import { API_BASE_URL } from '@/config';
import { View, Hide } from '@element-plus/icons-vue'

export default {
  components: { View, Hide },
  name: 'RegisterForm',
  
  data() {
    return {
      form: {
        username: '',
        email: '',
        verificationCode: '',
        password: '',
        confirmPassword: '',
      },
      showPassword: false,
      randomNames: {
        email: 'email_' + Math.random().toString(36).substr(2, 9),
        captcha: 'captcha_' + Math.random().toString(36).substr(2, 9),
        password: 'password_' + Math.random().toString(36).substr(2, 9),
        confirmPassword: 'confirm_' + Math.random().toString(36).substr(2, 9)
      },
      errors: {},
      loading: false,
      error: '',
      success: false,
      // 验证码相关状态
      isEmailValid: false,
      sendBtnDisabled: false,
      sendBtnText: '发送验证码',
      countdown: 60,
      sendCodeError: '',
      countdownTimer: null
    }
  },
  methods: {
    async handleRegister() {
      // 验证表单
      if (!this.validateForm()) return

      this.loading = true
      this.error = ''

      try {
        // 调用后端注册API，现在包含验证码
        const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            username: this.form.username,
            email: this.form.email,
            password: this.form.password,
            verificationCode: this.form.verificationCode,
            
          })
        })

        const data = await response.json()

        if (data.success) {
          this.success = true
          // 注册成功，保存token到localStorage
          if (data.token) {
            localStorage.setItem('token', data.token)
            localStorage.setItem('user', JSON.stringify(data.user))
          }
          // 注册成功，2秒后自动跳转
          setTimeout(() => {
            this.$emit('success', data.user)
          }, 2000)
        } else {
          this.error = data.message || '注册失败'
        }
      } catch (err) {
        this.error = '网络错误，请检查后端服务'
        console.error('注册错误:', err)
      } finally {
        this.loading = false
      }
    },

    // 发送验证码
    async sendVerificationCode() {
      if (!this.form.email || !this.isEmailValid) {
        this.sendCodeError = '请先输入有效的邮箱'
        return
      }

      this.sendBtnDisabled = true
      this.sendCodeError = ''

      try {
        const response = await fetch(`${API_BASE_URL}/api/verification/send-register-code`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            email: this.form.email
          })
        })

        const data = await response.json()

        if (data.success) {
          // 开始倒计时
          this.startCountdown()
        } else {
          this.sendCodeError = data.message || '发送验证码失败'
          this.sendBtnDisabled = false
        }
      } catch (err) {
        this.sendCodeError = '网络错误，请稍后重试'
        console.error('发送验证码错误:', err)
        this.sendBtnDisabled = false
      }
    },

    // 开始倒计时
    startCountdown() {
      this.sendBtnText = `${this.countdown}秒后重发`
      this.countdownTimer = setInterval(() => {
        this.countdown--
        this.sendBtnText = `${this.countdown}秒后重发`
        
        if (this.countdown <= 0) {
          clearInterval(this.countdownTimer)
          this.sendBtnDisabled = false
          this.sendBtnText = '发送验证码'
          this.countdown = 60
        }
      }, 1000)
    },

    // 检查邮箱格式
    checkEmail() {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      this.isEmailValid = emailRegex.test(this.form.email)
      
      if (this.form.email && !this.isEmailValid) {
        this.errors.email = '请输入有效的邮箱地址'
      } else {
        this.errors.email = ''
      }
    },

    validateForm() {
      this.errors = {}

      // 用户名验证
      if (!this.form.username) {
        this.errors.username = '用户名不能为空'
      } else if (this.form.username.length > 10) {
        this.errors.username = '用户名最长为10个字符'
      }

      // 邮箱验证
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!this.form.email) {
        this.errors.email = '邮箱不能为空'
      } else if (!emailRegex.test(this.form.email)) {
        this.errors.email = '请输入有效的邮箱地址'
      }

      // 验证码验证
      if (!this.form.verificationCode) {
        this.errors.verificationCode = '验证码不能为空'
      } else if (this.form.verificationCode.length !== 6) {
        this.errors.verificationCode = '验证码必须是6位数字'
      }

      // 密码验证（仅字母和数字，长度 1-8 位）
      const passwordRegex = /^[a-zA-Z0-9]{1,8}$/;
      if (!this.form.password) {
        this.errors.password = '密码不能为空'
      } else if (!passwordRegex.test(this.form.password)) {
        this.errors.password = '密码只能包含字母和数字，长度 1-8 位'
      }

      // 确认密码验证
      if (!this.form.confirmPassword) {
        this.errors.confirmPassword = '请确认密码'
      } else if (this.form.password !== this.form.confirmPassword) {
        this.errors.confirmPassword = '两次输入的密码不一致'
      }

      

      return Object.keys(this.errors).length === 0
    }
  },
  beforeUnmount() {
    // 清理定时器
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer)
    }
  }
}
</script>

<style scoped>
/* 添加text的自定义黑圆点CSS样式*/ 
.password-mode {
  font-family: var(--font-mono);
  letter-spacing: 1px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-regular);
  /* 隐藏真实文本，显示圆点*/
  -webkit-text-security: disc;
  -moz-text-security: disc;
}


.register-form {
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

    .verification-code-wrapper {
      position: relative;
      display: flex;
      gap: var(--space-3);
    }

.verification-code-wrapper input {
  flex: 1;
}

    .send-code-btn {
      background: var(--brand-gradient);
      color: var(--on-brand);
      border: none;
      padding: 0 var(--space-4);
      border-radius: var(--radius-sm);
      font-size: var(--text-base);
      font-weight: var(--weight-semibold);
      cursor: pointer;
      transition: opacity var(--transition-fast);
      min-width: 120px;
      white-space: nowrap;
    }

.send-code-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.send-code-btn:disabled {
  background: var(--text-muted);
  cursor: not-allowed;
  opacity: 0.7;
}

input[type="text"],
input[type="email"],
input[type="password"],
select {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 16px;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

    select {
      appearance: none;
      background-color: var(--bg-surface);
      background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
      background-repeat: no-repeat;
      background-position: right var(--space-3) center;
      background-size: 16px;
      padding-right: 40px;
    }

input:focus,
select:focus {
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

input.error,
select.error {
  border-color: var(--danger);
}

.error-text {
  color: var(--danger);
  font-size: 14px;
  margin-top: 5px;
  display: block;
}

.hint-text {
  color: var(--text-muted);
  font-size: 13px;
  margin-top: 5px;
  display: block;
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
      transition: opacity var(--transition-fast);
      margin-top: var(--space-2);
    }

.submit-button:hover:not(:disabled) {
  opacity: 0.9;
}

.submit-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  background: var(--danger-soft);
  color: var(--danger);
  padding: 12px;
  border-radius: 6px;
  margin-top: 15px;
  font-size: 14px;
  text-align: center;
}

.success-message {
  background: var(--success-soft);
  color: var(--success);
  padding: 12px;
  border-radius: 6px;
  margin-top: 15px;
  font-size: 14px;
  text-align: center;
}
</style>