<template>
  <div class="my-notice-container">
    <!-- 头部 -->
    <div class="notice-header">
      <h1 class="page-title">我的通知</h1>
      <div class="header-actions">
        <AppButton variant="primary" :disabled="loading || unreadCount === 0" @click="markAllRead">
          全部标为已读
        </AppButton>
        <AppButton variant="secondary" :disabled="loading" @click="refreshNotices">
          {{ loading ? '刷新中...' : '刷新' }}
        </AppButton>
      </div>
    </div>

    <!-- 通知统计 -->
    <div class="notice-stats">
      <div class="stat-item">
        <span class="stat-label">总通知数</span>
        <span class="stat-value total">{{ notices.length }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">反馈通知</span>
        <span class="stat-value feedback">{{ feedbackNotices.length }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">系统通知</span>
        <span class="stat-value system">{{ systemNotices.length }}</span>
      </div>
    </div>

    <!-- 标签页 -->
    <div class="notice-tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.id"
        :class="['tab-btn', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <AppSpinner size="36px" />
      <p>加载通知中...</p>
    </div>

    <!-- 通知列表 -->
    <div v-else class="notice-list">
      <!-- 空状态 -->
      <EmptyState
        v-if="filteredNotices.length === 0"
        :text="`您暂时没有${activeTab === 'all' ? '' : activeTab === 'feedback' ? '反馈' : '系统'}通知`"
        description="有新动态时会在这里提醒您"
      />

      <!-- 通知项 -->
      <div 
        v-for="notice in filteredNotices" 
        :key="notice.id"
        class="notice-item"
        :class="{ unread: !notice.isRead }"
        @click="markAsRead(notice)"
      >
        <div class="notice-icon">
          <el-icon v-if="notice.type === 'feedback'"><ChatDotRound /></el-icon>
          <el-icon v-else><Bell /></el-icon>
        </div>
        
        <div class="notice-content">
          <div class="notice-header">
            <h4 class="notice-title">{{ notice.title }}</h4>
            <span class="notice-time">{{ format_ago_Time(notice.time) }}</span>
          </div>
          
          <div class="notice-body">
            <p class="notice-text">{{ notice.content }}</p>
            
            <!-- 新增：反馈时间显示 -->
            <div v-if="notice.type === 'feedback'" class="feedback-time">
              <span class="notice-text">反馈时间：</span>
              <span class="notice-text">{{ formatDateTime(notice.feedbackTime) }}</span>
            </div>
            <!-- 如果是反馈且有回复 -->
            <div v-if="notice.type === 'feedback' && notice.reply" class="notice-reply">
              <div class="reply-header">
                <span class="reply-label">管理员回复：</span>
              </div>
              <p class="reply-content">{{ notice.reply }}</p>
            </div>
            
            <!-- 反馈信息 -->
            <div v-if="notice.type === 'feedback'" class="feedback-info">
              <span class="feedback-type" :class="notice.feedbackType">
                类型：{{ getFeedbackTypeText(notice.feedbackType) }}
              </span>
              <span class="feedback-status">
                状态：{{ notice.reply ? '已回复' : '待回复' }}
              </span>
            </div>
          </div>
          
          <div class="notice-footer">
            <span class="notice-status">
              {{ notice.type === 'feedback' ? '反馈通知' : '系统通知' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import { API_BASE_URL } from '@/config';
import { getUserToken } from '@/utils/auth';
import { ChatDotRound, Bell } from '@element-plus/icons-vue';
export default {
  name: 'MyNotice',
  components: { ChatDotRound, Bell },
  data() {
    return {
      loading: false,
      activeTab: 'all', // 'all', 'feedback', 'system'
      tabs: [
        { id: 'all', label: '全部通知' },
        { id: 'feedback', label: '我的反馈' },
        { id: 'system', label: '系统通知' }
      ],
      notices: []
    };
  },
  computed: {
    // 反馈通知
    feedbackNotices() {
      return this.notices.filter(notice => notice.type === 'feedback');
    },
    // 系统通知
    systemNotices() {
      return this.notices.filter(notice => notice.type === 'system');
    },
    // 当前标签的筛选结果
    filteredNotices() {
      switch (this.activeTab) {
        case 'feedback':
          return this.feedbackNotices;
        case 'system':
          return this.systemNotices;
        default:
          return this.notices;
      }
    },
    // 未读数量
    unreadCount() {
      return this.notices.filter(notice => !notice.isRead).length;
    }
  },
  mounted() {
    this.fetchNotices();
  },
  beforeUnmount() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  },
  methods: {
    // 获取认证token（统一身份入口：只认用户会话，避免与管理员 token 混用）
    getAuthToken() {
      return getUserToken()
    },

    // 获取通知列表（统一消费后端 /api/notice，反馈回复与系统通知同源）
    async fetchNotices() {
      this.loading = true;
      try {
        // 获取token
        const token = this.getAuthToken()
        
        if (!token) {
          this.errorMessage = '未找到认证信息，请重新登录'
          this.loading = false
          return
        }

        const response = await axios.get(`${API_BASE_URL}/api/notice`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });

        if (response.data.success) {
          this.notices = response.data.data.notices || [];
        } else {
          console.error('获取通知失败:', response.data.message);
          this.notices = [];
        }

      } catch (error) {
        console.error('获取通知时出错:', error);
        this.$message.error('获取通知时出错: ' + error.message);
        // 清空数据，不显示任何内容
        this.notices = [];
      } finally {
        this.loading = false;
      }
    },

    // 刷新通知
    refreshNotices() {
      this.fetchNotices();
    },

    // 标记单条通知为已读（反馈回复与系统通知统一走 notice_read）
    async markAsRead(notice) {
      if (notice.isRead) return;
      try {
        const token = this.getAuthToken();
        await axios.post(`${API_BASE_URL}/api/notice/read/${notice.id}`, {}, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        notice.isRead = true;
        this.$store.dispatch('fetchUnreadCount');
      } catch (error) {
        console.error('标记已读失败:', error);
      }
    },

    // 标记全部通知为已读
    async markAllRead() {
      if (this.unreadCount === 0) return;
      try {
        const token = this.getAuthToken();
        await axios.post(`${API_BASE_URL}/api/notice/read-all`, {}, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        this.notices.forEach(n => { n.isRead = true; });
        this.$store.dispatch('fetchUnreadCount');
        this.$message.success('已将全部通知标为已读');
      } catch (error) {
        console.error('全部标记已读失败:', error);
        this.$message.error('操作失败，请重试');
      }
    },
    
    //格式化为标准日期时间格式
    formatDateTime(date) {
      if (!date) return '未知时间';
      
      try {
        return new Date(date).toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        });
      } catch (error) {
        return '无效时间';
      }
    },
    // 格式化时间，显示为几天前
    format_ago_Time(date) {
      if (!date) return '未知时间';
      
      const now = new Date();
      const noticeDate = new Date(date);
      
      // 检查日期是否有效
      if (isNaN(noticeDate.getTime())) {
        return '无效时间';
      }
      
      const diffMs = now - noticeDate;
      const diffHours = diffMs / (1000 * 60 * 60);
      
      if (diffHours < 24) {
        if (diffHours < 1) {
          const diffMinutes = Math.floor(diffMs / (1000 * 60));
          return diffMinutes === 0 ? '刚刚' : `${diffMinutes}分钟前`;
        }
        return `${Math.floor(diffHours)}小时前`;
      } else if (diffHours < 24 * 30) {
        return `${Math.floor(diffHours / 24)}天前`;
      } else {
        return noticeDate.toLocaleDateString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        });
      }
    },
    
    // 获取反馈类型文本
    getFeedbackTypeText(type) {
      const typeMap = {
        'suggestion': '功能建议',
        'ui': '界面问题',
        'bug': 'BUG反馈',
        'other': '其他问题'
      };
      return typeMap[type] || type;
    }
  }
}
</script>

