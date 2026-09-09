<template>
  <div class="data-upload">
    <div class="upload-section">
      <h2><el-icon><Folder /></el-icon> 上传数据</h2>
      <p>在这里上传您的数据文件</p>
      
      <!-- 上传表单 -->
      <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="100px" class="upload-form">
        <el-form-item label="数据集名称" prop="name">
          <el-input 
            v-model="uploadForm.name" 
            placeholder="请输入名称"
            clearable
            maxlength="50"
          />
        </el-form-item>
        
        <el-form-item label="数据描述" prop="description">
          <el-input 
            v-model="uploadForm.description" 
            type="textarea" 
            :rows="3"
            placeholder="请输入描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        
        <el-form-item label="数据文件" prop="file" required>
          <el-upload
            class="upload-demo"
            drag
            :action="`${API_BASE_URL}/api/datasets`"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :before-upload="beforeUpload"
            :file-list="fileList"
            :show-file-list="true"
            :limit="1"
            ref="uploadRef"
          >
            <div class="upload-area">
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
              <div class="el-upload__tip">支持单个文件上传，最大100MB</div>
            </div>
            <template #tip>
              <div class="el-upload__tip" v-if="selectedFile">
                已选择文件: {{ selectedFile.name }} ({{ formatFileSize(selectedFile.size) }})
              </div>
            </template>
          </el-upload>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="handleUpload" :loading="uploading">
            上传
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="data-management">
      <h2><el-icon><Folder /></el-icon> 数据管理</h2>
      
      <!-- 搜索框 -->
      <div class="search-section">
        <el-input
          v-model="searchKeyword"
          placeholder="请输入关键词 搜索"
          class="search-input"
          clearable
          @input="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="fetchDatasets" />
          </template>
        </el-input>
      </div>
      
      <!-- 数据表格 -->
      <el-table 
        :data="filteredData" 
        style="width: 100%" 
        class="data-table" 
        v-loading="loading"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="name" label="数据集" width="220">
          <template #default="{ row }">
            <div class="dataset-name">
              <span class="name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="size" label="数据大小" width="150">
          <template #default="{ row }">
            <span class="data-size">{{ row.size }}</span>
          </template>
        </el-table-column>
        
        <!-- 修改上传时间列，添加排序功能 -->
        <el-table-column 
          prop="uploadTime" 
          label="上传时间" 
          width="180"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
          :sort-by="sortByUploadTime"
        >
          <template #default="{ row }">
            <span class="upload-time">{{ row.uploadTime }}</span>
          </template>
          </el-table-column>
        
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button 
                type="success" 
                size="small" 
                @click="handleDownload(row)"
                class="download-btn"
                v-if="row.filePath"
              >
                下载
              </el-button>
              <el-button 
                type="primary" 
                size="small" 
                @click="handleEdit(row)"
                class="edit-btn"
              >
                编辑
              </el-button>
              <el-button 
                type="danger" 
                size="small" 
                @click="handleDelete(row)"
                class="delete-btn"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑数据集"
      width="500px"
      :before-close="closeEditDialog"
    >
      <el-form 
        :model="editForm" 
        :rules="editRules" 
        ref="editFormRef" 
        label-width="80px"
      >
        <el-form-item label="名称" prop="name">
          <el-input 
            v-model="editForm.name" 
            placeholder="请输入数据集名称"
            clearable
            maxlength="50"
          />
        </el-form-item>
        
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model="editForm.description" 
            type="textarea" 
            :rows="3"
            placeholder="请输入数据描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeEditDialog">取消</el-button>
          <el-button type="primary" @click="saveEdit">
            保存
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Search, Folder } from '@element-plus/icons-vue'

// 添加排序相关变量
const sortField = ref('uploadTime')
const sortOrder = ref('descending') // 默认按时间倒序，最新的在前面
// 添加一个 AbortController 引用
const abortController = ref(null)

// API基础URL - 根据你的后端配置修改
import { API_BASE_URL } from '@/config';

