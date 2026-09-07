import { API_BASE_URL } from '@/config';

// 通过鉴权接口拉取 PDF Blob 并生成 objectURL。
// 背景：iframe/object 无法携带 Authorization 请求头，而把 JWT 拼进 URL 会泄露到日志，
// 因此改为 fetch + Bearer → blob → createObjectURL 供 <object :data> 预览。
export async function fetchPdfBlobUrl(historyId, token) {
  const response = await fetch(`${API_BASE_URL}/api/compile/${historyId}/pdf`, {
    headers: { 'Authorization': `Bearer ${token}` }
  })

  if (!response.ok) {
    let message = '获取PDF失败'
    try {
      const data = await response.json()
      message = data.message || message
    } catch (e) {
      // 非 JSON 响应时使用默认提示
    }
    const err = new Error(message)
    err.status = response.status
    throw err
  }

  const blob = await response.blob()
  return URL.createObjectURL(blob)
}
