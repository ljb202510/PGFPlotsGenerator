<template>
  <div class="ai-chat-container">
    <div class="chat-layout" :class="{ 'conv-collapsed': convCollapsed }">
      <!-- 对话列表面板（DeepSeek 风格） -->
      <div v-if="convDrawerOpen" class="conv-overlay" @click="convDrawerOpen = false"></div>
      <aside class="conversation-sidebar" :class="{ 'mobile-open': convDrawerOpen }">
        <div class="conv-sidebar__header">
          <span class="conv-sidebar__title">历史对话</span>
          <button class="conv-new-btn" @click="newChat">
            <el-icon><EditPen /></el-icon>
            <span>新建对话</span>
          </button>
        </div>
        <div class="conv-list">
          <div
            v-for="c in conversations"
            :key="c.conversation_id"
            :class="['conv-item', { active: c.conversation_id === currentConversationId }]"
            @click="selectConversation(c.conversation_id)"
          >
            <div class="conv-item__main">
              <div class="conv-item__title">{{ c.title }}</div>
              <div class="conv-item__meta">
                {{ formatConvTime(c.updated_at) }}<span v-if="c.message_count"> · {{ c.message_count }} 条</span>
              </div>
            </div>
            <div class="conv-item__actions">
              <el-icon class="conv-action" @click.stop="renameConversation(c)" title="重命名"><EditPen /></el-icon>
              <el-icon class="conv-action conv-action--danger" @click.stop="deleteConversation(c)" title="删除"><Delete /></el-icon>
            </div>
          </div>
          <div v-if="conversations.length === 0" class="conv-empty">
            还没有对话，点击「新建对话」开始
          </div>
        </div>
      </aside>

      <!-- 聊天区 -->
      <div class="chat-section">
        <div class="chat-header">
          <button class="conv-toggle" @click="toggleSidebar" aria-label="对话列表">
            <el-icon><Menu /></el-icon>
          </button>
          <h2><el-icon class="chat-title-icon"><ChatDotRound /></el-icon> AI图表助手</h2>
          <button class="new-chat-btn" @click="newChat">
            <el-icon><EditPen /></el-icon>
            新建对话
          </button>
        </div>
      
      <!-- 拖拽上传覆盖层 -->
      <div 
        v-if="dragOver" 
        class="drag-overlay"
        @dragover.prevent
        @dragleave="handleDragLeave"
        @drop="handleDrop"
      >
        <div class="drag-overlay-content">
          <div class="upload-icon-wrapper">
            <el-icon class="upload-icon"><upload-filled /></el-icon>
            <el-icon class="arrow-icon"><Top /></el-icon>
          </div>
          <div class="drag-text">文件拖拽到此处即可上传</div>
          <div class="drag-tip">最多可上传50个文件(每个100MB以内)，支持CSV、Excel等常用数据格式，自动使用文件名作为数据集名称</div>
        </div>
      </div>
      
      <!-- 聊天消息列表 -->
      <div 
        class="chat-messages" 
        ref="messagesContainer"
        @dragover.prevent="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
      >
        <div 
          v-for="(message, index) in messages" 
          :key="index"
          :class="['message-item', message.role, { error: message.isError }]"
        >
          <div class="message-content">
            <div class="message-info">
              <strong>{{ message.role === 'user' ? '我' : 'AI助手' }}</strong>
              <span class="message-time">{{ formatTime(message.timestamp) }}</span>
            </div>
            
            <!-- 用户消息中的文件显示 -->
            <div v-if="message.selectedFiles && message.selectedFiles.length > 0" class="message-files">
              <div class="files-list">
                <div 
                  v-for="file in message.selectedFiles"
                  :key="file.data_id"
                  class="file-item"
                  @click="selectFile(file)"
                >
                  <el-icon class="file-icon"><Document /></el-icon>
                  <span class="file-name">{{ file.data_name }}</span>
                  <span class="file-size">{{ formatFileSize(file.data_size) }}</span>
                </div>
              </div>
            </div>
            
            <!-- 消息内容 -->
            <div class="message-text" :class="{ 'error-text': message.isError }">
              {{ message.content }}
            </div>

            <!-- 用户消息底部操作区域 -->
            <div v-if="message.role === 'user'" class="user-message-actions">
              <el-tooltip content="复制消息" placement="bottom">
                <el-button 
                  size="small" 
                  text 
                  class="copy-message-btn"
                  @click="copyContent(message.content)"
                >
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
            
            <!-- 显示生成的图表代码 -->
            <div v-if="message.chartCode" class="chart-result">
              <div class="chart-preview">
                <div class="chart-preview__head">
                  <h4><el-icon><DataLine /></el-icon> 生成的PGFPlots图表代码：</h4>
                </div>

                <CodeBlock
                  :code="message.chartCode"
                  :max-height="'150px'"
                />

                <div class="chart-actions">
                  <el-button size="small" type="primary" @click="copyContent(message.chartCode)">
                    复制代码
                  </el-button>
                  <el-button
                    size="small"
                    type="success"
                    :loading="compiling"
                    @click="compileToPDF(message)"
                  >
                    生成PDF
                  </el-button>
                  <el-button size="small" @click="showFullCode(message.chartCode)">
                    查看完整代码
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 首屏引导区：用户尚未发送任何消息时显示（仅有欢迎语也算首屏） -->
      <div v-if="showTemplateGuide" class="guide-area">
        <div class="guide-hero">
          <span class="guide-hero__badge"><el-icon><MagicStick /></el-icon></span>
          <h2 class="guide-hero__title">用数据，一句话生成学术级图表</h2>
          <p class="guide-hero__subtitle">上传数据文件，选择下方模板或描述需求，AI 帮您生成 PGFPlots 代码并一键导出 PDF。</p>
        </div>

        <div class="guide-steps">
          <div class="guide-step">
            <span class="guide-step__icon"><el-icon><UploadFilled /></el-icon></span>
            <div class="guide-step__body">
              <div class="guide-step__title">上传 / 选择数据</div>
              <div class="guide-step__desc">支持 CSV、Excel，自动识别字段</div>
            </div>
          </div>
          <el-icon class="guide-step__arrow"><Right /></el-icon>
          <div class="guide-step">
            <span class="guide-step__icon"><el-icon><EditPen /></el-icon></span>
            <div class="guide-step__body">
              <div class="guide-step__title">选模板或描述需求</div>
              <div class="guide-step__desc">一句话说清想要的图表</div>
            </div>
          </div>
          <el-icon class="guide-step__arrow"><Right /></el-icon>
          <div class="guide-step">
            <span class="guide-step__icon"><el-icon><Download /></el-icon></span>
            <div class="guide-step__body">
              <div class="guide-step__title">一键生成 PDF</div>
              <div class="guide-step__desc">导出可发表的图表</div>
            </div>
          </div>
        </div>

        <h3 class="guide-templates__title">或从模板快速开始</h3>
        <div class="template-cards">
          <button
            v-for="t in templates"
            :key="t.key"
            class="template-card"
            type="button"
            @click="useTemplate(t)"
          >
            <span class="template-card__icon"><el-icon><component :is="t.icon" /></el-icon></span>
            <span class="template-card__title">{{ t.title }}</span>
            <span class="template-card__desc">{{ t.desc }}</span>
          </button>
        </div>
      </div>

      <!-- 底部区域 -->
      <div class="bottom-container">
        <!-- 已选择文件显示 -->
        <div v-if="selectedFiles.length > 0" class="selected-files">
          <div class="selected-files-header">
            <el-icon><Folder /></el-icon>
            <span>已选择文件 ({{ selectedFiles.length }}个)</span>
            <el-button 
              size="small" 
              type="text" 
              @click="clearAllSelectedFiles"
              class="clear-all-btn"
            >
              清除全部
            </el-button>
          </div>
          <div class="selected-files-list">
            <div 
              v-for="file in selectedFiles"
              :key="file.data_id"
              class="selected-file-item"
            >
              <el-icon class="file-icon"><Document /></el-icon>
              <span class="file-name">{{ file.data_name }}</span>
              <span class="file-size">{{ formatFileSize(file.data_size) }}</span>
              <el-icon class="remove-icon" @click="removeSelectedFile(file)"><Close /></el-icon>
            </div>
          </div>
        </div>
        
        <!-- 输入框区域 -->
        <div class="input-wrapper">
          <div class="input-container">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="描述您想要的图表，例如：'绘制折线图，展示各月销售额随时间的变化趋势'"
              @keydown="handleKeydown"
              class="message-input"
              resize="none"
              ref="inputRef"
            />
            <el-button 
              type="primary" 
              @click="sendMessage"
              :disabled="!inputMessage.trim()"
              class="send-btn"
            >
              <el-icon v-if="!loading"><Promotion /></el-icon>
              <el-icon v-else class="loading-icon"><Loading /></el-icon>
            </el-button>
          </div>
          
          <!-- 按钮区域 -->
          <div class="button-container">
            <el-button 
              @click="triggerFileUpload"
              class="upload-btn"
              size="small"
            >
              <el-icon><UploadFilled /></el-icon>
              点击上传
            </el-button>
            <el-button 
              @click="showFileHistory"
              class="history-btn"
              size="small"
            >
              <el-icon><Folder /></el-icon>
              上传历史文件
            </el-button>
            <el-button 
              @click="toggleModel"
              class="model-btn"
              size="small"
            >
              <el-icon><Switch /></el-icon>
              当前模型：{{ currentModel === 'deepseek' ? 'Deepseek-V4-Flash' : 'Qwen3.5' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
    </div>
    
    <!-- 文件历史对话框 -->
    <el-dialog
      v-model="fileHistoryVisible"
      title="历史文件"
      width="70%"
      class="file-history-dialog"
    >
      <div class="file-history-content">
        <div class="file-history-header">
          <div class="file-history-actions">
            <el-button 
              size="small" 
              @click="selectAllFiles"
              :disabled="fileList.length === 0"
            >
              全选
            </el-button>
            <el-button 
              size="small" 
              @click="clearAllSelectedFiles"
              :disabled="selectedFiles.length === 0"
            >
              全不选
            </el-button>
          </div>
        </div>
        
        <div v-loading="loadingFiles" class="file-history-list">
          <div 
            v-for="file in fileList" 
            :key="file.data_id"
            :class="['file-history-item', { active: isFileSelected(file) }]"
            @click="toggleSelectFile(file)"
          >
            <div class="file-info">
              <el-checkbox 
                :model-value="isFileSelected(file)"
                @click.stop="toggleSelectFile(file)"
                class="file-checkbox"
              />
              <el-icon class="file-icon"><Document /></el-icon>
              <!-- 文件名称单独占一列，右侧对齐大小和日期 -->
              <div class="file-details">
                <div class="file-name">{{ file.data_name }}</div>
              </div>
              <!-- 新增：文件大小和日期的右侧布局 -->
              <div class="file-meta-right">
                <span>{{ formatFileSize(file.data_size) }}</span>
                <span>{{ formatUploadTime(file.upload_time) }}</span>
              </div>
            </div>
            <!-- 选中标签改为左侧小标识，而非右侧标签 -->
            <div class="file-actions">
              <el-tag 
                v-if="isFileSelected(file)"
                type="success" 
                size="small"
              >
                已选中
              </el-tag>
            </div>
          </div>
          
          <div v-if="fileList.length === 0" class="empty-files">
            <el-empty description="暂无文件" />
          </div>
        </div>
      </div>
    </el-dialog>
    
    <!-- 代码查看对话框 -->
    <el-dialog
      v-model="codeDialogVisible"
      title="完整代码"
      width="80%"
    >
      <CodeBlock :code="currentCode" :show-copy="false" :max-height="'60vh'" />
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="copyContent(currentCode)">
            复制代码
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- PDF预览对话框 -->
    <el-dialog
      v-model="pdfDialogVisible"
      title="PDF预览"
      width="80%"
      @close="cleanupPdfUrl"
    >
      <div class="pdf-preview-container" v-if="currentPdfUrl">
        <div class="pdf-viewer">   
          <object
            :data="currentPdfUrl"
            type="application/pdf"
            width="100%"
            height="600px"
          >
            <p>您的浏览器不支持PDF预览，请<a :href="currentPdfUrl" download>点击下载</a></p>
          </object>
        </div>
      </div>
    </el-dialog>
    
    <!-- 隐藏的文件上传input -->
    <input 
      type="file" 
      ref="fileInputRef" 
      style="display: none" 
      @change="handleFileInputChange"
      multiple
    >
  </div>

</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Document,
  UploadFilled,
  Folder,
  Close,
  Promotion,
  Loading,
  Top,
  Switch,
  CopyDocument, // 新增复制图标
  EditPen,
  DataLine,
  Histogram,
  PieChart,
  DataAnalysis,
  ChatDotRound,
  Delete,
  Menu
} from '@element-plus/icons-vue'
import axios from 'axios'
import CodeBlock from '@/components/ui/CodeBlock.vue'

// API配置
import { API_BASE_URL } from '@/config';

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
const inputMessage = ref('')
const messages = ref([])
const loading = ref(false)
const loadingFiles = ref(false)
const fileList = ref([])
const selectedFiles = ref([])
const messagesContainer = ref(null)
const codeDialogVisible = ref(false)
const currentCode = ref('')
const compiling = ref(false)
const fileHistoryVisible = ref(false)
const dragOver = ref(false)
const fileInputRef = ref(null)

// 输入框引用（模板卡片点击后聚焦）
const inputRef = ref(null)

// 首屏模板引导：一键填充示例提示词
const templates = [
  { key: 'line', icon: DataLine, title: '折线图', desc: '展示数据随时间变化趋势', prompt: "请基于我上传的数据绘制折线图，展示数值随时间或类别变化的趋势；添加图表标题、X 轴与 Y 轴标签（含单位）、数据点标记与网格线，并加上图例。" },
  { key: 'bar', icon: Histogram, title: '柱状图', desc: '比较各类别数值大小', prompt: "请基于我上传的数据绘制柱状图，比较各类别的数值大小；添加图表标题、坐标轴标签（含单位）与数值标注，使用清晰的配色并加上图例。" },
  { key: 'pie', icon: PieChart, title: '饼图', desc: '展示各部分占比', prompt: "请基于我上传的数据绘制饼图，展示各部分所占比例；添加图表标题、各扇区的标签与百分比，并加上图例。" },
  { key: 'scatter', icon: DataAnalysis, title: '散点图', desc: '观察变量间相关性', prompt: "请基于我上传的数据绘制散点图，观察两个变量之间的相关性；添加图表标题、X 轴与 Y 轴标签（含单位），并可附一条趋势线。" }
]

// 首屏模板引导显示条件：用户尚未发送过任何消息时显示
// （挂载时总会插入一条 assistant 欢迎语，故不能用 messages.length === 0 判断）
const showTemplateGuide = computed(
  () => !messages.value.some((m) => m.role === 'user')
)

const useTemplate = (t) => {
  inputMessage.value = t.prompt
  nextTick(() => { inputRef.value?.focus() })
}

// 当前所选模型：qwen（默认）/ deepseek
const currentModel = ref('qwen')

// 切换模型
const toggleModel = () => {
  currentModel.value = currentModel.value === 'deepseek' ? 'qwen' : 'deepseek'
}

// PDF相关
const pdfDialogVisible = ref(false)
const currentPdfUrl = ref('')

// 对话（服务端持久化）相关状态
const conversations = ref([])
const currentConversationId = ref(null)
const convDrawerOpen = ref(false)
const convCollapsed = ref(localStorage.getItem('convCollapsed') === '1')

// 单一折叠入口：按视口分支到移动端抽屉或桌面端推挤折叠
const toggleSidebar = () => {
  if (window.innerWidth <= 768) {
    convDrawerOpen.value = !convDrawerOpen.value
  } else {
    convCollapsed.value = !convCollapsed.value
    localStorage.setItem('convCollapsed', convCollapsed.value ? '1' : '0')
  }
}

// 拖拽事件处理
const handleDragOver = (e) => {
  e.preventDefault()
  e.stopPropagation()
  if (!dragOver.value) {
    dragOver.value = true
  }
}

const handleDragLeave = (e) => {
  e.preventDefault()
  e.stopPropagation()
  // 只有当鼠标离开整个元素时才隐藏拖拽层
  if (!e.currentTarget.contains(e.relatedTarget)) {
    dragOver.value = false
  }
}

const handleDrop = async (e) => {
  e.preventDefault()
  e.stopPropagation()
  dragOver.value = false
  
  const files = Array.from(e.dataTransfer.files)
  if (files.length > 0) {
    for (const file of files) {
      await uploadDragFile(file)
    }
  }
}

// 文件大小格式化
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// 时间格式化
const formatTime = (date) => {
  return date.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

// 格式化上传时间
const formatUploadTime = (timeString) => {
  if (!timeString) return '';
  try {
    const date = new Date(timeString);
    return date.toLocaleDateString('zh-CN');
  } catch (e) {
    return timeString;
  }
}

// 检查文件是否被选中
const isFileSelected = (file) => {
  return selectedFiles.value.some(f => f.data_id === file.data_id);
}

// 触发文件上传
const triggerFileUpload = () => {
  fileInputRef.value.click()
}

// 处理文件输入变化
const handleFileInputChange = async (event) => {
  const files = Array.from(event.target.files)
  for (const file of files) {
    await uploadDragFile(file)
  }
  // 清空input值，允许重复选择相同文件
  event.target.value = ''
}

// 上传文件
const uploadDragFile = async (file) => {
  const token = getAuthToken()
  if (!token) {
    ElMessage.error('请先登录')
    return
  }
  
  // 验证文件大小
  const maxSize = 100 * 1024 * 1024 // 100MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }
  
  const formData = new FormData()
  const fileNameWithoutExt = file.name.replace(/\.[^/.]+$/, "")
  formData.append('name', fileNameWithoutExt)
  formData.append('description', `上传于 ${new Date().toLocaleString()}`)
  formData.append('file', file)
  
  try {
    const loadingMessage = ElMessage.info('正在上传文件...')
    
    const response = await axios.post(`${API_BASE_URL}/api/datasets`, formData, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'multipart/form-data'
      }
    })
    
    loadingMessage.close()
    
    if (response.data.code === 200) {
      ElMessage.success('文件上传成功！')
      await fetchFileList()
      
      const newFile = response.data.data
      if (newFile) {
        const updatedFile = fileList.value.find(f => f.data_id === newFile.data_id)
        if (updatedFile && !isFileSelected(updatedFile)) {
          selectedFiles.value.push(updatedFile)
          ElMessage.success(`已自动选择文件: ${updatedFile.data_name}`)
        }
      }
    } else {
      ElMessage.error('上传失败: ' + response.data.message)
    }
    
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('上传失败: ' + (error.response?.data?.message || error.message))
  }
}