// 获取认证token
const getAuthToken = () => {
  const possibleTokens = [
    localStorage.getItem('token'),
    sessionStorage.getItem('token'),
    localStorage.getItem('authToken'),
    sessionStorage.getItem('authToken'),
    localStorage.getItem('userToken'), 
    sessionStorage.getItem('userToken')
  ];
  
  for (let token of possibleTokens) {
    if (token && token !== 'null' && token !== 'undefined') {
      return token.replace(/^["']|["']$/g, '').trim();
    }
  }
  
  return null;
}

// 上传表单数据
const uploadForm = ref({
  name: '',
  description: '',
  file: null
})

// 上传规则验证
const uploadRules = {
  name: [
    { required: true, message: '请输入数据集名称', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入数据描述', trigger: 'blur' }
  ],
  file: [
    { required: true, message: '请选择要上传的文件', trigger: 'change' }
  ]
}

// 编辑对话框相关
const editDialogVisible = ref(false)
const editForm = ref({
  id: null,
  name: '',
  description: ''
})

// 编辑表单验证规则
const editRules = {
  name: [
    { required: true, message: '请输入数据集名称', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入数据描述', trigger: 'blur' }
  ]
}

// 文件列表
const fileList = ref([])
const selectedFile = ref(null)
const uploading = ref(false)
const loading = ref(false)
const uploadFormRef = ref()
const uploadRef = ref()
const editFormRef = ref()

// 搜索关键词
const searchKeyword = ref('')

// 表格数据
const tableData = ref([])

// 处理文件选择
const handleFileChange = (file) => {
  selectedFile.value = file.raw
  uploadForm.value.file = file.raw
}

// 处理文件移除
const handleFileRemove = () => {
  selectedFile.value = null
  uploadForm.value.file = null
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// 上传前的验证
const beforeUpload = (file) => {
  const isLt100M = file.size / 1024 / 1024  < 100
  if (!isLt100M) {
    ElMessage.error('文件大小不能超过 100MB!')
    return false
  }
  return true
}

// 处理上传
const handleUpload = async () => {
  uploadFormRef.value.validate(async (valid) => {
    if (valid) {
      if (!uploadForm.value.file) {
        ElMessage.warning('请选择要上传的文件')
        return
      }

      // 添加文件大小验证
      const maxSizeMB = 100
      const fileSizeMB = uploadForm.value.file.size / 1024 / 1024
      
      if (fileSizeMB > maxSizeMB) {
        ElMessage.error(`文件大小不能超过 ${maxSizeMB}MB!`)
        return
      }
      
      uploading.value = true
      
      // 创建 AbortController 用于取消上传
      abortController.value = new AbortController()

      try {
        const token = getAuthToken();
        if (!token) {
          ElMessage.error('未找到token')
          return;
        }
        
        // 创建FormData对象，用于文件上传
        const formData = new FormData()
        formData.append('name', uploadForm.value.name)
        formData.append('description', uploadForm.value.description)
        formData.append('file', uploadForm.value.file)
        
        // 设置上传超时（3分钟）
        const timeoutId = setTimeout(() => {
          if (abortController.value) {
            abortController.value.abort()
          }
        }, 3 * 60 * 1000)
        // 显示上传进度提示
        ElMessage.info('文件上传中，请稍候...')

        // 调用后端API上传数据（包含文件）
        const response = await fetch(`${API_BASE_URL}/api/datasets`, {
          method: 'POST',
          body: formData,
          headers: {
            'Authorization': `Bearer ${token}`
          },
          signal: abortController.value.signal  // 添加取消信号
        })
        
        clearTimeout(timeoutId)

        const result = await response.json()
        
        if (response.ok) {
          ElMessage.success('数据上传成功！')
          resetForm()
          // 重新获取数据
          fetchDatasets()
        } else {
          ElMessage.error(result.message || '上传失败')
        }
      } catch (error) {
        console.error('上传错误:', error)

        if (error.name === 'AbortError') {
          ElMessage.info('上传已取消')
        } else if (error.message && error.message.includes('network')) {
          ElMessage.error('网络错误，请检查后端服务器是否运行')
        } else {
          ElMessage.error('上传失败: ' + (error.message || '未知错误'))
        }
      } finally {
        uploading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  // 如果正在上传，取消上传
  if (uploading.value && abortController.value) {
    abortController.value.abort()
  }
  uploadFormRef.value.resetFields()
  fileList.value = []
  selectedFile.value = null
  uploadForm.value.file = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  uploading.value = false
}

// 处理编辑 - 打开编辑对话框
const handleEdit = (row) => {
  editForm.value = {
    id: row.id,
    name: row.name,
    description: row.description || ''
  }
  editDialogVisible.value = true
}

// 保存编辑
const saveEdit = async () => {
  editFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    try {
      const token = getAuthToken()
      if (!token) {
        ElMessage.error('未找到token')
        return
      }

      // 调用后端API更新数据
      const response = await fetch(`${API_BASE_URL}/api/datasets/${editForm.value.id}/update`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          name: editForm.value.name,
          description: editForm.value.description
        })
      })
      
      const result = await response.json()
      
      if (response.ok) {
        ElMessage.success('修改成功！')
        // 更新本地数据
        const index = tableData.value.findIndex(item => item.id === editForm.value.id)
        if (index !== -1) {
          tableData.value[index].name = editForm.value.name
          tableData.value[index].description = editForm.value.description
        }
        editDialogVisible.value = false
      } else {
        ElMessage.error(result.message || '修改失败')
      }
    } catch (error) {
      console.error('编辑保存失败:', error)
      ElMessage.error('编辑保存失败，请检查网络连接')
    }
  })
}

// 关闭编辑对话框
const closeEditDialog = () => {
  editDialogVisible.value = false
  editFormRef.value?.resetFields()
}

// 处理下载
const handleDownload = async (row) => {
  try {
    const token = getAuthToken();
    if (!token) {
      ElMessage.error('请先登录');
      return;
    }

    ElMessage.info('开始下载，请稍候...');

    const response = await fetch(`${API_BASE_URL}/api/datasets/download/${row.data_id}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('认证失败，请重新登录');
      } else if (response.status === 404) {
        throw new Error('文件不存在');
      } else {
        throw new Error(`下载失败: ${response.status}`);
      }
    }

    // 获取blob数据
    const blob = await response.blob();
    
    // 创建下载链接
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    
    // 从响应头或数据中获取文件名
    const contentDisposition = response.headers.get('content-disposition');
    let filename = row.file_name || `dataset_${row.data_id}`;
    
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename="?(.+)"?/);
      if (filenameMatch && filenameMatch[1]) {
        filename = decodeURIComponent(filenameMatch[1]);
      }
    }
    
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    // 清理URL对象
    window.URL.revokeObjectURL(url);
    
    ElMessage.success('下载完成');
    
  } catch (error) {
    console.error('下载错误:', error);
    ElMessage.error(error.message || '下载失败，请检查网络连接');
  }
}

// 处理删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据集 "${row.name}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    const token = getAuthToken();
    if (!token) {
      ElMessage.error('未找到token')
      return;
    }

    const response = await fetch(`${API_BASE_URL}/api/datasets/${row.id}`, {
      headers: {
        'Authorization': `Bearer ${token}`
      },
      method: 'DELETE'
    })
    
    const result = await response.json()
    
    if (response.ok) {
      ElMessage.success('删除成功！')
      // 从本地数据中移除
      const index = tableData.value.findIndex(item => item.id === row.id)
      if (index !== -1) {
        tableData.value.splice(index, 1)
      }
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (error) {
    // 用户取消了操作
  }
}

// 处理搜索
const handleSearch = () => {
  // 防抖处理，避免频繁请求
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    fetchDatasets()
  }, 500)
}

let searchTimeout = null

// 获取数据集
const fetchDatasets = async () => {
  loading.value = true
  try {
    let url = `${API_BASE_URL}/api/datasets`
    if (searchKeyword.value.trim()) {
      url += `?keyword=${encodeURIComponent(searchKeyword.value.trim())}`
    }

    const token = getAuthToken();
    if (!token) {
      ElMessage.error('未找到token')
      return;
    }
    
    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    const result = await response.json()
    
    if (response.ok) {
      tableData.value = result.data.map(item => ({
        id: item.data_id,
        name: item.data_name,
        description: item.description,
        count: item.count || 0,
        size: item.size,
        uploadTime: item.upload_time,
        filePath: item.file_path,
        data_id: item.data_id,
        data_name: item.data_name
      }))

    } else {
      ElMessage.error(result.message || '获取数据失败')
    }
  } catch (error) {
    ElMessage.error('网络错误，请检查后端服务器是否运行')
    console.error('获取数据错误:', error)
  } finally {
    loading.value = false
  }
}

// 添加排序处理方法
const handleSortChange = ({prop, order }) => {
  if (prop === 'uploadTime') {
    sortField.value = prop
    sortOrder.value = order || 'descending'
  }
}

// 添加上传时间排序方法
const sortByUploadTime = (row) => {
  // 将时间字符串转换为时间戳进行排序
  return new Date(row.uploadTime).getTime()
}

// 修改过滤数据的计算属性，添加排序逻辑
const filteredData = computed(() => {
  let data = tableData.value
  
  // 搜索过滤
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    data = data.filter(item => 
      item.name.toLowerCase().includes(keyword) 
    )
  }
  
  // 排序处理
  if (sortField.value === 'uploadTime') {
    data = [...data].sort((a, b) => {
      const timeA = new Date(a.uploadTime).getTime()
      const timeB = new Date(b.uploadTime).getTime()
      
      if (sortOrder.value === 'ascending') {
        return timeA - timeB // 正序：时间早的在前
      } else {
        return timeB - timeA // 倒序：时间晚的在前
      }
    })
  }
  
  return data
})

// 组件挂载时初始化数据
onMounted(() => {
  fetchDatasets()
})
</script>

<style scoped>
.upload-time {
  color: var(--text-regular);
}

.data-upload {
  padding: 20px;
  background-color: var(--bg-soft);
  min-height: 100vh;
}

.upload-section {
  background: var(--bg-surface);
  padding: 24px;
  border-radius: 8px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
}

.upload-section h2 {
  margin: 0 0 12px 0;
  color: var(--text-strong);
  font-size: 20px;
}

.upload-section p {
  margin: 0 0 24px 0;
  color: var(--text-muted);
  font-size: 14px;
}

.upload-form {
  max-width: 800px;
}

.upload-demo {
  width: 100%;
}

.upload-area {
  padding: 40px 20px;
}

.el-icon--upload {
  font-size: 67px;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.el-upload__text {
  font-size: 14px;
  color: var(--text-regular);
  margin-bottom: 8px;
}

.el-upload__tip {
  font-size: 12px;
  color: var(--text-muted);
}

.data-management {
  background: var(--bg-surface);
  padding: 24px;
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.data-management h2 {
  margin: 0 0 20px 0;
  color: var(--text-strong);
  font-size: 20px;
}

.search-section {
  margin-bottom: 24px;
}

.search-input {
  max-width: 400px;
}

.data-table {
  margin-top: 20px;
}

.dataset-name {
  display: flex;
  align-items: center;
}

.name-text {
  font-weight: 500;
  color: var(--text-strong);
}

.data-size {
  color: var(--success);
  font-weight: 500;
}

.upload-time {
  color: var(--text-regular);
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.edit-btn {
  background-color: var(--brand);
  border-color: var(--brand);
}

.delete-btn {
  background-color: var(--danger);
  border-color: var(--danger);
}

.download-btn {
  background-color: var(--success);
  border-color: var(--success);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .data-upload {
    padding: 12px;
  }
  
  .upload-section,
  .data-management {
    padding: 16px;
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 4px;
  }
  
  .search-input {
    width: 100%;
  }
}
</style>