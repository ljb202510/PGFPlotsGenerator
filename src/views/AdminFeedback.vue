<!-- 管理员反馈管理界面 -->
 <!-- AdminFeedback.vue -->
<template>
  <div class="admin-feedback-container">
    <!-- 标题区域 -->
    <div class="header">
      <h1><i class="fas fa-comments"></i> 反馈管理</h1>
      <p class="subtitle">管理系统用户提交的反馈和建议</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-cards">
      <div class="stat-card total">
        <div class="stat-icon">
          <i class="fas fa-inbox"></i>
        </div>
        <div class="stat-info">
          <h3>{{ totalFeedbacks }}</h3>
          <p>总反馈数</p>
        </div>
      </div>
      <div class="stat-card pending">
        <div class="stat-icon">
          <i class="fas fa-clock"></i>
        </div>
        <div class="stat-info">
          <h3>{{ pendingFeedbacks }}</h3>
          <p>待处理</p>
        </div>
      </div>
      <div class="stat-card replied">
        <div class="stat-icon">
          <i class="fas fa-check-circle"></i>
        </div>
        <div class="stat-info">
          <h3>{{ repliedFeedbacks }}</h3>
          <p>已回复</p>
        </div>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <div class="filter-toolbar">
      <div class="filter-group">
        <label for="typeFilter"><i class="fas fa-filter"></i> 反馈类型</label>
        <select id="typeFilter" v-model="filters.type" @change="onFilterChange">
          <option value="">全部类型</option>
          <option value="suggestion">功能建议</option>
          <option value="ui">界面优化</option>
          <option value="bug">产品bug</option>
          <option value="other">其他问题</option>
        </select>
      </div>

      <!-- <div class="filter-group">
        <label for="statusFilter"><i class="fas fa-tag"></i> 回复状态</label>
        <select id="statusFilter" v-model="filters.status" @change="loadFeedbacks">
          <option value="">全部状态</option>
          <option value="pending">待回复</option>
          <option value="replied">已回复</option>
        </select>
      </div> -->

      <div class="filter-group">
        <label for="dateFilter"><i class="fas fa-calendar"></i> 时间范围</label>
        <select id="dateFilter" v-model="filters.dateRange" @change="onFilterChange">
          <option value="">全部时间</option>
          <option value="today">今天</option>
          <option value="week">本周</option>
          <option value="month">本月</option>
        </select>
      </div>

      <!-- <div class="search-box">
        <input 
          type="text" 
          v-model="filters.search" 
          placeholder="搜索用户名或反馈内容..."
          @input="onSearchInput"
        >
        <i class="fas fa-search"></i>
      </div> -->
    </div>

    <!-- 反馈表格 -->
    <div class="table-container">
      <div class="table-header">
        <h3><i class="fas fa-list"></i> 反馈列表</h3>
        <div class="table-actions">
          <button class="btn-refresh" @click="loadFeedbacks" :disabled="loading">
            <i class="fas fa-sync-alt" :class="{ 'fa-spin': loading }"></i> 刷新
          </button>
        </div>
      </div>

      <div class="table-responsive">
        <table class="feedback-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>类型</th>
              <th>内容</th>
              <th>状态</th>
              <th class="sortable" @click="toggleSort('feedback_time')">
                提交时间
                <span class="sort-indicator">
                  <span v-if="sortField === 'feedback_time'" class="sort-arrow">
                    {{ sortOrder === 'asc' ? '⬆' : '⬇' }}
                  </span>
                  <span v-else class="sort-placeholder">↕</span>
                </span>
              </th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="feedback in sortedFeedbacks" :key="feedback.feedback_id">
              <td>{{ feedback.username }}</td>
              <td>
                <span class="type-badge" :class="getTypeClass(feedback.type)">
                  {{ getTypeLabel(feedback.type) }}
                </span>
              </td>
              <td class="content-cell">
                <div class="content-preview">
                  {{ truncateContent(feedback.content) }}
                </div>
              </td>
              <td>
                <span class="status-badge" :class="{ 'answered': feedback.answer }">
                  {{ feedback.answer ? '已回复' : '待回复' }}
                </span>
              </td>
              <td>{{ formatDate(feedback.feedback_time) }}</td>
              <td class="actions-cell">
                <button 
                  class="btn-view" 
                  @click="viewFeedback(feedback.feedback_id)"
                  title="查看详情"
                >
                  <p class="fas fa-eye">🔎</p>
                </button>
                <button 
                  class="btn-delete" 
                  @click="confirmDelete(feedback.feedback_id)"
                  title="删除反馈"
                  v-if="!feedback.answer"
                >
                  <el-icon class="trash-icon"><Delete /></el-icon>
                </button>
                <!-- <button 
                  class="btn-reply" 
                  @click="replyFeedback(feedback.feedback_id)"
                  title="回复"
                  v-if="!feedback.answer"
                >
                  <p class="fas fa-reply">✍️</p>
                </button> -->
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 空状态 -->
        <div v-if="feedbacks.length === 0 && !loading" class="empty-state">
          <i class="fas fa-inbox"></i>
          <h4>暂无反馈数据</h4>
          <p>当前筛选条件下没有找到反馈记录</p>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <i class="fas fa-spinner fa-spin"></i>
          <p>加载中...</p>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="totalPages > 1">
        <button 
          class="page-btn" 
          @click="prevPage" 
          :disabled="currentPage === 1"
        >
          <i class="fas fa-chevron-left"></i>
        </button>
        
        <span class="page-info">
          第 {{ currentPage }} 页 / 共 {{ totalPages }} 页
        </span>
        
        <button 
          class="page-btn" 
          @click="nextPage" 
          :disabled="currentPage === totalPages"
        >
          <i class="fas fa-chevron-right"></i>
        </button>
        
        <div class="page-size">
          <select v-model="pageSize" @change="changePageSize">
            <option value="10">10 条/页</option>
            <option value="20">20 条/页</option>
            <option value="50">50 条/页</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 反馈详情弹窗 -->
    <div v-if="showDetailModal" class="modal-overlay" @click.self="closeDetailModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3><i class="fas fa-file-alt"></i> 反馈详情</h3>
          <button class="btn-close" @click="closeDetailModal">
            <i class="fas fa-times"></i>
          </button>
        </div>
        
        <div class="modal-body">
          <div v-if="currentFeedback">
            <div class="detail-section">
              <h4><i class="fas fa-info-circle"></i> 基本信息</h4>
              <div class="detail-grid">
                <div class="detail-item">
                  <label>反馈ID</label>
                  <span>{{ currentFeedback.feedback_id }}</span>
                </div>
                <div class="detail-item">
                  <label>提交用户</label>
                  <span>{{ currentFeedback.username }}</span>
                </div>
                <div class="detail-item">
                  <label>反馈类型</label>
                  <span class="type-badge" :class="getTypeClass(currentFeedback.type)">
                    {{ getTypeLabel(currentFeedback.type) }}
                  </span>
                </div>
                <div class="detail-item">
                  <label>提交时间</label>
                  <span>{{ formatDate(currentFeedback.feedback_time) }}</span>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h4><i class="fas fa-comment"></i> 反馈内容</h4>
              <div class="content-box">
                {{ currentFeedback.content }}
              </div>
            </div>

            <div class="detail-section" v-if="currentFeedback.answer">
              <h4><i class="fas fa-reply"></i> 管理员回复</h4>
              <div class="reply-box">
                <div class="reply-header">
                  <span class="reply-time">
                    {{ formatDate(currentFeedback.answer_time) }}
                  </span>
                </div>
                <div class="reply-content">
                  {{ currentFeedback.answer }}
                </div>
              </div>
            </div>

            <div class="detail-section" v-else>
              <h4><i class="fas fa-reply"></i> 回复反馈</h4>
              <div class="content-box reply-input-box">
                <textarea 
                  v-model="replyContent" 
                  placeholder="请输入回复内容..."
                  rows="6"
                  maxlength="500"
                  class="reply-textarea"
                ></textarea>
              </div>
              <div class="char-count">
                {{ replyContent.length }}/500
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn-cancel" @click="closeDetailModal">
            取消
          </button>
          <button 
            class="btn-submit" 
            @click="submitReply"
            :disabled="!replyContent.trim()"
            v-if="!currentFeedback.answer"
          >
            提交回复
          </button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteModal" class="modal-overlay" @click.self="closeDeleteModal">
      <div class="confirm-modal">
        <div class="confirm-header">
          <i class="fas fa-exclamation-triangle warning-icon"></i>
          <h3>确认删除</h3>
        </div>
        
        <div class="confirm-body">
          <p>确定要删除反馈 {{ feedbackToDelete }} 吗？此操作无法撤销。</p>
        </div>
        
        <div class="confirm-footer">
          <button class="btn-cancel" @click="closeDeleteModal">
            取消
          </button>
          <button class="btn-confirm" @click="deleteFeedback">
            确认删除
          </button>
        </div>
      </div>
    </div>

    <!-- 消息提示 -->
    <div 
      v-if="showMessage" 
      class="message-toast" 
      :class="messageType"
    >
      <i :class="messageIcon"></i>
      <span>{{ messageText }}</span>
    </div>
  </div>