// 显示文件历史
const showFileHistory = () => {
  fetchFileList()
  fileHistoryVisible.value = true
}

// 消息持久化已迁移至服务端（见 /api/conversations），不再使用 localStorage


// AI 助手默认欢迎语（初始加载与新建对话共用，避免两段文案不一致）
const AI_GREETING = '已为您准备常用图表模板。请先上传或选择数据文件，再点击下方模板、或直接描述您想要的图表，生成后可一键导出 PDF。'

// 生成一条新的欢迎语（每次返回新对象，避免引用共享）
const makeGreeting = () => ({
  role: 'assistant',
  content: AI_GREETING,
  timestamp: new Date()
})

// 安全解析服务端返回的 selected_files（可能为 JSON 字符串或数组）
const safeParse = (v) => {
  if (!v) return []
  if (Array.isArray(v)) return v
  try {
    const p = JSON.parse(v)
    return Array.isArray(p) ? p : []
  } catch {
    return []
  }
}

// 拉取当前用户的对话列表
const fetchConversations = async () => {
  const token = getAuthToken()
  if (!token) return
  try {
    const res = await axios.get(`${API_BASE_URL}/api/conversations`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    conversations.value = res.data.data || []
  } catch (e) {
    console.error('获取对话列表失败:', e)
  }
}

// 新建对话（服务端），返回 conversation_id
const createConversation = async () => {
  const token = getAuthToken()
  if (!token) {
    ElMessage.error('请先登录')
    return null
  }
  try {
    const res = await axios.post(`${API_BASE_URL}/api/conversations`, { title: '新对话' }, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    const id = res.data.data.conversation_id
    currentConversationId.value = id
    await fetchConversations()
    return id
  } catch (e) {
    console.error('新建对话失败:', e)
    ElMessage.error('新建对话失败')
    return null
  }
}

// 切换到某个对话并加载其消息
const selectConversation = async (id) => {
  if (id === currentConversationId.value) {
    convDrawerOpen.value = false
    return
  }
  const token = getAuthToken()
  if (!token) return
  currentConversationId.value = id
  messages.value = []
  convDrawerOpen.value = false
  try {
    const res = await axios.get(`${API_BASE_URL}/api/conversations/${id}/messages`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    const msgs = res.data.data || []
    if (msgs.length === 0) {
      messages.value.push(makeGreeting())
    } else {
      messages.value = msgs.map(m => ({
        role: m.role,
        content: m.content,
        chartCode: m.chart_code || null,
        historyId: m.history_id || null,
        selectedFiles: safeParse(m.selected_files),
        timestamp: new Date(m.created_at),
        regenerating: false
      }))
    }
  } catch (e) {
    console.error('加载对话消息失败:', e)
    messages.value.push(makeGreeting())
  }
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 新建对话并进入空白会话
const newChat = async () => {
  inputMessage.value = ''
  selectedFiles.value = []
  const id = await createConversation()
  if (id) {
    messages.value = [makeGreeting()]
  }
  convDrawerOpen.value = false
}

// 重命名对话
const renameConversation = async (c) => {
  try {
    const { value } = await ElMessageBox.prompt('输入新的对话名称', '重命名对话', {
      inputValue: c.title,
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    const token = getAuthToken()
    if (!token || !value) return
    await axios.put(`${API_BASE_URL}/api/conversations/${c.conversation_id}`, { title: value }, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    await fetchConversations()
  } catch (e) {
    // 用户取消，忽略
  }
}

// 删除对话
const deleteConversation = async (c) => {
  try {
    await ElMessageBox.confirm(
      `确定删除对话「${c.title}」？该操作不可恢复。`,
      '删除对话',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const token = getAuthToken()
  if (!token) return
  try {
    await axios.delete(`${API_BASE_URL}/api/conversations/${c.conversation_id}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (currentConversationId.value === c.conversation_id) {
      currentConversationId.value = null
      messages.value = [makeGreeting()]
    }
    await fetchConversations()
  } catch (e) {
    console.error('删除对话失败:', e)
    ElMessage.error('删除失败')
  }
}

// 对话时间友好显示
const formatConvTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`
  return d.toLocaleDateString('zh-CN')
}
//键盘事件处理
const handleKeydown = (event) => {
  // 如果按下的是Enter键
  if (event.key === 'Enter') {
    // 如果同时按下了Shift键，则允许换行（不发送消息）
    if (event.shiftKey) {
      // 这里什么也不做，让浏览器默认处理换行
      return;
    } else {
      // 如果没有按下Shift键，则阻止默认行为（换行）并发送消息
      event.preventDefault();
      sendMessage();
    }
  }
}
// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim()) return
  
  const currentSelectedFiles = [...selectedFiles.value]
  
  if (selectedFiles.value.length > 0) {
    selectedFiles.value = []
  }
  
  const userMessage = {
    role: 'user',
    content: inputMessage.value || '（发送了文件）',
    timestamp: new Date(),
    selectedFiles: currentSelectedFiles
  }
  
  messages.value.push(userMessage)
  scrollToBottom()
  
  const currentInput = inputMessage.value || ''
  inputMessage.value = ''
  loading.value = true
  
  try {
    const token = getAuthToken()
    if (!token) {
      ElMessage.error('请先登录')
      return
    }

    // 确保存在可持久化的对话上下文
    let convId = currentConversationId.value
    if (!convId) {
      convId = await createConversation()
      if (!convId) return
    }

    const response = await axios.post(`${API_BASE_URL}/api/chat`, {
      message: currentInput,
      data_ids: currentSelectedFiles.length > 0 ?
        currentSelectedFiles.map(f => f.data_id) : undefined,
      selected_files: currentSelectedFiles,
      model: currentModel.value,
      conversation_id: convId
    }, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    const aiResponse = response.data.data.reply
    const chartCode = response.data.data.chart_code
    const historyId = response.data.data.history_id
    
    let finalChartCode = chartCode
    // 优先使用后端返回的chartCode，如果没有则从回复内容中提取
    if (!finalChartCode && aiResponse) {
    // 增强代码块匹配，支持多种格式
    const codeMatch = aiResponse.match(/```(?:\w+)?\s*([\s\S]*?)\s*```|\\begin\{document\}[\s\S]*?\\end\{document\}/)
    if (codeMatch) {
      finalChartCode = codeMatch[0].trim()
      // 如果是代码块格式，移除标记
      if (finalChartCode.startsWith('```')) {
        finalChartCode = finalChartCode.replace(/```[\w]*\s*/, '').replace(/\s*```/, '')
      }
    }
  }

    const aiMessage = {
      role: 'assistant',
      content: aiResponse,
      timestamp: new Date(),
      chartCode: finalChartCode,
      historyId: historyId,
      regenerating: false
    }
    
    if (historyId) {
      ElMessage.success('历史记录已保存')
    }

    messages.value.push(aiMessage)
    await fetchConversations()
    scrollToBottom()
    
  } catch (error) {
    console.error('发送消息失败:', error)
    // 错误处理，与后端保持一致（按实际模型动态显示）
    const modelName = currentModel.value === 'qwen' ? 'Qwen' : 'DeepSeek'
    let errorMessage = ''
    if (error.code === 'NETWORK_ERROR' || error.message === 'Network Error') {
      errorMessage = `无法连接到${modelName} API，请检查网络设置`
    } else if (error.response) {
      // 优先使用后端返回的错误信息
      errorMessage = error.response.data?.message ||
                    error.response.data?.error?.message ||
                    `${modelName} API错误: ${error.response.data?.error?.message || '未知错误'}`
      // 401 状态码额外提示重新登录
      if (error.response.status === 401) {
        errorMessage += '（登录已过期，请重新登录）'
      }
    } else {
      errorMessage = '服务器内部错误: ' + error.message
    }
    ElMessage.error(errorMessage)
    // 以错误气泡持久展示，避免瞬时提示被忽略（覆盖 34/50 这类空气泡）
    messages.value.push({
      role: 'assistant',
      content: errorMessage,
      timestamp: new Date(),
      isError: true
    })
    scrollToBottom()
  } finally {
    loading.value = false
  }
}


// 编译为PDF
const compileToPDF = async (message) => {
  if (!message.chartCode) {
    ElMessage.warning('没有可编译的代码')
    return
  }
  
  if (!message.historyId) {
    ElMessage.error('该消息没有关联的历史记录，请重新发送消息生成图表')
    return
  }
  
  compiling.value = true
  
  try {
    const token = getAuthToken()
    if (!token) {
      ElMessage.error('请先登录')
      return
    }
    
    const response = await axios.post(`${API_BASE_URL}/api/compile/${message.historyId}`, {}, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    const pdfUrl = response.data.data.pdf_url
    
    ElMessage.success('PDF生成成功！')
    await showPdfPreview(pdfUrl)
    
  } catch (error) {
    console.error('编译失败:', error)
    
    if (error.response?.status === 404) {
      ElMessage.error('历史记录不存在，请重新生成图表')
    } else if (error.response?.status === 400) {
      ElMessage.error('该历史记录没有可编译的图表代码')
    } else {
      const rawMsg = error.response?.data?.message || error.message || '未知错误'
      ElMessage.error('PDF生成失败: ' + String(rawMsg).slice(0, 300))
    }
  } finally {
    compiling.value = false
  }
}

// 浏览器显示PDF预览
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
}

// 复制内容函数
const copyContent = (content) => {
  copyToClipboard(content)
    .then(() => {
      ElMessage.success('已复制到剪贴板')
    })
    .catch(err => {
      console.error('复制失败:', err)
      ElMessage.error('复制失败，请手动选择文本复制')
    })
}

// 添加相同的兼容性函数
const copyToClipboard = (text) => {
  return new Promise((resolve, reject) => {
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text)
        .then(resolve)
        .catch(reject)
      return
    }
    
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

// 显示完整代码
const showFullCode = (code) => {
  currentCode.value = code
  codeDialogVisible.value = true
}

// 获取文件列表
const fetchFileList = async () => {
  loadingFiles.value = true
  try {
    const token = getAuthToken()
    if (!token) return
    
    const response = await axios.get(`${API_BASE_URL}/api/datasets`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    fileList.value = response.data.data.map(item => ({
      ...item,
      data_size: item.data_size || 0,
      upload_time: item.upload_time || new Date().toLocaleDateString()
    }))
    
  } catch (error) {
    console.error('获取文件列表失败:', error)
    ElMessage.error('获取文件列表失败')
  } finally {
    loadingFiles.value = false
  }
}

// 切换文件选择状态
const toggleSelectFile = (file) => {
  const index = selectedFiles.value.findIndex(f => f.data_id === file.data_id);
  if (index > -1) {
    selectedFiles.value.splice(index, 1);
    ElMessage.info(`已取消选择文件: ${file.data_name}`);
  } else {
    selectedFiles.value.push(file);
    ElMessage.success(`已选择文件: ${file.data_name}`);
  }
}

// 移除选中的文件
const removeSelectedFile = (file) => {
  const index = selectedFiles.value.findIndex(f => f.data_id === file.data_id);
  if (index > -1) {
    selectedFiles.value.splice(index, 1);
    ElMessage.info(`已取消选择文件: ${file.data_name}`);
  }
}

// 全选文件
const selectAllFiles = () => {
  selectedFiles.value = [...fileList.value];
  ElMessage.success(`已选择全部 ${fileList.value.length} 个文件`);
}

// 清除所有选中的文件
const clearAllSelectedFiles = () => {
  if (selectedFiles.value.length > 0) {
    ElMessage.info(`已清除所有选中的文件`);
  }
  selectedFiles.value = [];
}

// 点击消息中的文件
const selectFile = (file) => {
  if (!isFileSelected(file)) {
    selectedFiles.value.push(file);
    ElMessage.success(`已选择文件: ${file.data_name}`);
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      const container = messagesContainer.value
      
      // 平滑滚动,使用 requestAnimationFrame 确保在下一帧执行滚动
      requestAnimationFrame(() => {
        container.scrollTo({
          top: container.scrollHeight,
          behavior: 'smooth'
        })
      })
    }
  })
}

// 初始化
onMounted(async () => {
  fetchFileList()
  await fetchConversations()
  if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0].conversation_id)
  } else {
    messages.value.push(makeGreeting())
  }

  // 添加全局拖拽事件监听
  document.addEventListener('dragover', (e) => {
    e.preventDefault()
  })

  document.addEventListener('drop', (e) => {
    e.preventDefault()
  })
})
</script>

<style scoped>
.ai-chat-container {
  display: flex;
  flex-direction: row;
  height: 100%;
  background-color: var(--bg-soft);
  position: relative;
  overflow: hidden;
}

.chat-layout {
  display: flex;
  flex-direction: row;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

/* 对话列表面板（DeepSeek 风格） */
.conversation-sidebar {
  width: 260px;
  flex-shrink: 0;
  height: 100%;
  background: var(--bg-surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.25s ease;
}

.conv-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.conv-sidebar__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-strong);
}

.conv-new-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--bg-surface);
  background: var(--brand-gradient);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.conv-new-btn:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-1px);
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  gap: 8px;
}

.conv-item:hover {
  background: var(--bg-soft);
}

.conv-item.active {
  background: var(--brand-soft);
}

.conv-item.active .conv-item__title {
  color: var(--brand);
}

.conv-item__main {
  min-width: 0;
  flex: 1;
}

.conv-item__title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-item__meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-item__actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.conv-item:hover .conv-item__actions {
  display: flex;
}

/* 修复图标大小不生效：双类选择器覆盖 Element Plus 默认样式 */
.el-icon.conv-action {
  font-size: 30px;  /* 图标尺寸，按需调整大小 */
  color: var(--text-regular);  /* 默认颜色加深，不再用浅灰色，更明显 */
  padding: 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.el-icon.conv-action:hover {
  background: var(--bg-soft);
  color: var(--brand);
}

.el-icon.conv-action--danger:hover {
  color: var(--danger);
}

.conv-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
}

.conv-toggle {
  display: inline-flex;
}

.conv-overlay {
  display: none;
}


.chat-section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  overflow: hidden;
  position: relative;
  height: 100%;
  width: 100%;
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-surface);
  z-index: 10;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.chat-title-icon {
  font-size: 20px;
  color: var(--brand);
}

.conv-toggle {
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--brand);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
}

.conv-toggle:hover {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.chat-header .new-chat-btn {
  margin-left: auto;
}

/* 桌面端折叠：侧栏宽度推挤至 0，聊天区无缝占满全宽 */
@media (min-width: 769px) {
  .chat-layout.conv-collapsed .conversation-sidebar {
    width: 0;
    min-width: 0;
    border-right: none;
  }
}

.chat-header h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 新建对话按钮：清爽胶囊样式，主题色描边，悬停渐变填充（参考 DeepSeek 开启新对话） */
.new-chat-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--brand);
  background: var(--bg-surface);
  border: 1px solid var(--brand-soft);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.new-chat-btn:hover {
  color: var(--bg-surface);
  background: var(--brand-gradient);
  border-color: transparent;
  box-shadow: var(--shadow-hover);
}

.new-chat-btn:active {
  transform: translateY(1px);
}

.new-chat-icon {
  font-size: 16px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg-soft);
  position: relative;
  min-height: 0;
  padding-bottom: 20px; /* 为底部区域留出空间 */
}

/* 拖拽覆盖层 */
.drag-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7); /* 深色背景 */
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
  animation: fadeIn 0.2s ease;
  pointer-events: none; /* 防止拖拽层干扰拖拽事件 */
}

