<!-- MyHistory.vue -->
<template>
  <div class="history-container">
    <div class="history-header">
      <h2><el-icon><Document /></el-icon> 图表生成历史</h2>
      <p class="subtitle">查看和管理您生成的图表记录</p>
    </div>

    <!-- 筛选和搜索 -->
    <div class="filter-section">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-input
            v-model="searchQuery"
            placeholder="搜索图表描述或文件名..."
            clearable
            @input="debouncedSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="6">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            @change="fetchHistory"
          />
        </el-col>
        <el-col :span="4" :offset="1">
          <el-button type="info" @click="resetFilters">重置筛选</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 历史记录表格 -->
    <div class="history-table">
      <el-table
        :data="filteredHistory"
        v-loading="loading"
        style="width: 100%"
        @sort-change="handleSortChange"
      >
        <el-table-column label="描述/文件" min-width="200">
          <template #default="{ row }">
            <div class="description-cell">
              <div class="description-text" :title="row.description">
                {{ row.description || '无描述' }}
              </div>
              <div v-if="row.file_name" class="file-info">
                <el-icon size="12"><Document /></el-icon>
                <span>{{ row.file_name }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="代码片段" width="150">
          <template #default="{ row }">
            <el-tooltip content="点击查看完整代码" placement="top">
              <el-button 
                size="small" 
                @click="showCodePreview(row.chart_code)"
                :disabled="!row.chart_code"
              >
                查看代码 
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="生成的PDF" width="150">
          <template #default="{ row }">
            <div class="pdf-cell">
              <el-button 
                v-if="row.generation_path"
                size="small" 
                type="warning"
                @click="previewExistingPdf(row.id)"
                :loading="previewingPdfId === row.id"
              >
                <el-icon><DataLine /></el-icon> 预览PDF
              </el-button>
              <el-tooltip 
                v-else 
                content="该记录尚未生成PDF，点击生成" 
                placement="top"
              >
                <el-button 
                  size="small" 
                  type="info" 
                  plain
                  @click="compileToPDF(row.id)"
                >
                  生成PDF
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="created_at" label="生成时间" width="180" sortable>
          <template #default="{ row }">
            <div class="timestamp">
              <div>{{ formatDate(row.created_at) }}</div>
              <div class="time-ago">{{ timeAgo(row.created_at) }}</div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                size="small"
                type="primary"
                @click="copyCode(row.chart_code)"
                :disabled="!row.chart_code"
              >
                复制代码
              </el-button>
              <el-button
                size="small"
                type="success"
                @click="compileToPDF(row.id)"
                :loading="compilingId === row.id"
                :disabled="!row.chart_code"
              >
                生成PDF
              </el-button>
              <el-dropdown @command="(command) => handleMoreAction(command, row)">
                <el-button size="small" type="info">
                  更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="view">查看详情</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除记录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-section">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="totalItems"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 代码预览对话框 -->
    <el-dialog
      v-model="codeDialogVisible"
      title="图表代码预览"
      width="80%"
    >
      <div class="code-dialog-content">
        <div class="code-info">
          <el-tag type="info">LaTeX PGFPlots 代码</el-tag>
          <span class="code-length">代码长度: {{ currentCode.length }} 字符</span>
        </div>
        <CodeBlock :code="currentCode" :max-height="'60vh'" />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="copyCode(currentCode)">
            复制代码
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="历史记录详情"
      width="70%"
    >
      <div v-if="currentDetail" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录ID">
            {{ currentDetail.id }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            {{ currentDetail.description || '无描述' }}
          </el-descriptions-item>
          <el-descriptions-item label="使用的文件">
            {{ currentDetail.file_name || '未使用文件' }}
          </el-descriptions-item>
          <el-descriptions-item label="生成时间">
            {{ formatDateTime(currentDetail.created_at) }}
          </el-descriptions-item>
          <el-descriptions-item label="用户输入" :span="2">
            <div class="user-input">
              {{ currentDetail.user_input || '无' }}
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="AI回复" :span="2">
            <div class="ai-response">
              {{ currentDetail.ai_response || '无' }}
            </div>
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="currentDetail.chart_code" class="code-section">
          <h4>生成的代码:</h4>
          <CodeBlock :code="currentDetail.chart_code" :max-height="'300px'" />
        </div>
      </div>
    </el-dialog>

    <!-- 新增PDF预览对话框 -->
    <el-dialog
      v-model="pdfDialogVisible"
      title="PDF预览"
      width="80%"
      @close="cleanupPdfUrl"
    >
      <div class="pdf-preview-container" v-if="currentPdfUrl">
        <div class="pdf-viewer">   
          <!-- 使用object标签显示pdf -->
          <object
            :data="currentPdfUrl"
            type="application/pdf"
            width="100%"
            height="600px"
          >
            <!-- <p>您的浏览器不支持PDF预览，请<a :href="currentPdfUrl" download>点击下载</a></p> -->
          </object>
        </div>
      </div>
    </el-dialog>

    <!-- 空状态 -->
    <div v-if="!loading && filteredHistory.length === 0" class="empty-state">
      <el-empty description="暂无历史记录">
        <template #image>
          <el-icon size="60"><Histogram /></el-icon>
        </template>
        <p>您还没有生成任何图表记录</p>
        <el-button type="primary" @click="$router.push('/chart-generator')">
          去生成图表
        </el-button>
      </el-empty>
    </div>
  </div>
  <el-dialog v-model="compileErrorDialogVisible" :title="compileErrorTitle" width="70%">
    <div style="max-height: 60vh; overflow: auto; background: #111; color: var(--border); padding: 12px; border-radius: 8px;">
      <pre style="white-space: pre-wrap; word-break: break-word; margin: 0;">{{ compileErrorDetails }}</pre>
    </div>
    <template #footer>
      <el-button @click="compileErrorDialogVisible = false">关闭</el-button>
      <el-button type="primary" @click="copyCode(compileErrorDetails)">复制日志</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import debounce from 'lodash/debounce'
import {
  Search,
  Document,
  ArrowDown,
  Histogram,
  DataLine
} from '@element-plus/icons-vue'
import CodeBlock from '@/components/ui/CodeBlock.vue'

// API配置
import { API_BASE_URL } from '@/config';
const previewingPdfId = ref(null)

const compileErrorDialogVisible = ref(false)
const compileErrorTitle = ref('PDF生成失败')
const compileErrorDetails = ref('')

// 获取token
const getAuthToken = () => {
  const possibleTokens = [
    localStorage.getItem('token'),
    sessionStorage.getItem('token'),
  ];
  
  for (let token of possibleTokens) {
    if (token && token !== 'null' && token !== 'undefined') {
      return token.replace(/^["']|["']$/g, '').trim();
    }
  }
  return null;
}

// 响应式数据
const loading = ref(false)
const historyData = ref([])
const searchQuery = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)
const codeDialogVisible = ref(false)
const currentCode = ref('')
const detailDialogVisible = ref(false)
const currentDetail = ref(null)
const compilingId = ref(null)
const sortField = ref('created_at')
const sortOrder = ref('descending')

// 新增PDF相关变量
const pdfDialogVisible = ref(false)
const currentPdfUrl = ref('')
const currentPdfName = ref('')

// 防抖搜索
const debouncedSearch = debounce(() => {
  currentPage.value = 1
  fetchHistory()
}, 500)

// 获取历史记录
const fetchHistory = async () => {
  loading.value = true
  try {
    const token = getAuthToken()
    if (!token) {
      ElMessage.error('请先登录')
      return
    }

    const params = {
      page: currentPage.value,
      limit: pageSize.value,
      search: searchQuery.value,
      sort_by: sortField.value,
      sort_order: sortOrder.value === 'descending' ? 'desc' : 'asc',
      source: 'all'
    }

    if (dateRange.value && dateRange.value.length === 2) {
      params.start_date = dateRange.value[0]
      params.end_date = dateRange.value[1]
    }

    const response = await axios.get(`${API_BASE_URL}/api/history`, {
      headers: { 'Authorization': `Bearer ${token}` },
      params
    })

    historyData.value = response.data.data.records || []
    totalItems.value = response.data.data.total || 0
  } catch (error) {
    console.error('获取历史记录失败:', error)
    ElMessage.error('获取历史记录失败: ' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

// 过滤后的历史记录
const filteredHistory = computed(() => {
  return historyData.value.map(item => ({
    ...item,
    code_length: item.chart_code ? item.chart_code.length : 0
  }))
})

// 日期格式化
const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDateTime = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 相对时间
const timeAgo = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now - date
  const diffSec = Math.floor(diffMs / 1000)
  const diffMin = Math.floor(diffSec / 60)
  const diffHour = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHour / 24)

  if (diffDay > 0) return `${diffDay}天前`
  if (diffHour > 0) return `${diffHour}小时前`
  if (diffMin > 0) return `${diffMin}分钟前`
  return '刚刚'
}

// 显示代码预览
const showCodePreview = (code) => {
  currentCode.value = code || ''
  codeDialogVisible.value = true
}

// 复制代码
const copyCode = (code) => {
  if (!code) {
    ElMessage.warning('没有可复制的代码')
    return
  }
  
  // 使用兼容性更好的复制方法
  copyToClipboard(code)
    .then(() => {
      ElMessage.success('代码已复制到剪贴板')
    })
    .catch(err => {
      console.error('复制失败:', err)
      ElMessage.error('复制失败，请手动选择文本复制')
    })
}

// 添加兼容性复制函数
const copyToClipboard = (text) => {
  return new Promise((resolve, reject) => {
    // 方法1: 使用现代 Clipboard API
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text)
        .then(resolve)
        .catch(reject)
      return
    }
    
    // 方法2: 使用传统的execCommand作为降级方案
    const textArea = document.createElement('textarea')
    textArea.value = text
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()
    
    try {
      const successful = document.execCommand('copy')
      document.body.removeChild(textArea)
      if (successful) {
        resolve()
      } else {
        reject(new Error('复制命令执行失败'))
      }
    } catch (err) {
      document.body.removeChild(textArea)
      reject(err)
    }
  })
}

// 编译为PDF - 修改后的函数
const compileToPDF = async (historyId) => {
  if (!historyId) {
    ElMessage.warning('该记录没有关联的历史记录ID')
    return
  }
  
  compilingId.value = historyId
  
  try {
    const token = getAuthToken()
    if (!token) {
      ElMessage.error('请先登录')
      return
    }
    
    // 调用后端编译接口
    const response = await axios.post(`${API_BASE_URL}/api/compile/${historyId}`, {}, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    // 从响应中获取PDF URL
    const pdfUrl = response.data.data.pdf_url
    
    // 显示成功消息
    ElMessage.success('PDF生成成功！')
    
    // 显示PDF预览
    await showPdfPreview(pdfUrl)
    
  } catch (error) {
    console.error('编译失败:', error)
    
    if (error.response?.status === 404) {
      ElMessage.error('历史记录不存在')
    } else if (error.response?.status === 400) {
      ElMessage.error('该历史记录没有可编译的图表代码')
    } else {
      const resp = error.response?.data
      const msg = resp?.message || error.message || 'PDF生成失败'

      // 详细信息放 Dialog
      const details = resp?.details || resp?.message || error.message || ''
      compileErrorTitle.value = msg
      compileErrorDetails.value = details
      compileErrorDialogVisible.value = true
    }
  } finally {
    compilingId.value = null
  }
}


// 预览已存在的PDF
const previewExistingPdf = async (historyId) => {
  if (!historyId) {
    ElMessage.warning('无效的历史记录ID');
    return;
  }
  
  previewingPdfId.value = historyId;
  
  try {
    const token = getAuthToken();
    if (!token) {
      ElMessage.error('请先登录');
      return;
    }
    
    // 先获取PDF URL
    const response = await axios.get(`${API_BASE_URL}/api/compile/${historyId}/pdf-url`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    if (response.data.success && response.data.data.exists) {
      const pdfUrl = response.data.data.pdf_url;
      await showPdfPreview(pdfUrl);
    } else {
      ElMessage.warning('PDF文件不存在，请先生成PDF');
    }
    
  } catch (error) {
    console.error('预览PDF失败:', error);
    
    if (error.response?.status === 404) {
      ElMessage.error('PDF文件不存在，请先生成PDF');
    } else {
      ElMessage.error('预览失败: ' + (error.response?.data?.message || error.message));
    }
  } finally {
    previewingPdfId.value = null;
  }
};

// 显示PDF预览，使用服务器返回的URL或Blob URL
const showPdfPreview = async (pdfUrl) => {
  try {
    let fullPdfUrl = pdfUrl
    if (!pdfUrl.startsWith('http')) {
      fullPdfUrl = `${API_BASE_URL}${pdfUrl}`
    }
    
    currentPdfUrl.value = fullPdfUrl
    pdfDialogVisible.value = true
    
  } catch (error) {
    console.error('显示PDF预览失败:', error)
  }
}

// 清理PDF URL
const cleanupPdfUrl = () => {
  if (currentPdfUrl.value && currentPdfUrl.value.startsWith('blob:')) {
    window.URL.revokeObjectURL(currentPdfUrl.value)
  }
  currentPdfUrl.value = ''
  currentPdfName.value = ''
}

// 更多操作
const handleMoreAction = (command, row) => {
  switch (command) {
    case 'view':
      currentDetail.value = row
      detailDialogVisible.value = true
      break
    case 'delete':
      deleteRecord(row.id)
      break
  }
}

// 删除记录
const deleteRecord = async (id) => {
  ElMessageBox.confirm(
    '确定要删除这条记录吗？此操作不可撤销。',
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'error'
    }
  ).then(async () => {
    try {
      const token = getAuthToken()
      if (!token) {
        ElMessage.error('请先登录')
        return
      }
      
      await axios.delete(`${API_BASE_URL}/api/history/${id}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      
      ElMessage.success('删除成功')
      fetchHistory()
    } catch (error) {
      console.error('删除失败:', error)
      ElMessage.error('删除失败: ' + (error.response?.data?.message || error.message))
    }
  }).catch(() => {
    // 用户取消
  })
}

// 排序处理
const handleSortChange = ({ prop, order }) => {
  sortField.value = prop
  sortOrder.value = order
  fetchHistory()
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  fetchHistory()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchHistory()
}

// 重置筛选
const resetFilters = () => {
  searchQuery.value = ''
  dateRange.value = []
  currentPage.value = 1
  sortField.value = 'created_at'
  sortOrder.value = 'descending'
  fetchHistory()
}

// 初始化
onMounted(() => {
  fetchHistory()
})
</script>

<!-- 样式部分保持不变 -->
<style scoped>
.pdf-cell {
  display: flex;
  justify-content:left;
}

.pdf-preview-container {
  width: 100%;
  height: 100%;
}

.pdf-viewer object {
  border: 1px solid var(--border);
  border-radius: 4px;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .pdf-cell .el-button {
    font-size: 12px;
    padding: 6px 8px;
  }
}
.history-container {
  padding: 20px;
  background-color: var(--bg-soft);
  min-height: calc(100vh - 60px);
}

.history-header {
  margin-bottom: 30px;
}

.history-header h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 24px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 14px;
}

.filter-section {
  margin-bottom: 20px;
  padding: 20px;
  background: var(--bg-surface);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.history-table {
  margin-bottom: 20px;
  background: var(--bg-surface);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.pagination-section {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: var(--bg-surface);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.description-cell {
  max-width: 300px;
}

.description-text {
  font-weight: 500;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  line-height: 1.5;
  margin-bottom: 4px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.timestamp {
  font-size: 14px;
}

.time-ago {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.action-buttons {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.code-dialog-content {
  padding: 10px 0;
}

.code-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.code-length {
  font-size: 14px;
  color: var(--text-muted);
}

.detail-content {
  max-height: 600px;
  overflow-y: auto;
  padding-right: 10px;
}

.user-input,
.ai-response {
  background: var(--bg-soft);
  padding: 10px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}

.code-section {
  margin-top: 20px;
}

.code-section h4 {
  margin: 0 0 10px 0;
  color: var(--text-strong);
}

.empty-state {
  padding: 60px 0;
  background: var(--bg-surface);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.empty-state p {
  margin-top: 10px;
  color: var(--text-muted);
}

@media (max-width: 992px) {
  .filter-section .el-col {
    margin-bottom: 10px;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .action-buttons .el-button {
    width: 100%;
    margin: 2px 0;
  }
}
</style>