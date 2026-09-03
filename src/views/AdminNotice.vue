<template>
  <div class="admin-notice-container">
    <div class="header">
      <h1>通知管理</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>发布新通知
      </el-button>
    </div>

    <!-- 搜索和过滤 -->
    <el-card class="filter-card">
      <el-form :model="filterForm" @submit.prevent="handleSearch">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="关键词">
              <el-input
                v-model="filterForm.keyword"
                placeholder="搜索标题或内容"
                clearable
                @clear="handleSearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始日期">
              <el-date-picker
                v-model="filterForm.startDate"
                type="date"
                placeholder="选择开始日期"
                value-format="YYYY-MM-DD"
                @change="handleSearch"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束日期">
              <el-date-picker
                v-model="filterForm.endDate"
                type="date"
                placeholder="选择结束日期"
                value-format="YYYY-MM-DD"
                @change="handleSearch"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="filter-buttons">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
          <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
            <el-icon><Delete /></el-icon>批量删除
          </el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 通知列表 -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="noticeList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
        style="width: 100%"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="title" label="标题" min-width="150">
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200">
          <template #default="{ row }">
            <div class="content-preview">{{ formatContent(row.content) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="admin_name" label="发布者" width="120" />
        <el-table-column prop="created_time" label="发布时间" width="180" 
        sortable :sort-orders="['ascending', 'descending']" :sort-by="sortByCreatedTime">
          <template #default="{ row }">
            {{ formatTime(row.created_time) }}
          </template>
        </el-table-column>
        <el-table-column label="已读人数" width="110" align="center">
          <template #default="{ row }">
            <el-tag type="success" effect="plain">{{ row.read_count || 0 }} 人</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">
              <el-icon><View /></el-icon>查看
            </el-button>
            <el-button type="warning" link @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 查看/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="noticeFormRef"
        :model="noticeForm"
        :rules="noticeRules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="noticeForm.title"
            placeholder="请输入通知标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="noticeForm.content"
            type="textarea"
            :rows="8"
            placeholder="请输入通知内容"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEditMode ? '更新' : '发布' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Search,
  Refresh,
  Delete,
  View,
  Edit
} from '@element-plus/icons-vue'
import { API_BASE_URL } from '@/config';
// 状态管理
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const isEditMode = ref(false)
const selectedIds = ref([])
// 在状态管理部分添加排序相关状态
const sortField = ref('created_time')
const sortOrder = ref('descending')

// 数据
const noticeList = ref([])
const statistics = reactive({
  total_count: 0,
  read_count: 0,
  unread_count: 0
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0
})

// 过滤表单
const filterForm = reactive({
  keyword: '',
  startDate: '',
  endDate: ''
})

// 通知表单
const noticeForm = reactive({
  notice_id: null,
  title: '',
  content: ''
})

const noticeFormRef = ref(null)

// 表单验证规则
const noticeRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 1, max: 100, message: '标题长度在1到100个字符', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
    { min: 1, max: 1000, message: '内容长度在1到1000个字符', trigger: 'blur' }
  ]
}

// 计算属性
const dialogTitle = computed(() => {
  return isEditMode.value ? '编辑通知' : '发布新通知'
})

// 方法
const formatTime = (time) => {
  if (!time) return ''
  
  try {
    const date = new Date(time)
    
    // 检查日期是否有效
    if (isNaN(date.getTime())) {
      return time
    }
    
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  } catch (error) {
    console.error('日期格式化错误:', error)
    return time
  }
}

const formatContent = (content) => {
  if (!content) return ''
  return content.length > 50 ? content.substring(0, 50) + '...' : content
}

// 加载通知列表
const loadNotices = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      ...filterForm,
      sortField: sortField.value,
      sortOrder: sortOrder.value
    }
    
    // 构建查询参数，过滤空值
    const queryParams = new URLSearchParams()
    Object.keys(params).forEach(key => {
      if (params[key] !== undefined && params[key] !== '') {
        queryParams.append(key, params[key])
      }
    })
    
    const response = await fetch(`${API_BASE_URL}/api/admin/notices?${queryParams}`)
    const result = await response.json()
    
    if (result.code === 200) {
      noticeList.value = result.data.notices
      pagination.total = result.data.pagination.total
      pagination.totalPages = result.data.pagination.totalPages
      Object.assign(statistics, result.data.statistics)
    } else {
      ElMessage.error(result.message || '加载失败')
    }
  } catch (error) {
    console.error('加载通知列表失败:', error)
    ElMessage.error('网络错误，请重试')
  } finally {
    loading.value = false
  }
}