.drag-overlay-content {
  text-align: center;
  color: white;
  animation: slideUp 0.3s ease;
  pointer-events: none;
  max-width: 500px;
  padding: 20px;
}

.upload-icon-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: 20px;
}

.upload-icon {
  font-size: 80px;
  color: white;
}

.arrow-icon {
  position: absolute;
  top: -10px;
  right: -10px;
  font-size: 30px;
  color: var(--success); /* 绿色 */
  background: var(--bg-surface);
  border-radius: 50%;
  padding: 4px;
}

.drag-text {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 12px;
}

.drag-tip {
  font-size: 14px;
  opacity: 0.9;
  line-height: 1.5;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* 消息样式 */
.message-item {
  margin-bottom: 20px;
  max-width: 80%;
  position: relative;
}

.message-item.user {
  margin-left: auto;
  display: flex;
  justify-content: flex-end;
}

.message-content {
  display: inline-block;
  padding: 12px 16px;
  border-radius: 12px;
  position: relative;
  word-break: break-word;
  max-width: 100%;
  box-shadow: var(--shadow-sm);
}

.message-item.user .message-content {
  background: var(--brand-gradient);
  color: white;
  border-radius: 12px 12px 2px 12px;
}

.message-item:not(.user) .message-content {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 2px 12px 12px 12px;
}

.message-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;
}

