// import Vue from 'vue'
// import Router from 'vue-router'
// Vue.use(Router)
//这里使用的VUE2版本不能适用当前VUE3版本
import { createRouter, createWebHistory } from 'vue-router'

const routerHistory = createWebHistory()

//配置路由组件, 确保组件名与路由名称不同
const router = createRouter({
  history: routerHistory,
  routes: [
    {
      path: '/',
      // 修改这里：使用函数进行动态重定向
      redirect: () => {
        // 1. 优先检查是否有管理员登录信息 (根据 AdminLogin.vue 的逻辑)
        const adminUser = localStorage.getItem('adminUser');
        if (adminUser) {
          // 如果是管理员，强制去管理员首页 (请确保 '/admin' 是你的管理员主路由 path)
          return '/admin'; 
        }
        
        // 2. 如果没有管理员信息，才跳转到默认的图表生成页
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


export default router
