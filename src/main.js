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
const _ResizeObserver = window.ResizeObserver;
window.ResizeObserver = class ResizeObserver extends _ResizeObserver {
  constructor(callback) {
    callback = debounce(callback, 16);
    super(callback);
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
