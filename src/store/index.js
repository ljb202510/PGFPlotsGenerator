// store/index.js
//状态管理模式 + 库，相当于应用程序的“前端数据库”，所有需要跨组件共享的数据（即状态）都放在这里统一管理。
// import Vue from 'vue'
import Vuex from 'vuex'
import { API_BASE_URL } from '@/config'
// 直接创建 store 实例，不使用 Vue.use()
const store = new Vuex.Store({
  state: {
    currentUser: null,
    isAuthenticated: false,
    unreadCount: 0
  },
  mutations: {
    SET_USER(state, user) {
      state.currentUser = user
      state.isAuthenticated = !!user
      
      if (user) {
        const storage = localStorage.getItem('token') ? localStorage : sessionStorage
        storage.setItem('user', JSON.stringify(user))
      }
    },
    UPDATE_USERNAME(state, newUsername) {
      if (state.currentUser) {
        //更新内存中状态
        state.currentUser.username = newUsername
        //更新本地存储
        const storage = localStorage.getItem('token') ? localStorage : sessionStorage
        const storedUser = storage.getItem('user')
        if (storedUser) {
          const userObj = JSON.parse(storedUser)
          userObj.username = newUsername
          storage.setItem('user', JSON.stringify(userObj))
        }
      }
    },
    SET_UNREAD_COUNT(state, count) {
      state.unreadCount = count
    },
    LOGOUT(state) {
      state.currentUser = null
      state.isAuthenticated = false
      state.unreadCount = 0
    }
  },
  actions: {
    updateUsername({ commit }, newUsername) {
      commit('UPDATE_USERNAME', newUsername)
    },
    setUser({ commit }, user) {
      commit('SET_USER', user)
    },
    logout({ commit }) {
      commit('LOGOUT')
    },
    async fetchUnreadCount({ commit }) {
      const token = localStorage.getItem('token') || sessionStorage.getItem('token')
      if (!token) return
      try {
        const response = await fetch(`${API_BASE_URL}/api/notice/unread-count`, {
          headers: { 'Authorization': `Bearer ${token}` }
        })
        const result = await response.json()
        if (result.success) {
          commit('SET_UNREAD_COUNT', result.data.unreadCount)
        }
      } catch (error) {
        console.error('获取未读数量失败:', error)
      }
    }
  }
})

export default store