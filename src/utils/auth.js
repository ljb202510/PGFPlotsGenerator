// 统一身份入口：用户会话与管理员会话分开读写，杜绝跨身份混用。
// 管理员 token 取值的唯一实现仍在 utils/adminToken.js，这里直接复用并转出，避免两份实现漂移。
import { getAdminToken, adminAuthHeader } from './adminToken'

export { getAdminToken, adminAuthHeader }

const isBlank = (v) => !v || v === 'null' || v === 'undefined'

/** 用户 token（local 优先，其次 session）；无有效值返回 null */
export function getUserToken() {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  return isBlank(token) ? null : token
}

/** 用户信息对象；无或解析失败返回 null */
export function getUser() {
  const raw = localStorage.getItem('user') || sessionStorage.getItem('user')
  if (isBlank(raw)) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

/** 写入用户会话：同一时刻只保留一份（记住我 → local，否则 → session） */
export function saveUserSession(user, token, remember) {
  const target = remember ? localStorage : sessionStorage
  const other = remember ? sessionStorage : localStorage
  other.removeItem('token')
  other.removeItem('user')
  target.setItem('token', token)
  target.setItem('user', JSON.stringify(user))
}

/** 更新已存储的用户信息（沿用当前生效的那份存储） */
export function updateStoredUser(user) {
  const storage = localStorage.getItem('token') ? localStorage : sessionStorage
  storage.setItem('user', JSON.stringify(user))
}

/** 清除用户会话（local 与 session 都清） */
export function clearUserSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('user')
}

/** 管理员信息对象；无或解析失败返回 null */
export function getAdmin() {
  const raw = localStorage.getItem('adminUser')
  if (isBlank(raw)) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

/** 写入管理员会话 */
export function saveAdminSession(admin, token) {
  localStorage.setItem('adminToken', token)
  localStorage.setItem('adminUser', JSON.stringify(admin))
}

/** 清除管理员会话 */
export function clearAdminSession() {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminUser')
}

/** 是否为管理员路由（/admin 及其子路径） */
export function isAdminRoute(path) {
  return typeof path === 'string' && (path === '/admin' || path.startsWith('/admin/'))
}
