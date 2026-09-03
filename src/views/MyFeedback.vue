<template>
  <div class="feedback-container">
    <div class="feedback-form">
      <h2><i class="fas fa-comment-dots"></i> 问题反馈</h2>
      <p class="subtitle">在这里提交您的问题和建议，帮助我们改进产品</p>
      
      <form @submit.prevent="submitFeedback">
        <div class="form-group">
          <label for="feedbackType"><i class="fas fa-tag"></i> 反馈类型</label>
          <select 
            id="feedbackType" 
            v-model="feedbackType"
            required
          >
            <option value="" disabled selected>请选择反馈类型</option>
            <option value="suggestion">功能建议</option>
            <option value="ui">界面优化</option>
            <option value="bug">产品bug</option>
            <option value="other">其他问题</option>
          </select>
        </div>
        
        <div class="form-group">
          <label for="feedbackContent"><i class="fas fa-edit"></i> 反馈内容</label>
          <textarea 
            id="feedbackContent" 
            v-model="feedbackContent"
            placeholder="请详细描述您遇到的问题或建议..." 
            maxlength="100" 
            required
          ></textarea>
          <div 
            class="char-count" 
            :class="{ warning: charCount > 80 }"
          >
            {{ charCount }}/100
          </div>
        </div>
        
        <button 
          type="submit" 
          class="submit-btn"
          :disabled="isSubmitting"
        >
          <i class="fas fa-paper-plane"></i> 
          {{ isSubmitting ? '提交中...' : '提交反馈' }}
        </button>
      </form>
      
      <div 
        class="message" 
        :class="messageClass"
        v-if="showMessage"
      >
        <i :class="messageIcon"></i> {{ messageText }}
      </div>
    </div>
    
    <div class="feedback-image">
      <div class="image-content">
        <i class="fas fa-headset"></i>
        <h3>您的意见很重要</h3>
        <p>我们珍视每一位用户的反馈，您的建议将帮助我们持续改进产品体验，打造更优质的服务。</p>
      </div>
    </div>
  </div>
</template>

<script>
import { API_BASE_URL } from '@/config';
export default {
  name: 'MyFeedback',
  data() {
    return {
      feedbackType: '',
      feedbackContent: '',
      isSubmitting: false,
      showMessage: false,
      messageText: '',
      messageClass: '',
      messageIcon: ''
    }
  },
  computed: {
    charCount() {
      return this.feedbackContent.length
    }
  },
  methods: {
    async submitFeedback() {
      // 表单验证
      if (!this.feedbackType) {
        this.showMessageFunc('请选择反馈类型', 'error');
        return;
      }
      
      if (!this.feedbackContent.trim()) {
        this.showMessageFunc('请输入反馈内容', 'error');
        return;
      }
      
      //this.isSubmitting = true;
      
      try {
        // 获取token
        const token = this.getAuthToken()
        
        if (!token) {
          this.errorMessage = '未找到认证信息，请重新登录'
          this.loading = false
          return
        }

        // 调用后端API提交反馈
        const response = await fetch(`${API_BASE_URL}/api/feedback`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({
            type: this.feedbackType,
            content: this.feedbackContent
          })
        });
        
        const result = await response.json();
        
        if (result.success) {
          this.showMessageFunc('感谢您的反馈！我们已经收到您的问题，会尽快处理。', 'success');
          // 重置表单
          this.feedbackType = '';
          this.feedbackContent = '';
        } else {
          this.showMessageFunc('提交失败: ' + result.message, 'error');
        }
      } catch (error) {
        console.error('提交反馈时出错:', error);
        this.showMessageFunc('网络错误，请稍后重试', 'error');
      } finally {
        this.isSubmitting = false;
      }
    },

    // 获取认证token
    getAuthToken() {
      // 尝试从多个可能的存储位置获取 token
      const possibleTokens = [
        localStorage.getItem('token'),
        sessionStorage.getItem('token'),
        localStorage.getItem('authToken'),
        sessionStorage.getItem('authToken'),
        localStorage.getItem('userToken'), 
        sessionStorage.getItem('userToken')
      ]
      
      // 返回第一个有效的 token
      for (let token of possibleTokens) {
        if (token && token !== 'null' && token !== 'undefined') {
          // 清理 token（移除可能的引号或空格）
          return token.replace(/^["']|["']$/g, '').trim()
        }
      }
            
      return null
    },
    
    showMessageFunc(text, type) {
      this.messageText = text;
      this.messageClass = type;
      this.showMessage = true;
      
      if (type === 'success') {
        this.messageIcon = 'fas fa-check-circle';
      } else {
        this.messageIcon = 'fas fa-exclamation-circle';
      }
      
      // 5秒后自动隐藏消息
      setTimeout(() => {
        this.showMessage = false;
      }, 5000);
    }
  }
}
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  font-family: var(--font-sans);
}

