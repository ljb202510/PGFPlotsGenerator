// 获取管理员 token（本地存储，去除可能包裹的引号）
export function getAdminToken() {
  const token = localStorage.getItem('adminToken')
  if (token && token !== 'null' && token !== 'undefined') {
    return token.replace(/^["']|["']$/g, '').trim()
  }
  return null
}

// 生成 Authorization 请求头
export function adminAuthHeader() {
  const token = getAdminToken()
  return token ? { 'Authorization': `Bearer ${token}` } : {}
}