<style scoped>
/* 样式部分保持不变 */
.my-notice-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

/* 头部样式 */
.notice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.page-title {
  font-size: 24px;
  color: var(--text-strong);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

/* 刷新/标为已读按钮已统一为 <AppButton> */

/* 统计信息 */
.notice-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin-bottom: 30px;
}

.stat-item {
  background: var(--bg-surface);
  padding: 20px;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-label {
  font-size: 14px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-value.total {
  color: var(--brand);
}

.stat-value.feedback {
  color: var(--success);
}

.stat-value.system {
  color: var(--info);
}

/* 标签页 */
.notice-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 10px;
}

.tab-btn {
  padding: 8px 16px;
  background: none;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-muted);
  position: relative;
  transition: all 0.3s;
}

.tab-btn:hover {
  background: var(--bg-soft);
}

.tab-btn.active {
  background: var(--brand);
  color: white;
}

/* 加载状态 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: var(--text-muted);
}

/* 加载动画已统一为 <AppSpinner> */
/* 空状态已统一为 <EmptyState> */

/* 通知列表 */
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

/* 通知项 */
.notice-item {
  background: var(--bg-surface);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 15px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.notice-item:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

/* 未读通知：左侧高亮条 + 标题加粗 */
.notice-item.unread {
  border-left: 4px solid var(--brand);
}

.notice-item.unread .notice-title {
  font-weight: 600;
  color: var(--text-strong);
}

.notice-item.unread .notice-icon {
  background: var(--brand-soft);
}

/* 通知图标 */
.notice-icon {
  font-size: 24px;
  padding: 8px;
  background: var(--bg-soft);
  border-radius: var(--radius-sm);
  color: var(--brand);
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 通知内容 */
.notice-content {
  flex: 1;
}

.notice-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.notice-title {
  margin: 0;
  font-size: 16px;
  color: var(--text-strong);
  font-weight: 600;
}

.notice-time {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
}

.notice-body {
  margin-bottom: 10px;
}

.notice-text {
  margin: 0 0 10px 0;
  color: var(--text-regular);
  line-height: 1.5;
}

/* 回复样式 */
.notice-reply {
  background: var(--bg-soft);
  border-left: 3px solid var(--success);
  padding: 12px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  margin: 10px 0;
}

.reply-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.reply-label {
  font-weight: bold;
  color: var(--success);
  font-size: 14px;
}

.reply-content {
  margin: 0;
  color: var(--text-regular);
  line-height: 1.5;
}

/* 反馈信息 */
.feedback-info {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.feedback-type {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--brand-soft);
  color: var(--brand);
}

.feedback-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--bg-soft);
  color: var(--text-muted);
}

.feedback-type.suggestion {
  background: var(--success-soft);
  color: var(--success);
}

.feedback-type.ui {
  background: var(--bg-surface);
  color: var(--warning);
}

.feedback-type.bug {
  background: var(--danger-soft);
  color: var(--danger);
}

.feedback-type.other {
  background: var(--bg-soft);
  color: var(--info);
}

/* 通知底部 */
.notice-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}

.notice-status {
  font-size: 12px;
  color: var(--text-muted);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .notice-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .notice-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }
  
  .notice-header .header-actions {
    align-self: stretch;
  }
  
  .notice-header .btn-refresh {
    flex: 1;
  }
  
  .notice-item {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .notice-stats {
    grid-template-columns: 1fr;
  }
  
  .notice-tabs {
    flex-wrap: wrap;
  }
}
</style>