</template>

<script>
import { API_BASE_URL } from '@/config';
import { getAdminToken } from '@/utils/auth';
import { Delete } from '@element-plus/icons-vue';
export default {
  name: 'AdminFeedback',
  components: { Delete },
  data() {
    return {
      // 反馈列表数据
      feedbacks: [],
      loading: false,
      
      // 分页数据
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,

       // 排序参数
      sortField: 'feedback_time', // 默认按提交时间排序
      sortOrder: 'desc', // 默认降序（最新的在前）
      
      // 筛选条件
      filters: {
        type: '',
        status: '',
        dateRange: '',
        search: ''
      },
      
      // 统计信息
      totalFeedbacks: 0,
      pendingFeedbacks: 0,
      repliedFeedbacks: 0,
      
      // 详情弹窗数据
      showDetailModal: false,
      currentFeedback: null,
      replyContent: '',
      
      // 删除确认弹窗
      showDeleteModal: false,
      feedbackToDelete: null,
      
      // 消息提示
      showMessage: false,
      messageText: '',
      messageType: '',
      messageIcon: '',
      
      // 防抖定时器
      searchTimer: null
    }
  },
  
  computed: {
    totalPages() {
      return Math.ceil(this.totalItems / this.pageSize)
    },

    // 添加计算属性，返回排序后的反馈列表
    sortedFeedbacks() {
      if (!this.feedbacks.length) return [];
      
      // 创建数组副本进行排序
      const sorted = [...this.feedbacks];
      
      return sorted.sort((a, b) => {
        let aValue, bValue;
        
        // 根据排序字段获取值
        if (this.sortField === 'feedback_time') {
          aValue = new Date(a.feedback_time).getTime();
          bValue = new Date(b.feedback_time).getTime();
        } else if (this.sortField === 'feedback_id') {
          aValue = a.feedback_id;
          bValue = b.feedback_id;
        } else if (this.sortField === 'username') {
          aValue = a.username || '';
          bValue = b.username || '';
        }
        
        // 根据排序顺序进行比较
        if (this.sortOrder === 'asc') {
          return aValue < bValue ? -1 : aValue > bValue ? 1 : 0;
        } else {
          return aValue > bValue ? -1 : aValue < bValue ? 1 : 0;
        }
      });
    }
  },
  
  mounted() {
    this.loadFeedbacks()
    this.loadStats()
  },
  
  methods: {
    // 切换排序
    toggleSort(field) {
      if (this.sortField === field) {
        // 如果是同一个字段，切换排序顺序
        this.sortOrder = this.sortOrder === 'asc' ? 'desc' : 'asc';
      } else {
        // 如果是不同字段，设置新字段并默认为降序
        this.sortField = field;
        this.sortOrder = 'desc';
      }
      
      // 不需要重新加载数据，因为使用了计算属性自动排序
      console.log(`排序字段: ${this.sortField}, 排序顺序: ${this.sortOrder}`);
    },

    // 获取认证token（统一身份入口：管理员页面只认管理员会话，绝不回落到用户 token）
    getAuthToken() {
      return getAdminToken()
    },
    
    // 加载反馈列表
    async loadFeedbacks() {
      this.loading = true
      try {
        const token = this.getAuthToken()
        if (!token) {
          this.showMessageFunc('请先登录', 'error')
          return
        }
        
        // 构建查询参数
        const params = new URLSearchParams({
          page: this.currentPage,
          limit: this.pageSize
        })
        
      if (this.filters.type) params.append('type', this.filters.type)
      
      // 添加时间范围筛选
      if (this.filters.dateRange) {
        const range = this.getDateRange(this.filters.dateRange)
        if (range) {
          params.append('startDate', range.start)
          params.append('endDate', range.end)
        }
      }
      
        const response = await fetch(`${API_BASE_URL}/api/feedback?${params}`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        })
        
        if (!response.ok) throw new Error('请求失败')
        
        const result = await response.json()
        
        if (result.success) {
          this.feedbacks = result.data.records || []
          this.totalItems = result.data.pagination.total
        } else {
          throw new Error(result.message)
        }
      } catch (error) {
        console.error('加载反馈列表失败:', error)
        this.showMessageFunc('加载失败: ' + error.message, 'error')
      } finally {
        this.loading = false
      }
    },
    
    // 加载统计数据
    async loadStats() {
      try {
        const token = this.getAuthToken()
        if (!token) return
        
        // 这里可以调用单独的统计接口，或者通过现有接口计算
        // 暂时使用现有的列表接口获取统计数据
        const response = await fetch(`${API_BASE_URL}/api/feedback?limit=1000`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        })
        
        if (response.ok) {
          const result = await response.json()
          if (result.success) {
            const allFeedbacks = result.data.records || []
            this.totalFeedbacks = allFeedbacks.length
            this.pendingFeedbacks = allFeedbacks.filter(f => !f.answer).length
            this.repliedFeedbacks = allFeedbacks.filter(f => f.answer).length
          }
        }
      } catch (error) {
        console.error('加载统计数据失败:', error)
      }
    },
    
    // 查看反馈详情
    async viewFeedback(feedbackId) {
      try {
        const token = this.getAuthToken()
        if (!token) return
        
        const response = await fetch(`${API_BASE_URL}/api/feedback/${feedbackId}`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        })
        
        if (!response.ok) throw new Error('请求失败')
        
        const result = await response.json()
        
        if (result.success) {
          this.currentFeedback = result.data
          this.replyContent = result.data.answer || ''
          this.showDetailModal = true
        } else {
          throw new Error(result.message)
        }
      } catch (error) {
        console.error('获取反馈详情失败:', error)
        this.showMessageFunc('获取详情失败: ' + error.message, 'error')
      }
    },
    
    // 提交回复
    async submitReply() {
    if (!this.replyContent.trim()) {
      this.showMessageFunc('请输入回复内容', 'error')
      return
    }
    
    try {
      const token = this.getAuthToken()
      if (!token) {
        this.showMessageFunc('请先登录', 'error')
        return
      }
      
      const response = await fetch(`${API_BASE_URL}/api/feedback/${this.currentFeedback.feedback_id}/reply`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          answer: this.replyContent
        })
      })
      
      const result = await response.json()
      
      if (!response.ok) {
        throw new Error(result.message || '回复失败')
      }
      
      if (result.success) {
        this.showMessageFunc('回复成功', 'success')
        
        // 更新当前反馈数据
        this.currentFeedback.answer = result.data.answer
        this.currentFeedback.answer_time = result.data.answer_time
        
        // 刷新列表和统计
        this.loadFeedbacks()
        this.loadStats()
        
        // 这里可以选择是否立即关闭弹窗，或者保持弹窗显示更新后的回复
        // this.closeDetailModal()
      } else {
        throw new Error(result.message)
      }
      
    } catch (error) {
      console.error('回复失败:', error)
      this.showMessageFunc('回复失败: ' + error.message, 'error')
    }
    },
    
    // 确认删除
    confirmDelete(feedbackId) {
      this.feedbackToDelete = feedbackId
      this.showDeleteModal = true
    },
    
    // 删除反馈
    async deleteFeedback() {
      try {
        const token = this.getAuthToken()
        if (!token) return
        
        const response = await fetch(`${API_BASE_URL}/api/feedback/${this.feedbackToDelete}`, {
          method: 'DELETE',
          headers: {
            'Authorization': `Bearer ${token}`
          }
        })
        
        if (!response.ok) throw new Error('请求失败')
        
        const result = await response.json()
        
        if (result.success) {
          this.showMessageFunc('删除成功', 'success')
          this.closeDeleteModal()
          this.loadFeedbacks()
          this.loadStats()
        } else {
          throw new Error(result.message)
        }
      } catch (error) {
        console.error('删除失败:', error)
        this.showMessageFunc('删除失败: ' + error.message, 'error')
      }
    },
    
    // 搜索输入防抖
    onSearchInput() {
      clearTimeout(this.searchTimer)
      this.searchTimer = setTimeout(() => {
        this.loadFeedbacks()
      }, 500)
    },
    
    // 分页方法
    prevPage() {
      if (this.currentPage > 1) {
        this.currentPage--
        this.loadFeedbacks()
      }
    },
    
    nextPage() {
      if (this.currentPage < this.totalPages) {
        this.currentPage++
        this.loadFeedbacks()
      }
    },
    
    changePageSize() {
      this.currentPage = 1
      this.loadFeedbacks()
    },
    
    // 工具方法
    getTypeLabel(type) {
      const typeMap = {
        suggestion: '功能建议',
        ui: '界面优化',
        bug: '产品bug',
        other: '其他问题'
      }
      return typeMap[type] || type
    },
    
    getTypeClass(type) {
      return `type-${type}`
    },
    
    // 根据选择的时间范围计算起止日期（本地时区，YYYY-MM-DD）
    getDateRange(range) {
      const now = new Date()
      const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
      let start
      if (range === 'today') {
        start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
      } else if (range === 'week') {
        const day = now.getDay() || 7 // 周一为一周开始
        start = new Date(now)
        start.setDate(now.getDate() - day + 1)
        start.setHours(0, 0, 0, 0)
      } else if (range === 'month') {
        start = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0)
      } else {
        return null
      }
      const fmt = (d) => {
        const y = d.getFullYear()
        const m = String(d.getMonth() + 1).padStart(2, '0')
        const day = String(d.getDate()).padStart(2, '0')
        return `${y}-${m}-${day}`
      }
      return { start: fmt(start), end: fmt(end) }
    },
    
    // 筛选条件变化时重置到第 1 页，避免停留在超出结果的页码
    onFilterChange() {
      this.currentPage = 1
      this.loadFeedbacks()
    },
    
    truncateContent(content, length = 50) {
      if (!content) return ''
      return content.length > length ? content.substring(0, length) + '...' : content
    },
    
    formatDate(dateString) {
      if (!dateString) return ''
      const date = new Date(dateString)
      return date.toLocaleString('zh-CN')
    },
    
    // 关闭弹窗
    closeDetailModal() {
      this.showDetailModal = false
      this.currentFeedback = null
      this.replyContent = ''
    },
    
    closeDeleteModal() {
      this.showDeleteModal = false
      this.feedbackToDelete = null
    },
    
    // 显示消息
    showMessageFunc(text, type) {
      this.messageText = text
      this.messageType = type
      this.showMessage = true
      
      if (type === 'success') {
        this.messageIcon = 'fas fa-check-circle'
      } else {
        this.messageIcon = 'fas fa-exclamation-circle'
      }
      
      setTimeout(() => {
        this.showMessage = false
      }, 3000)
    }
  }
}
</script>

