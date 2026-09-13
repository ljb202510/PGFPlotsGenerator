// main.js

// 解决 ResizeObserver loop completed with undelivered notifications 报错
const debounce = (fn, delay) => {
  let timer = null;
  return function () {
    let context = this;
    let args = arguments;
    clearTimeout(timer);
    timer = setTimeout(function () {
      fn.apply(context, args);
    }, delay);
  }
}

// 核心逻辑：重写 ResizeObserver，加入防抖
// 注意：防抖会把回调推迟到 16ms 后，若组件在此期间已卸载（典型场景：Element Plus 的 el-select
// 被销毁），element-plus 会在回调里对已失效的 DOM 引用调用 getComputedStyle，抛出
// 「parameter 1 is not of type 'Element'」并冒泡成开发期红屏。
// 因此这里先过滤掉已脱离文档的观察目标，并对这类"卸载后回调"做兜底，避免弹错。
const _ResizeObserver = window.ResizeObserver;
window.ResizeObserver = class ResizeObserver extends _ResizeObserver {
  constructor(callback) {
    const guardedCallback = (entries, observer) => {
      const alive = (entries || []).filter((entry) => entry && entry.target && entry.target.isConnected);
      if (!alive.length) {
        return;
      }
      try {
        callback(alive, observer);
      } catch (error) {
        // 组件已卸载等场景下的良性异常：不冒泡到 Vue 错误处理，避免开发期红屏
        console.debug('[ResizeObserver] 已忽略回调异常:', error && error.message);
      }
    };
    super(debounce(guardedCallback, 16));
  }
}

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store' // 确保路径正确
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/theme.css'

import AppCard from './components/ui/AppCard.vue'
import AppButton from './components/ui/AppButton.vue'
import EmptyState from './components/ui/EmptyState.vue'
import AppSpinner from './components/ui/AppSpinner.vue'
import PageHeader from './components/ui/PageHeader.vue'
import StatCard from './components/ui/StatCard.vue'

const app = createApp(App)
app.use(router)
app.use(store)
app.use(ElementPlus)
app.component('AppCard', AppCard)
app.component('AppButton', AppButton)
app.component('EmptyState', EmptyState)
app.component('AppSpinner', AppSpinner)
app.component('PageHeader', PageHeader)
app.component('StatCard', StatCard)
app.mount('#app')
