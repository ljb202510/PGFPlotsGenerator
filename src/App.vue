<!-- src/App.vue -->
<template>
  <!-- 全局消息默认：5 秒自动消失 + 可点击关闭按钮 -->
  <el-config-provider :message="messageConfig">
  <div id="app">
    <!-- 根据认证状态显示不同的顶部导航栏 -->
    <!-- 普通用户登录后显示普通导航栏 -->
    <CommonNavbar 
      v-if="isAuthenticated && !isAdminAuthenticated"
      :show-navbar="isAuthenticated"
      :user="currentUser"
      :unread-count="unreadCount"
      @logout="handleLogout"
      @user-click="goToChangeInformation"
    />
    
    <!-- 管理员登录后显示管理员导航栏 -->
    <AdminNavbar 
      v-else-if="isAdminAuthenticated"
      :admin="currentAdmin"
      @logout="handleAdminLogout"
    />

    <!-- 主要内容 -->
    <main>
      <!-- 未登录显示认证页面 -->
      <Auth 
        v-if="!isAuthenticated && !isAdminAuthenticated" 
        @auth-success="handleAuthSuccess"
        @admin-success="handleAdminSuccess"
      />
      
      <!-- 普通用户登录后显示主应用 -->
      <div v-else-if="isAuthenticated" class="main-app">
        <div class="app-layout">
          <!-- 主内容区域 -->
          <div class="content-area" :class="{ 'is-chat': $route.path === '/chart-generator' }">
            <router-view></router-view>
          </div>
        </div>
      </div>
      
      <!-- 管理员登录后显示路由视图容器 -->
      <div v-else-if="isAdminAuthenticated" class="admin-main">
        <div class="admin-layout">
          <!-- 移动端抽屉遮罩 -->
          <div v-if="adminMobileDrawerOpen" class="drawer-overlay" @click="adminMobileDrawerOpen = false"></div>
          <!-- 移动端汉堡按钮 -->
          <button class="drawer-toggle" @click="adminMobileDrawerOpen = !adminMobileDrawerOpen" aria-label="打开菜单">
            <el-icon><Menu /></el-icon>
          </button>

          <!-- 管理员侧边栏组件 -->
          <AdminSidebar 
            :collapsed="isAdminSidebarCollapsed"
            :mobile-open="adminMobileDrawerOpen"
            @toggle="handleAdminSidebarToggle"
            @navigate="adminMobileDrawerOpen = false"
            @close="adminMobileDrawerOpen = false"
          />

          <!-- 主内容区域 -->
          <div class="admin-content-area">
            <router-view></router-view>
          </div>
        </div>
      </div>
    </main>
  </div>
  </el-config-provider>
</template>

<script>
import Auth from './components/TheAuth.vue';
import CommonNavbar from './components/CommonNavbar.vue';
import AdminNavbar from './components/AdminNavbar.vue';
import AdminSidebar from './components/AdminSidebar.vue';
import { Menu } from '@element-plus/icons-vue';
import { API_BASE_URL } from '@/config';