.message-info strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-strong);
}

.message-item.user .message-info strong {
  color: white;
}

.message-time {
  font-size: 12px;
  color: var(--text-muted);
  opacity: 0.7;
}

.message-item.user .message-time {
  color: rgba(255, 255, 255, 0.85);
}

/* 用户消息操作区域样式 */
.user-message-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.message-item.user .user-message-actions {
  border-top-color: rgba(255, 255, 255, 0.3);
}

.copy-message-btn {
  padding: 4px 8px;
  color: rgba(255, 255, 255, 0.7);
  transition: all 0.3s ease;
  border: none;
  background: transparent;
}

.copy-message-btn:hover {
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}

.copy-message-btn .el-icon {
  font-size: 14px;
}

/* 文件显示样式 */
/* 历史文件 */
.file-history-dialog :deep(.el-dialog__body) {
  padding: 16px;
}
.file-history-content {
  width: 100%;
}
.file-history-header {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-start; /* 全选/全不选靠左 */
}
.file-history-actions {
  display: flex;
  gap: 8px; /* 按钮间距缩小 */
}
.file-history-list {
  width: 100%;
  border: 1px solid var(--border); /* 列表边框 */
  border-radius: 8px;
  overflow: hidden;
}
.file-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border); /* 行分隔线 */
  cursor: pointer;
  transition: background-color 0.2s;
}
.file-history-item:last-child {
  border-bottom: none;
}
.file-history-item:hover {
  background-color: var(--bg-soft); /* hover背景色 */
}
.file-history-item.active {
  background-color: var(--brand-soft); /* 选中行背景色 */
}
.file-info {
  display: flex;
  align-items: center;
  flex: 1; /* 占满左侧空间 */
  gap: 12px; /* 元素间距 */
}
.file-checkbox {
  flex-shrink: 0; /* 复选框不缩小 */
}
.file-icon {
  font-size: 16px;
  color: var(--brand);
  flex-shrink: 0;
}
.file-details {
  flex: 1; /* 文件名占满中间空间 */
}
.file-name {
  font-size: 14px;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap; /* 文件名不换行 */
}
/* 新增：右侧大小和日期的布局 */
.file-meta-right {
  display: flex;
  gap: 24px; /* 大小和日期间距 */
  margin-left: auto; /* 右对齐 */
}
.file-meta-right span {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap; /* 不换行 */
}
/* 调整选中标签样式 */
.file-actions {
  margin-left: 16px;
}
.file-actions :deep(.el-tag) {
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
}
/* 空状态样式调整 */
.empty-files {
  padding: 40px 0;
  text-align: center;
}