.feedback-container {
  display: flex;
  max-width: 1200px;
  width: 100%;
  background: var(--bg-surface);
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  animation: fadeIn 0.8s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.feedback-form {
  flex: 2;
  padding: 40px;
  background: var(--bg-surface);
}

.feedback-image {
  flex: 1;
  background: var(--brand-gradient);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 30px;
  color: white;
  position: relative;
  overflow: hidden;
}

.feedback-image::before {
  content: '';
  position: absolute;
  width: 150%;
  height: 150%;
  background: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" preserveAspectRatio="none"><path d="M0,0 L100,0 L100,100 Z" fill="rgba(255,255,255,0.1)"/></svg>');
  background-size: cover;
  top: 0;
  left: 0;
  transform: rotate(15deg);
}

.image-content {
  position: relative;
  z-index: 1;
  text-align: center;
}

.image-content i {
  font-size: 80px;
  margin-bottom: 20px;
  opacity: 0.9;
}

.image-content h3 {
  font-size: 24px;
  margin-bottom: 15px;
  font-weight: 600;
  color: var(--on-brand);
}

.image-content p {
  font-size: 16px;
  opacity: 0.9;
  line-height: 1.6;
}

h2 {
  color: var(--text-regular);
  font-size: 32px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}

h2 i {
  margin-right: 12px;
  color: var(--brand);
}

.subtitle {
  color: var(--text-muted);
  margin-bottom: 30px;
  font-size: 16px;
}

.form-group {
  margin-bottom: 25px;
}

label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-regular);
  font-weight: 500;
}

select, textarea {
  width: 100%;
  padding: 14px;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: 16px;
  transition: all 0.3s;
  background-color: var(--bg-soft);
}

select:focus, textarea:focus {
  outline: none;
  border-color: var(--brand);
  background-color: white;
  box-shadow: 0 0 0 3px var(--brand-soft);
}

textarea {
  min-height: 150px;
  resize: vertical;
  font-family: inherit;
}

.char-count {
  text-align: right;
  font-size: 14px;
  color: var(--text-muted);
  margin-top: 5px;
}

.char-count.warning {
  color: var(--danger);
}

.submit-btn {
  background: linear-gradient(135deg, var(--brand) 0%, var(--text-regular) 100%);
  color: white;
  border: none;
  padding: 16px 30px;
  font-size: 18px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  box-shadow: 0 4px 15px rgba(77, 107, 254, 0.3);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-3px);
  box-shadow: 0 7px 20px rgba(77, 107, 254, 0.4);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(1px);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.submit-btn i {
  margin-right: 10px;
}

.message {
  padding: 15px;
  border-radius: 8px;
  text-align: center;
  margin-top: 20px;
  animation: slideDown 0.5s ease-out;
}

.message.success {
  background: var(--success);
  color: white;
}

.message.error {
  background: var(--danger);
  color: white;
}

.message i {
  margin-right: 8px;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-20px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 900px) {
  .feedback-container {
    flex-direction: column;
  }
  
  .feedback-image {
    order: -1;
    padding: 40px 20px;
  }
  
  .image-content i {
    font-size: 60px;
  }
}
</style>