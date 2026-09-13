// import Vue from 'vue'
// import Router from 'vue-router'
// Vue.use(Router)
//这里使用的VUE2版本不能适用当前VUE3版本
import { createRouter, createWebHistory } from 'vue-router'
import { getAdminToken, getUserToken, clearAdminSession } from '@/utils/auth'

const routerHistory = createWebHistory()

//配置路由组件, 确保组件名与路由名称不同
const router = createRouter({
  history: routerHistory,
  routes: [
    {
      path: '/',
      // 修改这里：使用函数进行动态重定向
      redirect: () => {
        // 已有用户会话 → 用户主流程；仅登录了管理员 → 管理端；都无 → 图表页（由 App.vue 渲染登录页）
        if (!getUserToken() && getAdminToken()) {
          return '/admin';
        }
        return '/chart-generator';
      }
    },
    {
      path: '/admin',
      name: 'Admin',
      component: () => import('../Admin.vue'),
      meta: { requiresAdmin: true } // 添加路由元信息
    },
    {
      path: '/admin/feedback',
      name: 'AdminFeedback',
      component: () => import('../views/AdminFeedback.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/log',
      name: 'AdminLog',
      component: () => import('../views/AdminLog.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/notice',
      name: 'AdminNotice',
      component: () => import('../views/AdminNotice.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/admin/user',
      name: 'AdminUser',
      component: () => import('../views/AdminUser.vue'),
      meta: { requiresAdmin: true }
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/components/LoginForm.vue')
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../components/RegisterForm.vue')
    },
    {
      path: '/MyNotice',
      name: 'MyNotice',
      component: () => import('@/views/MyNotice.vue')
    },
    {
      path: '/change-information',
      name: 'ChangeInformation',
      component: () => import('../views/ChangeInformation.vue')
    },
    {
      path: '/chart-generator',
      name: 'ChartGenerator',
      component: () => import('../views/ChartGenerator.vue')
    },
    {
      path: '/history',
      name: 'MyHistory',
      component: () => import('../views/MyHistory.vue')
    },
    {
      path: '/data-upload',
      name: 'DataUpload',
      component: () => import('../views/DataUpload.vue')
    },
    
    {
      path: '/feedback',
      name: 'MyFeedback',
      component: () => import('../views/MyFeedback.vue')
    },
  ]
})

// 管理员路由守卫：/admin/** 需要有效管理员会话，缺失则清理残留并回到入口
router.beforeEach((to) => {
  if (to.meta && to.meta.requiresAdmin && !getAdminToken()) {
    clearAdminSession()
    return { path: '/' }
  }
  return true
})

export default router