.message-files {
  margin-top: 8px;
  margin-bottom: 8px;
}

.files-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  animation: fadeIn 0.3s ease;
  min-width: 180px;
  position: relative;
  overflow: hidden;
}

.file-item:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.3);
}

.message-item.user .file-item {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.25);
}

.message-item.user .file-item:hover {
  background: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.35);
}

.file-item .file-icon {
  font-size: 16px;
  color: var(--brand);
  margin-right: 8px;
  flex-shrink: 0;
}

.message-item.user .file-item .file-icon {
  color: rgba(255, 255, 255, 0.9);
}

.file-item .file-name {
  flex: 1;
  font-weight: 500;
  color: var(--text-strong);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-item.user .file-item .file-name {
  color: rgba(255, 255, 255, 0.95);
  font-weight: 500;
}

.file-item .file-size {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 8px;
  flex-shrink: 0;
  opacity: 0.85;
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.message-item.user .file-item .file-size {
  color: rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-size: 14px;
  color: var(--text-strong);
}

/* 错误气泡：红底红字，与正常 AI 气泡区分 */
.message-item.error .message-content {
  background: var(--danger-soft);
  border: 1px solid var(--danger);
  border-radius: 2px 12px 12px 12px;
}

.message-item.error .message-info strong {
  color: var(--danger);
}

.message-item.error .error-text {
  color: var(--danger);
}

.message-item.user .message-text {
  color: white;
}

/* 底部容器 */
.bottom-container {
  position: relative;
  background: var(--bg-surface);
  border-top: 1px solid var(--border);
  z-index: 100;
  padding: 16px 20px;
  box-shadow: var(--shadow-xs);
  width: 100%;
}

/* 已选择文件显示 */
.selected-files {
  background: var(--bg-soft);
  border-radius: 8px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  margin-bottom: 12px;
  animation: slideUp 0.3s ease;
  max-height: 120px;
  overflow-y: auto;
}

.selected-files-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-strong);
}

