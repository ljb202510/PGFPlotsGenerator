import axios from 'axios'
import { API_BASE_URL } from '@/config'

/**
 * [G1] 提交编译任务并轮询至终态（1.5s 间隔，90s 上限 = 30s 编译超时 + 排队余量）。
 * 两处视图（ChartGenerator / MyHistory）共用，替代原先同步阻塞的 POST。
 * @param {number|string} historyId 历史记录 ID
 * @param {string} token 用户 JWT
 * @returns {Promise<{pdf_path: string, file_size: number, duration_ms: number}>} 终态任务体
 * @throws {Error} message 为可展示的后端错误文案或超时提示
 */
export async function submitAndPollCompile(historyId, token) {
  // 提交阶段失败（400/404/503）保留 error.response，交给调用方按状态码分支处理
  const submit = await axios.post(`${API_BASE_URL}/api/compile/${historyId}`, {}, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: 15000
  })
  const taskId = submit.data && submit.data.data && submit.data.data.task_id
  if (!taskId) {
    throw new Error('编译任务提交失败：未返回 task_id')
  }

  const deadline = Date.now() + 90 * 1000
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 1500))
    let res
    try {
      res = await axios.get(`${API_BASE_URL}/api/compile/task/${taskId}`, {
        headers: { Authorization: `Bearer ${token}` },
        timeout: 15000
      })
    } catch (e) {
      // 查询阶段失败统一转成纯文案 Error，避免调用方把「任务已丢失」误判成「历史记录不存在」
      const message = (e.response && e.response.data && e.response.data.message) || e.message || '编译任务查询失败'
      throw new Error(message)
    }
    const task = res.data && res.data.data
    if (!task) {
      throw new Error('编译任务查询失败')
    }
    if (task.status === 'success') {
      return task
    }
    if (task.status === 'failed') {
      throw new Error(task.error || '编译失败')
    }
    // queued / running → 继续轮询
  }
  throw new Error('编译任务超时（90 秒），请稍后在历史记录中重试')
}