<style scoped>
/* 反馈回复样式 */
.reply-input-box {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  padding: 16px;
}

.reply-input-box .reply-textarea {
  width: 100%;
  min-height: 120px;
  border: none;
  outline: none;
  resize: vertical;
  font-size: var(--text-base);
  line-height: 1.5;
  font-family: inherit;
  background: transparent;
}
/* 排序相关样式 */
.sortable {
  cursor: pointer;
  user-select: none;
  position: relative;
  padding-right: 30px !important;
  transition: background-color 0.3s ease;
}

.sortable:hover {
  background-color: var(--bg-hover);
}

.sort-indicator {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}

.sort-arrow {
  font-size: var(--text-lg);
  font-weight: bold;
  color: var(--brand);
  animation: fadeIn 0.3s ease;
}

.sort-placeholder {
  font-size: var(--text-base);
  color: var(--text-muted);
  opacity: 0.5;
  transition: opacity 0.3s ease;
}

.sortable:hover .sort-placeholder {
  opacity: 1;
  color: var(--text-muted);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}


.admin-feedback-container {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

/* 标题区域 */
.header {
  margin-bottom: 32px;
  text-align: center;
}

.header h1 {
  color: var(--text-regular);
  font-size: 36px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.subtitle {
  color: var(--text-muted);
  font-size: var(--text-lg);
}

/* 统计卡片 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: var(--bg-surface);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: var(--shadow-base);
  transition: transform 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-card.total {
  border-left: 5px solid var(--brand);
}

.stat-card.pending {
  border-left: 5px solid var(--danger);
}

.stat-card.replied {
  border-left: 5px solid var(--success);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-2xl);
}

.total .stat-icon {
  background: rgba(77, 107, 254, 0.1);
  color: var(--brand);
}

.pending .stat-icon {
  background: rgba(245, 108, 108, 0.1);
  color: var(--danger);
}

.replied .stat-icon {
  background: rgba(103, 194, 58, 0.1);
  color: var(--success);
}

.stat-info h3 {
  font-size: var(--text-4xl);
  margin-bottom: 4px;
  color: var(--text-regular);
}

.stat-info p {
  color: var(--text-muted);
  font-size: var(--text-base);
}

/* 筛选工具栏 */
.filter-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 24px;
  padding: 20px;
  background: var(--bg-surface);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
}

.filter-group {
  flex: 1;
  min-width: 180px;
}

.filter-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-regular);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-group select {
  width: 100%;
  padding: 10px;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: var(--text-base);
  transition: all 0.3s;
}