.selected-files-header .el-icon {
  color: var(--brand);
  font-size: 16px;
}

.selected-files-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.selected-file-item {
  display: flex;
  align-items: center;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 6px 10px;
  gap: 6px;
  transition: all 0.2s;
  box-shadow: var(--shadow-xs);
  cursor: pointer;
  min-width: 150px;
  position: relative;
  overflow: hidden;
}

.selected-file-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
  border-color: var(--brand);
}

.selected-file-item .file-icon {
  color: var(--brand);
  font-size: 16px;
  flex-shrink: 0;
}

.selected-file-item .file-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.selected-file-item .file-size {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
  opacity: 0.9;
  background: rgba(0, 0, 0, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.selected-file-item .remove-icon {
  color: var(--danger);
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
  opacity: 0.7;
  transition: all 0.2s;
  margin-left: 2px;
}

.selected-file-item .remove-icon:hover {
  opacity: 1;
  color: var(--danger);
}

.clear-all-btn {
  color: var(--danger);
  font-size: 12px;
  padding: 0 4px;
  height: auto;
  min-height: 0;
  margin-left: auto;
}

.clear-all-btn:hover {
  background: rgba(245, 108, 108, 0.1);
}

/* 输入框包装器 */
.input-wrapper {
  position: relative;
  background: var(--bg-surface);
  border-radius: 12px;
  border: 1px solid var(--border);
  padding: 16px;
  box-shadow: var(--shadow-base);
  transition: all 0.3s;
  width: 100%;
}

.input-wrapper:hover {
  box-shadow: var(--shadow-lg);
  border-color: var(--brand);
}

.input-container {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  margin-bottom: 12px;
  width: 100%;
}

.message-input {
  flex: 1;
  width: 100%;
}

.message-input :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 14px;
  resize: none;
  box-shadow: none;
  transition: all 0.3s;
  min-height: 60px;
  max-height: 150px;
  overflow-y: auto;
  font-family: var(--font-sans);
  width: 100%;
}

.message-input :deep(.el-textarea__inner):focus {
  box-shadow: none;
  background: var(--bg-soft);
}

.message-input :deep(.el-textarea__inner)::placeholder {
  color: var(--text-muted);
  font-size: 13px;
}

/* 发送按钮样式 */
.send-btn {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: var(--brand-gradient);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  box-shadow: var(--shadow-hover);
  align-self: flex-end;
  margin-bottom: 2px;
  padding: 0;
}

.send-btn:hover:not(.is-disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
  background: linear-gradient(135deg, var(--brand-hover), var(--brand-active));
}

.send-btn:active:not(.is-disabled) {
  transform: translateY(0);
}

.send-btn.is-disabled {
  background: var(--brand-soft);
  color: var(--brand);
  cursor: not-allowed;
  box-shadow: var(--shadow-xs);
}

.send-btn .el-icon {
  font-size: 18px;
  color: var(--bg-surface);
}

.send-btn.is-disabled .el-icon {
  color: var(--brand);
}

.loading-icon {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 按钮容器 */
.button-container {
  display: flex;
  gap: 8px;
  justify-content: flex-start;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}

.upload-btn, .history-btn, .model-btn {
  border-radius: 6px;
  padding: 6px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s;
  font-weight: 400;
  border: 1px solid var(--border);
  background: var(--bg-soft);
  color: var(--text-regular);
  font-size: 12px;
  height: auto;
}

.upload-btn:hover, .history-btn:hover, .model-btn:hover {
  background: var(--bg-hover);
  border-color: var(--text-muted);
  transform: translateY(-1px);
}

.upload-btn:active, .history-btn:active {
  transform: translateY(0);
}

.upload-btn .el-icon, .history-btn .el-icon {
  font-size: 14px;
}

/* 图表结果样式 */
.chart-result {
  margin-top: 12px;
  border-top: 1px dashed var(--border);
  padding-top: 12px;
}

.chart-result h4 {
  margin: 0 0 8px 0;
  color: var(--brand);
  font-size: 14px;
}

.chart-preview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chart-preview__head h4 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 代码预览已统一由 <CodeBlock> 组件承载（高亮/行号/复制） */

.chart-actions {
  display: flex;
  gap: 6px;
}

/* 首屏引导区 */
.guide-area {
  padding: 8px 0 12px;
}
.guide-hero {
  text-align: center;
  padding: 28px 20px 24px;
  background: var(--brand-gradient);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  color: #fff;
}
.guide-hero__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(4px);
}
.guide-hero__badge .el-icon {
  font-size: 26px;
  color: #fff;
}
.guide-hero__title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--on-brand);
}
.guide-hero__subtitle {
  margin: 0 auto;
  max-width: 520px;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.92);
}
.guide-steps {
  display: flex;
  align-items: stretch;
  gap: 10px;
  margin: 20px 0 8px;
}
.guide-step {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
}
.guide-step__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
}
.guide-step__icon .el-icon {
  font-size: 20px;
}
.guide-step__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong);
}
.guide-step__desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
  line-height: 1.4;
}
.guide-step__arrow {
  display: flex;
  align-items: center;
  color: var(--text-muted);
  font-size: 18px;
  flex-shrink: 0;
}
.guide-templates__title {
  margin: 22px 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-strong);
  text-align: center;
}
.template-cards {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}
.template-card {
  width: 180px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  text-align: left;
  padding: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  cursor: pointer;
  transition: all 0.2s ease;
}
.template-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}
.template-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: var(--brand);
  background: var(--brand-soft);
  padding: 8px;
  border-radius: var(--radius-sm);
}
.template-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong);
}
.template-card__desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
}
@media (max-width: 768px) {
  .guide-steps {
    flex-direction: column;
  }
  .guide-step__arrow {
    transform: rotate(90deg);
    align-self: center;
  }
  .guide-hero {
    padding: 22px 16px 18px;
  }
  .guide-hero__title {
    font-size: 19px;
  }
}


/* 响应式设计 */
@media (max-width: 768px) {
  .user-message-actions {
    margin-top: 6px;
  }
  
  .copy-message-btn {
    padding: 3px 6px;
  }
  
  .copy-message-btn .el-icon {
    font-size: 12px;
  }
  
  .ai-chat-container {
    padding: 0;
  }
  
  .chat-section {
    padding: 0;
  }
  
  .message-item {
    max-width: 90%;
  }
  
  .input-container {
    flex-direction: column;
  }
  
  .button-container {
    flex-direction: column;
  }
  
  .selected-file-item {
    min-width: 120px;
  }
  
  .bottom-container {
    padding: 12px 16px;
  }

  /* 对话列表面板：移动端抽屉化 */
  .conversation-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 1000;
    width: 260px;
    height: 100vh;
    box-shadow: var(--shadow-hover);
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }

  .conversation-sidebar.mobile-open {
    transform: translateX(0);
  }

  .conv-toggle {
    display: inline-flex;
  }

  .conv-overlay {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 999;
    background: rgba(16, 24, 40, 0.45);
  }

  .chat-header {
    padding-left: 56px;
  }
}
</style>