export default {
  name: 'App',
  components: {
    Auth,
    CommonNavbar,
    AdminNavbar,
    AdminSidebar,
    Menu
  },
  data() {
    return {
      // 全局 ElMessage 默认：5 秒自动消失 + 显示可点击关闭按钮
      messageConfig: { duration: 5000, showClose: true },
      isAdminSidebarCollapsed: false,
      isAdminAuthenticated: false,
      currentAdmin: null,
      adminMobileDrawerOpen: false
    }
  },
  computed: {
    currentUser() {
      return this.$store.state.currentUser
    },
    isAuthenticated() {
      return this.$store.state.isAuthenticated
    },
    unreadCount() {
      return this.$store.state.unreadCount
    }
  },
  watch: {
    '$route'(to) {
      if (to.path === '/MyNotice') {
        this.fetchUnreadCount()
      }
      // 路由切换时收起移动端抽屉
      this.adminMobileDrawerOpen = false
    }
  },
  mounted() {
    this.checkAuthStatus()
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
  },

  methods: {
    checkAuthStatus() {
      // 检查普通用户认证
      const token = localStorage.getItem('token') || sessionStorage.getItem('token')
      const user = localStorage.getItem('user') || sessionStorage.getItem('user')
      
      if (token && user) {
        this.$store.commit('SET_USER', JSON.parse(user))
        this.validateToken(token)
        this.fetchUnreadCount()
      }
      
      // 检查管理员认证
      const adminToken = localStorage.getItem('adminToken')
      const adminUser = localStorage.getItem('adminUser')
      
      if (adminToken && adminUser) {
        try {
          this.currentAdmin = JSON.parse(adminUser)
          this.isAdminAuthenticated = true
          console.log('管理员已认证，当前路径:', window.location.pathname)
        } catch (e) {
          console.error('解析管理员信息失败:', e)
          this.handleAdminLogout()
        }
      }
    },
    
    async validateToken(token) {
      try {
        const response = await fetch(`${API_BASE_URL}/api/auth/validate`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        })
        
        if (!response.ok) {
          this.handleLogout()
        }
      } catch (error) {
        console.error('Token验证失败:', error)
        this.handleLogout()
      }
    },
    
    handleAuthSuccess(user) {
      this.$store.commit('SET_USER', user)
      this.fetchUnreadCount()
    },
    
    handleAdminSuccess(adminData) {
      console.log('管理员登录成功:', adminData)
      this.currentAdmin = adminData
      this.isAdminAuthenticated = true
      
      // 存储管理员信息到localStorage
      localStorage.setItem('adminUser', JSON.stringify(adminData))
      localStorage.setItem('adminToken', adminData.token)
      
      // 管理员登录后跳转到Admin.vue界面
      this.$router.push('/admin')
    },
    
    handleLogout() {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      
      this.$store.commit('LOGOUT')
      // 登出后跳转到首页
      this.$router.push('/')
    },
    
    handleAdminLogout() {
      localStorage.removeItem('adminToken')
      localStorage.removeItem('adminUser')
      this.isAdminAuthenticated = false
      this.currentAdmin = null
      
      // 管理员登出后跳转到首页
      this.$router.push('/')
    },
    
    handleResize() {
      // 回到桌面尺寸时自动收起抽屉
      if (window.innerWidth > 768) {
        this.adminMobileDrawerOpen = false
      }
    },
    
    handleAdminSidebarToggle(isCollapsed) {
      this.isAdminSidebarCollapsed = isCollapsed;
    },

    goToChangeInformation() {
      this.$router.push('/change-information');
    },

    fetchUnreadCount() {
      this.$store.dispatch('fetchUnreadCount')
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: var(--font-sans);
  background: var(--bg-soft);
}

#app {
  min-height: 100vh;
}

.main-app {
  padding: var(--space-5);
  max-width: 100%;
  margin: 0 auto;
  height: calc(100vh - 60px);
  overflow: hidden;
}

.admin-main {
  height: calc(100vh - 60px); /* 锁死外壳高度，减去导航栏高度 */
  background: var(--bg-soft);
  padding: var(--space-5);
  overflow: hidden; /* 窗口不再整页滚动 */
}

.app-layout {
  display: flex;
  height: 100%;
  min-height: 0;
  gap: var(--space-5);
}

.admin-layout {
  display: flex;
  height: 100%; /* 填满外壳内容区，不撑高 */
  min-height: 0; /* 允许子项收缩到父高，从而内容区可内部滚动 */
  gap: var(--space-5);
  overflow: hidden;
}

.content-area {
  flex: 1;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-base);
  padding: var(--space-5);
  overflow: auto;
}

/* 图表生成页：铺满内容区，整页不滚，由内部叶子容器滚动 */
.content-area.is-chat {
  padding: 0;
  overflow: hidden;
  height: 100%;
}

.admin-content-area {
  flex: 1;
  background: var(--bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-base);
  padding: var(--space-5);
  min-height: 0; /* 关键：使 flex 子项可收缩到父高并内部滚动 */
  overflow-y: auto;
}

@media (max-width: 768px) {
  .app-layout,
  .admin-layout {
    flex-direction: column;
  }
  
  .content-area,
  .admin-content-area {
    padding: var(--space-4);
  }
}

/* 移动端汉堡按钮与抽屉遮罩 */
.drawer-toggle {
  display: none;
}

.drawer-overlay {
  display: none;
}

@media (max-width: 768px) {
  .drawer-toggle {
    display: inline-flex;
    position: fixed;
    top: 14px;
    left: 14px;
    z-index: 1100;
    width: 40px;
    height: 40px;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    color: var(--brand);
    background: var(--bg-surface);
    border: 1px solid var(--border);
    border-radius: 10px;
    box-shadow: var(--shadow-card);
    cursor: pointer;
  }

  .drawer-toggle:hover {
    border-color: var(--brand);
    background: var(--brand-soft);
  }

  .drawer-overlay {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 999;
    background: rgba(16, 24, 40, 0.45);
  }
}
</style>