.filter-group select:focus {
  outline: none;
  border-color: var(--brand);
}

.search-box {
  position: relative;
  flex: 2;
  min-width: 280px;
}

.search-box input {
  width: 100%;
  padding: 10px 40px 10px 16px;
  border: 2px solid var(--border);
  border-radius: 8px;
  font-size: var(--text-base);
}

.search-box i {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
}

/* 表格容器 */
.table-container {
  background: var(--bg-surface);
  border-radius: 12px;
  box-shadow: var(--shadow-base);
  overflow: hidden;
}

.table-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-header h3 {
  color: var(--text-regular);
  font-size: var(--text-xl);
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-refresh {
  padding: 8px 16px;
  background: var(--brand);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s;
}

.btn-refresh:hover:not(:disabled) {
  background: var(--brand-hover);
}

.btn-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 表格样式 */
.table-responsive {
  overflow-x: auto;
}

.feedback-table {
  width: 100%;
  border-collapse: collapse;
}

.feedback-table th {
  background: var(--bg-soft);
  padding: 16px;
  text-align: left;
  font-weight: 600;
  color: var(--text-regular);
  border-bottom: 2px solid var(--border);
}

.feedback-table td {
  padding: 16px;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}

.feedback-table tbody tr:hover {
  background: var(--bg-soft);
}

/* 徽章样式 */
.type-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: var(--text-xs);
  font-weight: 500;
}