// 添加排序方法
const sortByCreatedTime = (a, b) => {
  const dateA = new Date(a.created_time).getTime()
  const dateB = new Date(b.created_time).getTime()
  return sortOrder.value === 'ascending' ? dateA - dateB : dateB - dateA
}

// 处理排序变化
const handleSortChange = ({ prop, order }) => {
  if (prop === 'created_time') {
    sortField.value = prop
    sortOrder.value = order
    loadNotices()
  }
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  loadNotices()
}

// 重置
const handleReset = () => {
  filterForm.keyword = ''
  filterForm.startDate = ''
  filterForm.endDate = ''
  pagination.page = 1
  loadNotices()
}

// 表格选择
const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.notice_id)
}

// 分页变化
const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.page = 1
  loadNotices()
}

const handleCurrentChange = (page) => {
  pagination.page = page
  loadNotices()
}

// 创建新通知
const handleCreate = () => {
  isEditMode.value = false
  noticeForm.notice_id = null
  noticeForm.title = ''
  noticeForm.content = ''
  dialogVisible.value = true
  nextTick(() => {
    noticeFormRef.value?.clearValidate()
  })
}

// 查看通知
const handleView = (row) => {
  ElMessageBox.alert(row.content, `通知详情 - ${row.title}`, {
    confirmButtonText: '关闭',
    customClass: 'notice-detail-dialog',
    dangerouslyUseHTMLString: false
  })
}

// 编辑通知
const handleEdit = (row) => {
  isEditMode.value = true
  noticeForm.notice_id = row.notice_id
  noticeForm.title = row.title
  noticeForm.content = row.content
  dialogVisible.value = true
  nextTick(() => {
    noticeFormRef.value?.clearValidate()
  })
}

// 删除通知
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除通知"${row.title}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await fetch(`${API_BASE_URL}/api/admin/notices/${row.notice_id}`, {
      method: 'DELETE'
    })
    const result = await response.json()

    if (result.code === 200) {
      ElMessage.success('删除成功')
      loadNotices()
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要删除的通知')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.length} 条通知吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await fetch(`${API_BASE_URL}/api/admin/notices`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ ids: selectedIds.value })
    })
    const result = await response.json()

    if (result.code === 200) {
      ElMessage.success('批量删除成功')
      selectedIds.value = []
      loadNotices()
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!noticeFormRef.value) return

  const valid = await noticeFormRef.value.validate()
  if (!valid) return

  submitting.value = true
  try {
    const url = isEditMode.value 
      ? `${API_BASE_URL}/api/admin/notices/${noticeForm.notice_id}`
      : `${API_BASE_URL}/api/admin/notices`
    
    const method = isEditMode.value ? 'PUT' : 'POST'
    
    const response = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        title: noticeForm.title,
        content: noticeForm.content
      })
    })
    
    const result = await response.json()
    
    if (result.code === 200) {
      ElMessage.success(isEditMode.value ? '更新成功' : '发布成功')
      dialogVisible.value = false
      loadNotices()
    } else {
      ElMessage.error(result.message || '操作失败')
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('网络错误，请重试')
  } finally {
    submitting.value = false
  }
}

// 初始化
onMounted(() => {
  loadNotices()
})
</script>

<style scoped>

.admin-notice-container {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.statistics-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 10px;
}

.stat-icon {
  font-size: 40px;
  margin-right: 15px;
}

.stat-icon.total {
  color: var(--brand);
}

.stat-icon.read {
  color: var(--success);
}

.stat-icon.unread {
  color: var(--warning);
}

.stat-icon.rate {
  color: var(--danger);
}

.stat-content {
  flex: 1;
}

.stat-number {
  font-size: var(--text-2xl);
  font-weight: bold;
  margin-bottom: 5px;
}

.stat-label {
  color: var(--text-muted);
  font-size: var(--text-base);
}

.filter-card {
  margin-bottom: 20px;
}

.filter-buttons {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.table-card {
  margin-bottom: 20px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unread-title {
  font-weight: bold;
}

.content-preview {
  color: var(--text-regular);
  line-height: 1.5;
}

.notice-detail-dialog {
  max-width: 80%;
}

.notice-detail-dialog .el-message-box__content {
  max-height: 60vh;
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>