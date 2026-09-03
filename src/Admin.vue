<!-- src/Admin.vue -->
<template>
  <div class="admin-welcome">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="banner-content">
        <h1 class="welcome-title">欢迎回来，{{ admin.username }}！</h1>
        <p class="welcome-subtitle">PGFPlotsGenerator 管理员控制台</p>
        <div class="current-time">当前时间：{{ currentTime }}</div>
        
        <!-- 加载状态 -->
        <div v-if="loading" class="loading-stats">
          <div class="loading-spinner"></div>
          <span>正在加载统计数据...</span>
        </div>
        
        <!-- 统计数据 -->
        <div v-else class="admin-stats">
          <div class="stat-card">
            <div class="stat-icon"><el-icon><User /></el-icon></div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.users }}</div>
              <div class="stat-label">注册用户</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon"><el-icon><Folder /></el-icon></div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.files }}</div>
              <div class="stat-label">数据文件</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon"><el-icon><Document /></el-icon></div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.generations }}</div>
              <div class="stat-label">生成记录</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon"><el-icon><ChatDotRound /></el-icon></div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.feedback }}</div>
              <div class="stat-label">用户反馈</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 管理员信息 -->
    <div class="admin-info-section">
      <h2 class="section-title">管理员信息</h2>
      <div class="info-card">
        <div class="info-row">
          <span class="info-label">管理员ID：</span>
          <span class="info-value">{{ admin.user_id }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">用户名：</span>
          <span class="info-value">{{ admin.username }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">邮箱：</span>
          <span class="info-value">{{ admin.email }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">角色：</span>
          <span class="info-value admin-role">{{ admin.role }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios';
import { API_BASE_URL } from '@/config';
import { User, Folder, Document, ChatDotRound } from '@element-plus/icons-vue';

export default {
  name: 'AdminWelcome',
  components: { User, Folder, Document, ChatDotRound },
  data() {
    return {
      admin: {},
      stats: {
        users: 0,
        files: 0,
        generations: 0,
        feedback: 0
      },
      currentTime: '',
      loading: false,
      error: null
    }
  },
  created() {
    this.loadAdminData()
    this.updateCurrentTime()
    this.loadSystemStats()
    
    // 每10分钟更新一次统计数据
    setInterval(this.loadSystemStats, 10 * 60 * 1000)
    
    // 更新时间
    setInterval(this.updateCurrentTime, 1000)
  },
  methods: {
    loadAdminData() {
      const adminUser = localStorage.getItem('adminUser')
      if (adminUser) {
        try {
          this.admin = JSON.parse(adminUser)
        } catch (e) {
          console.error('解析管理员信息失败:', e)
          this.handleLogout()
        }
      } else {
        this.handleLogout()
      }
    },
    
    handleLogout() {
      // 调用 App.vue 的登出方法
      if (typeof this.$parent.handleAdminLogout === 'function') {
        this.$parent.handleAdminLogout()
      }
    },
    
    updateCurrentTime() {
      const now = new Date()
      this.currentTime = now.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      })
    },
    
    async loadSystemStats() {
      this.loading = true
      this.error = null
      
      try {
        const response = await axios.get(`${API_BASE_URL}/api/admin/static`)
        
        if (response.data.success) {
          this.stats = response.data.data
        } else {
          throw new Error(response.data.message || '获取数据失败')
        }
      } catch (error) {
        console.error('获取系统统计失败:', error)
        this.error = error.message || '网络请求失败'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.admin-welcome {
  min-height: 0;
  background: var(--bg-soft);
  padding: 0;
}

/* 欢迎横幅 */
.welcome-banner {
  background: var(--brand-gradient);
  color: white;
  padding: 40px 20px;
  margin-bottom: 30px;
}

.banner-content {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-title {
  font-size: var(--text-4xl);
  margin-bottom: 10px;
  font-weight: 600;
  color: var(--on-brand);
}

.welcome-subtitle {
  font-size: var(--text-lg);
  opacity: 0.9;
  margin-bottom: 10px;
}

.current-time {
  font-size: var(--text-base);
  opacity: 0.8;
  margin-bottom: 30px;
}

.loading-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: white;
  animation: spin 1s ease-in-out infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.admin-stats {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.stat-card {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  min-width: 200px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: transform 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-icon {
  font-size: 30px;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.stat-label {
  font-size: var(--text-base);
  opacity: 0.9;
  margin-top: 5px;
}

/* 管理员信息 */
.admin-info-section {
  max-width: 1200px;
  margin: 0 auto 30px;
  padding: 0 20px;
}

.section-title {
  font-size: var(--text-2xl);
  color: var(--text-strong);
  margin-bottom: 20px;
  font-weight: 600;
}

.info-card {
  background: var(--bg-surface);
  border-radius: 12px;
  padding: 25px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-xs);
}

.info-row {
  display: flex;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--border);
}

.info-row:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.info-label {
  font-weight: 600;
  color: var(--text-strong);
  min-width: 100px;
}

.info-value {
  color: var(--text-muted);
}

.admin-role {
  background: var(--brand-gradient);
  color: white;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: var(--text-xs);
  font-weight: 600;
  display: inline-block;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-stats {
    flex-direction: column;
  }
  
  .stat-card {
    width: 100%;
    min-width: auto;
  }
  
  .info-row {
    flex-direction: column;
  }
  
  .info-label {
    min-width: auto;
    margin-bottom: 5px;
  }
}
</style>