.type-suggestion {
  background: rgba(77, 107, 254, 0.1);
  color: var(--brand);
  border: 1px solid rgba(77, 107, 254, 0.2);
}

.type-ui {
  background: rgba(77, 107, 254, 0.1);
  color: var(--brand);
  border: 1px solid rgba(77, 107, 254, 0.2);
}

.type-bug {
  background: rgba(245, 108, 108, 0.1);
  color: var(--danger);
  border: 1px solid rgba(245, 108, 108, 0.2);
}

.type-other {
  background: rgba(230, 162, 60, 0.1);
  color: var(--warning);
  border: 1px solid rgba(230, 162, 60, 0.2);
}

.status-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: var(--text-xs);
  background: rgba(245, 108, 108, 0.1);
  color: var(--danger);
  border: 1px solid rgba(245, 108, 108, 0.2);
}

.status-badge.answered {
  background: rgba(103, 194, 58, 0.1);
  color: var(--success);
  border: 1px solid rgba(103, 194, 58, 0.2);
}

/* 操作按钮 */
.actions-cell {
  white-space: nowrap;
}

.actions-cell button {
  padding: 8px 12px;  /* 增加内边距 */
  margin: 0 4px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: var(--text-base);
  /* 添加以下样式确保图标显示 */
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;  /* 确保按钮有足够宽度 */
  height: 36px;     /* 确保按钮有足够高度 */
}

