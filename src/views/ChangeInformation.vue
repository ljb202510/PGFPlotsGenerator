<template>
  <div class="change-info-container">
    <div class="info-card">
      <!-- 头部 -->
      <div class="card-header">
        <h2><el-icon><User /></el-icon> 个人信息管理</h2>
        <button @click="goBack" class="back-btn"><el-icon><ArrowLeft /></el-icon> 返回</button>
      </div>

      <!-- 个人信息部分 -->
      <div class="section">
        <h3 class="section-title"><el-icon><EditPen /></el-icon> 个人信息</h3>
        
        <!-- 修改用户名 -->
        <div class="form-group">
          <label for="username">用户名</label>
          <div class="input-with-btn">
            <input
              id="username"
              v-model="userInfo.username"
              type="text"
              autocomplete="off"
              placeholder="请输入用户名"
              @blur="validateUsername"
              :disabled="!editingUsername"
            />
            <button
              v-if="!editingUsername"
              type="button"
              class="edit-btn"
              @click="startEditUsername"
            >
              修改
            </button>
            <div v-else class="edit-actions">
              <button
                type="button"
                class="cancel-btn"
                @click="cancelEditUsername"
              >
                取消
              </button>
              <button
                type="button"
                class="save-btn"
                @click="saveUsername"
                :disabled="!isUsernameValid || savingUsername"
              >
                {{ savingUsername ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
          <div v-if="usernameError" class="error-text">
            {{ usernameError }}
          </div>
          <div v-if="usernameSuccess" class="success-text">
            {{ usernameSuccess }}
          </div>
        </div>

        <!-- 修改邮箱 -->
        <div class="form-group">
          <label for="email">邮箱</label>
          <div class="input-with-btn">
            <input
              id="email"
              v-model="userInfo.email"
              type="email"
              placeholder="请输入邮箱"
              autocomplete="off"
              @blur="validateEmail"
              :disabled="!editingEmail"
            />
            <button
              v-if="!editingEmail"
              type="button"
              class="edit-btn"
              @click="startEditEmail"
            >
              修改
            </button>
            <div v-else class="edit-actions">
              <button
                type="button"
                class="cancel-btn"
                @click="cancelEditEmail"
              >
                取消
              </button>
              <button
                type="button"
                class="save-btn"
                @click="saveEmail"
                :disabled="!isEmailValid || savingEmail"
              >
                {{ savingEmail ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
          <!-- 邮箱验证码输入 -->
          <div v-if="editingEmail" class="verification-section">
            <label for="verificationCode">邮箱验证码</label>
            <div class="input-with-btn">
              <input
                id="verificationCode"
                v-model="emailVerificationCode"
                autocomplete="off"
                type="text"
                placeholder="请输入验证码"
                maxlength="6"
              />
              <button
                type="button"
                class="send-code-btn"
                @click="sendVerificationCode"
                :disabled="sendCodeDisabled"
              >
                {{ sendCodeBtnText }}
              </button>
            </div>
          </div>
          <div v-if="emailError" class="error-text">
            {{ emailError }}
          </div>
          <div v-if="emailSuccess" class="success-text">
            {{ emailSuccess }}
          </div>
        </div>
      </div>

      <div class="divider"></div>

      <!-- 修改密码部分 -->
      <div class="section">
        <h3 class="section-title"><el-icon><Lock /></el-icon> 修改密码</h3>
        
        <form @submit.prevent="handlePasswordSubmit" class="password-form" >
          <!-- 当前密码 -->
          <div class="form-group">
            <label for="currentPassword">当前密码</label>
            <div class="input-wrapper">
              <input
                id="currentPassword"
                v-model="passwordForm.currentPassword"
                :type="showCurrentPassword ? 'text' : 'password'"
                placeholder="请输入当前密码"
                @blur="validateCurrentPassword"
              />
              <button
                type="button"
                class="toggle-password"
                @click="showCurrentPassword = !showCurrentPassword"
              >
                <el-icon v-if="showCurrentPassword"><Hide /></el-icon>
                <el-icon v-else><View /></el-icon>
              </button>
            </div>
            <div v-if="passwordErrors.currentPassword" class="error-text">
              {{ passwordErrors.currentPassword }}
            </div>
          </div>

          <!-- 新密码 -->
          <div class="form-group">
            <label for="newPassword">新密码</label>
            <div class="input-wrapper">
              <input
                id="newPassword"
                v-model="passwordForm.newPassword"
                :type="showNewPassword ? 'text' : 'password'"
                placeholder="字母或数字，长度1-8位"
                @blur="validateNewPassword"
              />
              <button
                type="button"
                class="toggle-password"
                @click="showNewPassword = !showNewPassword"
              >
                <el-icon v-if="showNewPassword"><Hide /></el-icon>
                <el-icon v-else><View /></el-icon>
              </button>
            </div>
            <div v-if="passwordErrors.newPassword" class="error-text">
              {{ passwordErrors.newPassword }}
            </div>
            <span class="hint-text">密码只能包含字母和数字，长度 1-8 位</span>
          </div>

          <!-- 确认新密码 -->
          <div class="form-group">
            <label for="confirmPassword">确认新密码</label>
            <div class="input-wrapper">
              <input
                id="confirmPassword"
                v-model="passwordForm.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                placeholder="请再次输入新密码"
                @blur="validateConfirmPassword"
              />
              <button
                type="button"
                class="toggle-password"
                @click="showConfirmPassword = !showConfirmPassword"
              >
                <el-icon v-if="showConfirmPassword"><Hide /></el-icon>
                <el-icon v-else><View /></el-icon>
              </button>
            </div>
            <div v-if="passwordErrors.confirmPassword" class="error-text">
              {{ passwordErrors.confirmPassword }}
            </div>
          </div>

          <!-- 提交按钮 -->
          <button
            type="submit"
            class="submit-btn"
            :disabled="!isPasswordFormValid || passwordLoading"
          >
            {{ passwordLoading ? '修改中...' : '确认修改密码' }}
          </button>
        </form>

        <!-- 密码修改成功提示 -->
        <div v-if="passwordSuccessMessage" class="success-message">
          {{ passwordSuccessMessage }}
        </div>

        <!-- 密码修改错误提示 -->
        <div v-if="passwordErrorMessage" class="error-message">
          {{ passwordErrorMessage }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { API_BASE_URL } from '@/config';
import { User, ArrowLeft, EditPen, Lock, View, Hide } from '@element-plus/icons-vue';
export default {
  name: 'ChangeInformation',
  components: { User, ArrowLeft, EditPen, Lock, View, Hide },
  data() {
    return {
      // 用户信息
      userInfo: {
        username: '',
        email: ''
      },
      originalUserInfo: {
        username: '',
        email: ''
      },
      
      // 用户名编辑状态
      editingUsername: false,
      savingUsername: false,
      usernameError: '',
      usernameSuccess: '',
      
      // 邮箱编辑状态
      editingEmail: false,
      savingEmail: false,
      emailVerificationCode: '',
      emailPassword: '',
      showEmailPassword: false,
      emailError: '',
      emailSuccess: '',
      
      // 验证码发送状态
      sendCodeDisabled: false,
      sendCodeBtnText: '发送验证码',
      countdown: 0,
      
      // 密码修改相关
      passwordForm: {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      },
      passwordErrors: {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      },
      showCurrentPassword: false,
      showNewPassword: false,
      showConfirmPassword: false,
      passwordLoading: false,
      passwordSuccessMessage: '',
      passwordErrorMessage: ''
    }
  },
  computed: {
    currentUser() {
      return this.$store.state.currentUser
    },
    // 检查用户名是否有效
    isUsernameValid() {
      const trimmedUsername = this.userInfo.username.trim();
      const usernameRegex = /^[a-zA-Z0-9_\u4e00-\u9fa5]{1,10}$/;
      return trimmedUsername && 
             trimmedUsername.length <= 10 && 
             usernameRegex.test(trimmedUsername) &&
             trimmedUsername !== this.originalUserInfo.username;
    },
    
    // 检查邮箱是否有效
    isEmailValid() {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      return emailRegex.test(this.userInfo.email) &&
             this.userInfo.email !== this.originalUserInfo.email;
    },
    
    // 检查密码表单是否有效
    isPasswordFormValid() {
      return (
        this.passwordForm.currentPassword &&
        this.passwordForm.newPassword &&
        this.passwordForm.confirmPassword &&
        !this.passwordErrors.currentPassword &&
        !this.passwordErrors.newPassword &&
        !this.passwordErrors.confirmPassword
      )
    }
  },
  created() {
    this.loadUserInfo();
  },
  methods: {
    // 获取认证token
    getAuthToken() {
      const possibleTokens = [
        localStorage.getItem('token'),
        sessionStorage.getItem('token'),
        localStorage.getItem('authToken'),
        sessionStorage.getItem('authToken'),
        localStorage.getItem('userToken'), 
        sessionStorage.getItem('userToken')
      ];
      
      for (let token of possibleTokens) {
        if (token && token !== 'null' && token !== 'undefined') {
          return token.replace(/^["']|["']$/g, '').trim();
        }
      }
      
      return null;
    },
    
    // 用户名相关方法
    startEditUsername() {
      this.editingUsername = true;
      this.usernameError = '';
      this.usernameSuccess = '';
    },
    
    cancelEditUsername() {
      this.userInfo.username = this.originalUserInfo.username;
      this.editingUsername = false;
      this.usernameError = '';
      this.usernameSuccess = '';
    },
    
    validateUsername() {
      const trimmedUsername = this.userInfo.username.trim();
      
      if (!trimmedUsername) {
        this.usernameError = '用户名不能为空';
      } else if (trimmedUsername.length > 10) {
        this.usernameError = '用户名最长为10个字符';
      } else if (!/^[a-zA-Z0-9_\u4e00-\u9fa5]{1,10}$/.test(trimmedUsername)) {
        this.usernameError = '用户名只能包含字母、数字、下划线和中文字符';
      } else if (trimmedUsername === this.originalUserInfo.username) {
        this.usernameError = '新用户名不能与当前用户名相同';
      } else {
        this.usernameError = '';
      }
    },
    //保存用户名方法
    async saveUsername() {
      if (!this.isUsernameValid) {
        this.validateUsername();
        return;
      }
      
      this.savingUsername = true;
      this.usernameError = '';
      this.usernameSuccess = '';
      
      try {
        const token = this.getAuthToken();
        if (!token) {
          this.usernameError = '未找到认证信息，请重新登录';
          return;
        }
        
        const response = await fetch(`${API_BASE_URL}/api/auth/change-username`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({
            newUsername: this.userInfo.username.trim()
          })
        });
        
        const data = await response.json();
        
        if (data.success) {
          this.usernameSuccess = data.message || '用户名修改成功';
          this.originalUserInfo.username = this.userInfo.username;
          this.editingUsername = false;
          
          // 更新 Vuex 中的用户名
          this.$store.commit('UPDATE_USERNAME', this.userInfo.username.trim());

        } else {
          this.usernameError = data.message || '用户名修改失败';
        }
      } catch (error) {
        console.error('修改用户名失败:', error);
        if (error.message.includes('Network')) {
          this.usernameError = '网络错误，请检查服务器连接';
        } else {
          this.usernameError = '修改用户名失败，请稍后重试';
        }
      } finally {
        this.savingUsername = false;
      }
    },

    updateLocalStorage() {
      const storage = localStorage.getItem('token') ? localStorage : sessionStorage;
      const user = storage.getItem('user');
      if (user) {
        const userObj = JSON.parse(user);
        userObj.username = this.userInfo.username.trim();
        storage.setItem('user', JSON.stringify(userObj));
      }
    },

    // 加载用户信息时从 Vuex 获取
    async loadUserInfo() {
      if (this.currentUser) {
        this.userInfo.username = this.currentUser.username;
        this.userInfo.email = this.currentUser.email;
        this.originalUserInfo = { ...this.userInfo };
      } else {
        // 备用方案：从本地存储获取
        const user = localStorage.getItem('user') || sessionStorage.getItem('user');
        if (user) {
          const userData = JSON.parse(user);
          this.userInfo.username = userData.username;
          this.userInfo.email = userData.email;
          this.originalUserInfo = { ...this.userInfo };
        }  
      }
    },
    
    // 邮箱相关方法
    startEditEmail() {
      this.editingEmail = true;
      this.emailError = '';
      this.emailSuccess = '';
      this.emailVerificationCode = '';
      this.emailPassword = '';
    },
    
    cancelEditEmail() {
      this.userInfo.email = this.originalUserInfo.email;
      this.editingEmail = false;
      this.emailError = '';
      this.emailSuccess = '';
      this.emailVerificationCode = '';
      this.emailPassword = '';
    },
    
    validateEmail() {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      
      if (!this.userInfo.email) {
        this.emailError = '邮箱不能为空';
      } else if (!emailRegex.test(this.userInfo.email)) {
        this.emailError = '邮箱格式不正确';
      } else if (this.userInfo.email === this.originalUserInfo.email) {
        this.emailError = '新邮箱不能与当前邮箱相同';
      } else {
        this.emailError = '';
      }
    },
    
    // 发送验证码
    async sendVerificationCode() {
      if (!this.isEmailValid) {
        this.validateEmail();
        return;
      }
      
      try {
        const token = this.getAuthToken();
        if (!token) {
          this.emailError = '未找到认证信息，请重新登录';
          return;
        }
        
        // 这里需要调用发送验证码的接口
        const response = await fetch(`${API_BASE_URL}/api/verification/send-register-code`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({
            email: this.userInfo.email
          })
        });
        
        const data = await response.json();
        
        if (data.success) {
          // 开始倒计时
          this.startCountdown();
        } else {
          this.emailError = data.message || '发送验证码失败';
        }
      } catch (error) {
        console.error('发送验证码失败:', error);
        this.emailError = '发送验证码失败，请稍后重试';
      }
    },
    
    startCountdown() {
      this.countdown = 60;
      this.sendCodeDisabled = true;
      this.sendCodeBtnText = `${this.countdown}秒后重发`;
      
      const timer = setInterval(() => {
        this.countdown--;
        this.sendCodeBtnText = `${this.countdown}秒后重发`;
        
        if (this.countdown <= 0) {
          clearInterval(timer);
          this.sendCodeDisabled = false;
          this.sendCodeBtnText = '发送验证码';
        }
      }, 1000);
    },
    
    async saveEmail() {
      if (!this.isEmailValid) {
        this.validateEmail();
        return;
      }
      
      if (!this.emailVerificationCode) {
        this.emailError = '请输入验证码';
        return;
      }
      
      this.savingEmail = true;
      this.emailError = '';
      this.emailSuccess = '';
      
      try {
        const token = this.getAuthToken();
        if (!token) {
          this.emailError = '未找到认证信息，请重新登录';
          return;
        }
        
        const response = await fetch(`${API_BASE_URL}/api/auth/change-email`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({
            newEmail: this.userInfo.email,
            verificationCode: this.emailVerificationCode
          })
        });
        
        const data = await response.json();
        
        if (data.success) {
          this.emailSuccess = data.message || '邮箱修改成功';
          this.originalUserInfo.email = this.userInfo.email;
          this.editingEmail = false;
        } else {
          this.emailError = data.message || '邮箱修改失败';
        }
      } catch (error) {
        console.error('修改邮箱失败:', error);
        if (error.message.includes('Network')) {
          this.emailError = '网络错误，请检查服务器连接';
        } else {
          this.emailError = '修改邮箱失败，请稍后重试';
        }
      } finally {
        this.savingEmail = false;
      }
    },
    
    // 密码相关方法（保持原有逻辑）
    validateCurrentPassword() {
      // 当前密码只与后端哈希比对，前端仅做非空判断（由 isPasswordFormValid 控制提交）
      this.passwordErrors.currentPassword = '';
    },
    
    validateNewPassword() {
      const passwordRegex = /^[a-zA-Z0-9]{1,8}$/;
      if (!this.passwordForm.newPassword) {
        this.passwordErrors.newPassword = '';
      } else if (!passwordRegex.test(this.passwordForm.newPassword)) {
        this.passwordErrors.newPassword = '密码只能包含字母和数字，长度 1-8 位';
      } else {
        this.passwordErrors.newPassword = '';
      }
      
      if (this.passwordForm.confirmPassword) {
        this.validateConfirmPassword();
      }
    },
    
    validateConfirmPassword() {
      if (!this.passwordForm.confirmPassword) {
        this.passwordErrors.confirmPassword = '';
      } else if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
        this.passwordErrors.confirmPassword = '两次输入的密码不一致';
      } else {
        this.passwordErrors.confirmPassword = '';
      }
    },
    
    validatePasswordForm() {
      this.validateCurrentPassword();
      this.validateNewPassword();
      this.validateConfirmPassword();
      
      return !this.passwordErrors.currentPassword && 
             !this.passwordErrors.newPassword && 
             !this.passwordErrors.confirmPassword;
    },
    
    async handlePasswordSubmit() {
      if (!this.validatePasswordForm()) {
        return;
      }
      
      this.passwordLoading = true;
      this.passwordErrorMessage = '';
      this.passwordSuccessMessage = '';
      
      try {
        const token = this.getAuthToken();
        
        if (!token) {
          this.passwordErrorMessage = '未找到认证信息，请重新登录';
          this.passwordLoading = false;
          return;
        }

        const response = await fetch(`${API_BASE_URL}/api/auth/change-password`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({
            currentPassword: this.passwordForm.currentPassword,
            newPassword: this.passwordForm.newPassword
          })
        });

        const data = await response.json();

        if (data.success) {
          this.passwordSuccessMessage = data.message || '密码修改成功！';
          this.passwordForm.currentPassword = '';
          this.passwordForm.newPassword = ''; 
          this.passwordForm.confirmPassword = '';
        } else {
          this.passwordErrorMessage = data.message || '密码修改失败';
        }
      } catch (error) {
        console.error('修改密码失败:', error);
        if (error.message.includes('Network')) {
          this.passwordErrorMessage = '网络错误，请检查服务器连接';
        } else {
          this.passwordErrorMessage = '修改密码失败，请稍后重试';
        }
      } finally {
        this.passwordLoading = false;
      }
    },
    
    goBack() {
      this.$router.go(-1);
    }
  }
}
</script>

<style scoped>
.change-info-container {
  min-height: 600px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 40px 20px;
  background-color: var(--bg-soft);
}

.info-card {
  background: var(--bg-surface);
  border-radius: 12px;
  box-shadow: var(--shadow-base);
  padding: 30px;
  width: 100%;
  max-width: 550px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 2px solid var(--border);
}

.card-header h2 {
  color: var(--text-strong);
  margin: 0;
  font-size: 24px;
}

.back-btn {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 14px;
  transition: all 0.3s;
}

.back-btn:hover {
  background: var(--bg-hover);
  color: var(--text-strong);
}

.section {
  margin-bottom: 30px;
}

.section-title {
  color: var(--text-regular);
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
  font-size: 18px;
  display: flex;
  align-items: center;
}

.section-title::before {
  margin-right: 10px;
}

.divider {
  height: 1px;
  background: var(--border);
  margin: 30px 0;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.form-group label {
  font-weight: 600;
  color: var(--text-strong);
  font-size: 14px;
}

.input-with-btn {
  display: flex;
  gap: 10px;
  align-items: center;
}

.input-with-btn input {
  flex: 1;
  padding: 12px 15px;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.input-with-btn input:focus {
  outline: none;
  border-color: var(--brand);
}

.input-with-btn input:disabled {
  background-color: var(--bg-soft);
  cursor: not-allowed;
}

.edit-btn, .send-code-btn {
  background: var(--brand);
  color: white;
  border: none;
  padding: 10px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
  transition: background 0.3s;
}

.edit-btn:hover:not(:disabled) {
  background: var(--brand-hover);
}

.edit-btn:disabled, .send-code-btn:disabled {
  background: var(--text-muted);
  cursor: not-allowed;
}

.send-code-btn {
  background: var(--success);
}

.send-code-btn:hover:not(:disabled) {
  background: var(--success);
}

.edit-actions {
  display: flex;
  gap: 10px;
}

.cancel-btn, .save-btn {
  padding: 10px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  border: 1px solid var(--border);
  white-space: nowrap;
}

.cancel-btn {
  background: var(--bg-soft);
  color: var(--text-muted);
}

.cancel-btn:hover {
  background: var(--bg-hover);
}

.save-btn {
  background: var(--success);
  color: white;
  border: none;
}

.save-btn:hover:not(:disabled) {
  background: var(--success);
}

.save-btn:disabled {
  background: var(--text-muted);
  cursor: not-allowed;
}

.verification-section {
  margin-top: 15px;
  padding: 15px;
  background: var(--bg-soft);
  border-radius: 8px;
  border: 1px solid var(--border);
}

.verification-section label {
  display: block;
  margin-bottom: 5px;
  font-weight: 600;
  color: var(--text-strong);
  font-size: 13px;
}

.password-input {
  width: calc(100% - 40px);
  padding: 10px 15px;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  margin-top: 5px;
  margin-bottom: 10px;
}

.toggle-password {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  padding: 8px;
  margin-left: 10px;
}

.toggle-password.small {
  font-size: 14px;
  padding: 6px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper input {
  width: 100%;
  padding: 12px 45px 12px 15px;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.3s;
}
.input-wrapper input[type="password"]::-ms-reveal {
  display: none;
}

.input-wrapper input:focus {
  outline: none;
  border-color: var(--brand);
}

.error-text, .success-text {
  font-size: 12px;
  margin-top: 4px;
}

.error-text {
  color: var(--danger);
}

.success-text {
  color: var(--success);
}

.hint-text {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 4px;
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.submit-btn {
  background: var(--danger);
  color: white;
  border: none;
  padding: 14px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.3s;
  margin-top: 10px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--danger);
}

.submit-btn:disabled {
  background: var(--text-muted);
  cursor: not-allowed;
}

.success-message, .error-message {
  padding: 12px;
  border-radius: 6px;
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
}

.success-message {
  background: var(--success-soft);
  color: var(--success);
}

.error-message {
  background: var(--danger-soft);
  color: var(--danger);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .info-card {
    padding: 20px;
    max-width: 100%;
  }
  
  .card-header {
    flex-direction: column;
    gap: 15px;
    align-items: flex-start;
  }
  
  .input-with-btn {
    flex-direction: column;
    gap: 10px;
  }
  
  .edit-actions {
    width: 100%;
  }
  
  .cancel-btn, .save-btn {
    flex: 1;
  }
}
</style>