/* 为图标添加具体样式 */
.actions-cell button i {
  font-size: var(--text-base);  /* 明确设置图标大小 */
  display: inline-block;
}

.btn-view {
  background: rgba(77, 107, 254, 0.1);
  color: var(--brand);
}

.btn-view:hover {
  background: rgba(77, 107, 254, 0.2);
}

.btn-delete {
  background: rgba(245, 108, 108, 0.1);
  color: var(--danger);
}

.btn-delete:hover {
  background: rgba(245, 108, 108, 0.2);
}

.btn-reply {
  background: rgba(103, 194, 58, 0.1);
  color: var(--success);
}

.btn-reply:hover {
  background: rgba(103, 194, 58, 0.2);
}

/* 空状态和加载状态 */
.empty-state,
.loading-state {
  padding: 60px 20px;
  text-align: center;
  color: var(--text-muted);
}

.empty-state i,
.loading-state i {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state h4 {
  margin-bottom: 8px;
  color: var(--text-muted);
}

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  gap: 20px;
  border-top: 1px solid var(--border);
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid var(--border);
  background: var(--bg-surface);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  background: var(--bg-soft);
  border-color: var(--brand);
  color: var(--brand);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  color: var(--text-muted);
}

.page-size select {
  padding: 8px 16px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-surface);
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: var(--bg-surface);
  border-radius: 12px;
  width: 90%;
  max-width: 700px;
  max-height: 80vh;
  overflow-y: auto;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from { transform: translateY(50px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  color: var(--text-regular);
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-close {
  background: none;
  border: none;
  font-size: var(--text-xl);
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
}

.btn-close:hover {
  color: var(--danger);
}

.modal-body {
  padding: 24px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  color: var(--text-regular);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item label {
  color: var(--text-muted);
  font-size: var(--text-base);
}

.content-box,
.reply-box {
  background: var(--bg-soft);
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--border);
}

.reply-header {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.reply-time {
  color: var(--text-muted);
  font-size: var(--text-base);
}

.modal-footer {
  padding: 20px 24px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-cancel,
.btn-submit {
  padding: 10px 24px;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.3s;
}

.btn-cancel {
  background: var(--bg-soft);
  border: 1px solid var(--border);
  color: var(--text-muted);
}

.btn-cancel:hover {
  background: var(--bg-hover);
}

.btn-submit {
  background: var(--brand);
  border: none;
  color: white;
}

.btn-submit:hover:not(:disabled) {
  background: var(--brand-hover);
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 确认弹窗 */
.confirm-modal {
  background: var(--bg-surface);
  border-radius: 12px;
  width: 90%;
  max-width: 400px;
  animation: slideUp 0.3s ease;
}

.confirm-header {
  padding: 24px;
  text-align: center;
  border-bottom: 1px solid var(--border);
}

.warning-icon {
  font-size: 48px;
  color: var(--danger);
  margin-bottom: 16px;
}

.confirm-body {
  padding: 24px;
  text-align: center;
}

.confirm-footer {
  padding: 20px 24px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: center;
  gap: 12px;
}

.btn-confirm {
  background: var(--danger);
  color: white;
  border: none;
  padding: 10px 24px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-confirm:hover {
  background: var(--danger);
}

/* 消息提示 */
.message-toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  padding: 16px 24px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  animation: slideInRight 0.3s ease;
  z-index: 1001;
}

@keyframes slideInRight {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.message-toast.success {
  background: var(--success);
  color: white;
}

.message-toast.error {
  background: var(--danger);
  color: white;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-feedback-container {
    padding: 16px;
  }
  
  .stats-cards {
    grid-template-columns: 1fr;
  }
  
  .filter-toolbar {
    flex-direction: column;
  }
  
  .filter-group,
  .search-box {
    min-width: 100%;
  }
  
  .detail-grid {
    grid-template-columns: 1fr;
  }
  
  .actions-cell {
    display: flex;
    gap: 8px;  /* 增加按钮间距 */
  }
  
  .actions-cell button {
    padding: 8px;
    min-width: 36px;
    height: 32px;
    margin: 0;
  }
  
  .actions-cell button i {
    font-size: var(--text-xs);
  }
}
</style>