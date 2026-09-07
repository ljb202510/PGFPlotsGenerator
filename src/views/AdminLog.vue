<!-- AdminLog.vue - 系统监控前端组件 -->
<template>
  <div class="admin-log-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1>系统监控</h1>
      <div class="header-actions">
        <el-button type="primary" @click="refreshData" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 系统健康概览 -->
    <div class="overview-section">
      <h2>系统健康概览</h2>
      <div class="overview-cards">
        <el-card class="overview-card">
          <div class="card-content">
            <div class="card-icon" :class="healthStatus.statusColor">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-title">系统状态</div>
              <div class="card-value">{{ healthStatus.systemStatusText }}</div>
            </div>
          </div>
        </el-card>
        
        <el-card class="overview-card">
          <div class="card-content">
            <div class="card-icon primary">
              <el-icon><Connection /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-title">API成功率</div>
              <div class="card-value">{{ apiStats.summary.success_rate || 0 }}%</div>
            </div>
          </div>
        </el-card>
        
        <el-card class="overview-card">
          <div class="card-content">
            <div class="card-icon warning">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-title">最近错误</div>
              <div class="card-value">{{ healthOverview.metrics.recentErrors || 0 }}</div>
            </div>
          </div>
        </el-card>
        
        <el-card class="overview-card">
          <div class="card-content">
            <div class="card-icon success">
              <el-icon><User /></el-icon>
            </div>
            <div class="card-info">
              <div class="card-title">活跃用户</div>
              <div class="card-value">{{ healthOverview.metrics.activeUsers || 0 }}</div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- API调用统计 -->
    <div class="chart-section">
      <h2>API调用统计</h2>
      <div class="chart-container">
        <div class="chart-filters">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="fetchApiStats"
            style="margin-right: 20px;"
          />
          <el-button @click="resetDateRange">重置</el-button>
        </div>
        
        <div class="chart-wrapper">
          <div ref="chartRef" style="width: 100%; height: 400px;"></div>
        </div>
        
        <!-- API调用详情 -->
        <div class="api-details">
          <div class="detail-item">
            <span class="detail-label">总调用次数:</span>
            <span class="detail-value">{{ apiStats.summary.total_calls || 0 }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">成功调用:</span>
            <span class="detail-value success">{{ apiStats.summary.success_calls || 0 }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">失败调用:</span>
            <span class="detail-value danger">{{ apiStats.summary.failed_calls || 0 }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">成功率:</span>
            <span class="detail-value">{{ apiStats.summary.success_rate || 0 }}%</span>
          </div>
        </div>
        
        <!-- 最近失败调用 -->
        <div class="recent-failures" v-if="apiStats.recentFailures && apiStats.recentFailures.length > 0">
          <h3>最近失败调用</h3>
          <el-table 
            :data="sortedFailures" 
            style="width: 100%"
            @sort-change="handleFailuresSortChange"
          >
            <el-table-column 
              prop="call_time" 
              label="时间" 
              width="180"
              sortable="custom"
              :sort-orders="['ascending', 'descending']"
              :sort-by="sortByCallTime"
            >
              <template #default="{ row }">
                {{ formatDateTime(row.call_time) }}
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户" width="120" />
            <el-table-column prop="call_error" label="描述信息" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="text" @click="viewErrorDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 系统日志 -->
    <div class="log-section">
      <h2>系统日志</h2>
      <div class="log-filters">
        <el-form :inline="true" :model="logFilters">
          <el-form-item label="日志级别">
            <el-select v-model="logFilters.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="全部" value="all" />
              <el-option label="正常" value="normal" />
              <el-option label="警告" value="warning" />
              <el-option label="错误" value="error" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="logFilters.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
            />
          </el-form-item>
          
          <el-form-item label="关键词">
            <el-input
              v-model="logFilters.search"
              placeholder="搜索描述信息"
              clearable
            />
          </el-form-item>
          
          <el-form-item>
            <el-button type="primary" @click="fetchSystemLogs">查询</el-button>
            <el-button @click="resetLogFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      
      <!-- 日志表格 -->
      <el-table 
        :data="sortedLogs" 
        style="width: 100%" 
        v-loading="logsLoading"
        @sort-change="handleLogsSortChange"
      >
        <el-table-column prop="sys_id" label="ID" width="80" sortable />
        <el-table-column 
          prop="log_time" 
          label="时间" 
          width="180"
          sortable="custom"
          :sort-orders="['ascending', 'descending']"
          :sort-by="sortByLogTime"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.log_time) }}
          </template>
        </el-table-column>
        <el-table-column prop="system_status" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status_type" size="small">
              {{ getStatusText(row.system_status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="error" label="描述信息">
          <template #default="{ row }">
            <div class="error-message" :title="row.error">
              {{ truncateText(row.error, 100) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="text" @click="viewLogDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="logFilters.page"
          v-model:page-size="logFilters.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="systemLogs.pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchSystemLogs"
          @current-change="fetchSystemLogs"
        />
      </div>
    </div>

    <!-- 错误详情对话框 -->
    <el-dialog
      v-model="detailDialog.visible"
      :title="detailDialog.title"
      width="600px"
    >
      <div class="detail-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item v-for="(value, key) in detailDialog.data" :key="key" :label="formatLabel(key)">
            <template v-if="key === 'call_error' || key === 'error'">
              <pre class="error-pre">{{ value }}</pre>
            </template>
            <template v-else-if="key.includes('time')">
              {{ formatDateTime(value) }}
            </template>
            <template v-else>
              {{ value || '-' }}
            </template>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialog.visible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  Refresh, 
  Monitor, 
  Connection, 
  Warning, 
  User 
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { API_BASE_URL } from '@/config';
import { adminAuthHeader } from '@/utils/adminToken';

// 响应式数据
const loading = ref(false)
const logsLoading = ref(false)
const chartRef = ref(null)
let chartInstance = null

// 排序相关变量（参考DataUpload.vue）
const sortField = ref('log_time') // 默认按日志时间排序
const sortOrder = ref('descending') // 默认按时间倒序，最新的在前面

const failuresSortField = ref('call_time') // 失败调用表的排序字段
const failuresSortOrder = ref('descending') // 默认按时间倒序

// API统计相关数据
const dateRange = ref([])
const apiStats = reactive({
  summary: {},
  timeSeries: [],
  recentFailures: [],
  responseTime: {}
})

// 系统健康概览
const healthOverview = reactive({
  systemStatus: 'healthy',
  statusColor: 'success',
  metrics: {}
})

// 系统日志相关数据
const logFilters = reactive({
  status: '',
  dateRange: [],
  search: '',
  page: 1,
  pageSize: 20
})

const systemLogs = reactive({
  logs: [],
  pagination: {
    page: 1,
    pageSize: 20,
    total: 0,
    totalPages: 0
  }
})

// 详情对话框
const detailDialog = reactive({
  visible: false,
  title: '',
  data: {}
})

// 初始化健康状态
const healthStatus = reactive({
  systemStatus: 'healthy',
  statusColor: 'success',
  systemStatusText: '运行正常'
})

// 计算属性：排序后的系统日志（参考DataUpload.vue）
const sortedLogs = computed(() => {
  let data = [...systemLogs.logs]
  
  // 根据排序字段和顺序进行排序
  if (sortField.value === 'log_time') {
    data.sort((a, b) => {
      const timeA = new Date(a.log_time).getTime()
      const timeB = new Date(b.log_time).getTime()
      
      if (sortOrder.value === 'ascending') {
        return timeA - timeB // 正序：时间早的在前
      } else {
        return timeB - timeA // 倒序：时间晚的在前
      }
    })
  }
  
  return data
})

// 计算属性：排序后的失败调用
const sortedFailures = computed(() => {
  if (!apiStats.recentFailures) return []
  
  let data = [...apiStats.recentFailures]
  
  // 根据排序字段和顺序进行排序
  if (failuresSortField.value === 'call_time') {
    data.sort((a, b) => {
      const timeA = new Date(a.call_time).getTime()
      const timeB = new Date(b.call_time).getTime()
      
      if (failuresSortOrder.value === 'ascending') {
        return timeA - timeB // 正序：时间早的在前
      } else {
        return timeB - timeA // 倒序：时间晚的在前
      }
    })
  }
  
  return data
})

// 生命周期钩子
onMounted(() => {
  initChart()
  fetchHealthOverview()
  fetchApiStats()
  fetchSystemLogs()
})

// 初始化图表
const initChart = () => {
  if (!chartRef.value) return
  
  chartInstance = echarts.init(chartRef.value)
  
  const resizeObserver = new ResizeObserver(() => {
    if (chartInstance) {
      chartInstance.resize()
    }
  })
  
  resizeObserver.observe(chartRef.value)
}

// 获取系统健康概览
const fetchHealthOverview = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/api/admin/log/health-overview`, {
      headers: adminAuthHeader()
    })
    const result = await response.json()
    
    if (result.success) {
      Object.assign(healthOverview, result.data)
      
      // 更新健康状态
      healthStatus.systemStatus = result.data.systemStatus
      healthStatus.statusColor = result.data.statusColor
      healthStatus.systemStatusText = getSystemStatusText(result.data.systemStatus)
    }
  } catch (error) {
    console.error('获取健康概览失败:', error)
    ElMessage.error('获取系统健康概览失败')
  }
}

// 获取API统计
const fetchApiStats = async () => {
  loading.value = true
  try {
    let url = `${API_BASE_URL}/api/admin/log/api-stats`
    const params = []
    
    if (dateRange.value && dateRange.value.length === 2) {
      const [start, end] = dateRange.value
      params.push(`startDate=${formatDate(start)}&endDate=${formatDate(end)}`)
    }
    
    if (params.length > 0) {
      url += `?${params.join('&')}`
    }
    
    const response = await fetch(url, {
      headers: adminAuthHeader()
    })
    const result = await response.json()
    
    if (result.success) {
      Object.assign(apiStats, result.data)
      renderChart()
    }
  } catch (error) {
    console.error('获取API统计失败:', error)
    ElMessage.error('获取API统计失败')
  } finally {
    loading.value = false
  }
}

// 获取系统日志
const fetchSystemLogs = async () => {
  logsLoading.value = true
  try {
    const params = []
    
    if (logFilters.status && logFilters.status !== 'all') {
      params.push(`status=${logFilters.status}`)
    }
    
    if (logFilters.dateRange && logFilters.dateRange.length === 2) {
      const [start, end] = logFilters.dateRange
      params.push(`startDate=${formatDate(start)}&endDate=${formatDate(end)}`)
    }
    
    if (logFilters.search) {
      params.push(`search=${encodeURIComponent(logFilters.search)}`)
    }
    
    params.push(`page=${logFilters.page}`)
    params.push(`pageSize=${logFilters.pageSize}`)
    
    const url = `${API_BASE_URL}/api/admin/log/system-logs?${params.join('&')}`
    const response = await fetch(url, {
      headers: adminAuthHeader()
    })
    const result = await response.json()
    
    if (result.success) {
      systemLogs.logs = result.data.logs
      systemLogs.pagination = result.data.pagination
    }
  } catch (error) {
    console.error('获取系统日志失败:', error)
    ElMessage.error('获取系统日志失败')
  } finally {
    logsLoading.value = false
  }
}

// 渲染图表
const renderChart = () => {
  if (!chartInstance || !apiStats.timeSeries) return
  
  // 修复日期排序：按时间从早到晚排序
  const sortedTimeSeries = [...apiStats.timeSeries].sort((a, b) => {
    return new Date(a.date) - new Date(b.date)
  })

  const dates = sortedTimeSeries.map(item => item.date)
  const successData = sortedTimeSeries.map(item => item.success)
  const failedData = sortedTimeSeries.map(item => item.failed)
  
  const option = {
    title: {
      text: 'API调用趋势图',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        let result = `${params[0].axisValue}<br/>`
        params.forEach(param => {
          result += `${param.seriesName}: ${param.value}<br/>`
        })
        return result
      }
    },
    legend: {
      data: ['成功', '失败'],
      top: 10,
      right: '10%'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '调用次数'
    },
    series: [
      {
        name: '成功',
        type: 'bar',
        data: successData,
        itemStyle: {
          color: '#67C23A'
        },
        barWidth: '40%'
      },
      {
        name: '失败',
        type: 'bar',
        data: failedData,
        itemStyle: {
          color: '#F56C6C'
        },
        barWidth: '40%'
      }
    ]
  }
  
  chartInstance.setOption(option)
}

// 查看错误详情
const viewErrorDetail = (row) => {
  detailDialog.title = '失败调用详情'
  detailDialog.data = row
  detailDialog.visible = true
}

// 查看日志详情
const viewLogDetail = (row) => {
  detailDialog.title = '系统日志详情'
  detailDialog.data = row
  detailDialog.visible = true
}

// 刷新数据
const refreshData = () => {
  fetchHealthOverview()
  fetchApiStats()
  fetchSystemLogs()
}

// 重置日期范围
const resetDateRange = () => {
  dateRange.value = []
  fetchApiStats()
}

// 重置日志筛选
const resetLogFilters = () => {
  logFilters.status = ''
  logFilters.dateRange = []
  logFilters.search = ''
  logFilters.page = 1
  fetchSystemLogs()
}

// 系统日志排序处理方法（参考DataUpload.vue）
const handleLogsSortChange = ({prop, order }) => {
  if (prop === 'log_time') {
    sortField.value = prop
    sortOrder.value = order || 'descending'
  }
}

// 失败调用排序处理方法
const handleFailuresSortChange = ({prop, order }) => {
  if (prop === 'call_time') {
    failuresSortField.value = prop
    failuresSortOrder.value = order || 'descending'
  }
}

// 系统日志时间排序方法（参考DataUpload.vue）
const sortByLogTime = (row) => {
  return new Date(row.log_time).getTime()
}

// 失败调用时间排序方法
const sortByCallTime = (row) => {
  return new Date(row.call_time).getTime()
}

// 工具函数
const formatDate = (date) => {
  if (!date) return ''
  // 使用本地时区格式化，避免 toISOString()（UTC）导致日期偏移一天
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const formatDateTime = (dateTime) => {
  if (!dateTime) return ''
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN')
}

const truncateText = (text, maxLength) => {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

const getStatusText = (status) => {
  const statusMap = {
    'normal': '正常',
    'warning': '警告',
    'error': '错误',
    'success': '成功',
    'failed': '失败'
  }
  return statusMap[status] || status
}

const getSystemStatusText = (status) => {
  const textMap = {
    'healthy': '运行正常',
    'warning': '运行警告',
    'critical': '运行异常'
  }
  return textMap[status] || status
}

const formatLabel = (key) => {
  const labelMap = {
    'call_id': '调用ID',
    'user_id': '用户ID',
    'username': '用户名',
    'call_status': '调用状态',
    'call_time': '调用时间',
    'call_error': '错误信息',
    'sys_id': '日志ID',
    'system_status': '系统状态',
    'log_time': '日志时间',
    'error': '描述信息',
    'status_type': '状态类型'
  }
  return labelMap[key] || key
}
</script>

<style scoped>
.admin-log-container {
  padding: 20px;
  background-color: var(--bg-soft);
  min-height: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  color: var(--text-strong);
}

.overview-section {
  margin-bottom: 30px;
}

.overview-section h2 {
  margin-bottom: 15px;
  color: var(--text-strong);
  font-size: var(--text-xl);
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
}

.overview-card {
  transition: transform 0.3s;
}

.overview-card:hover {
  transform: translateY(-5px);
}

.card-content {
  display: flex;
  align-items: center;
}

.card-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: var(--text-2xl);
  color: white;
}

.card-icon.success {
  background-color: var(--success);
}

.card-icon.warning {
  background-color: var(--warning);
}

.card-icon.danger {
  background-color: var(--danger);
}

.card-icon.primary {
  background-color: var(--brand);
}

.card-info {
  flex: 1;
}

.card-title {
  font-size: var(--text-base);
  color: var(--text-muted);
  margin-bottom: 5px;
}

.card-value {
  font-size: var(--text-2xl);
  font-weight: bold;
  color: var(--text-strong);
}

.chart-section {
  background: var(--bg-surface);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 30px;
  box-shadow: var(--shadow-base);
}

.chart-section h2 {
  margin-bottom: 15px;
  color: var(--text-strong);
  font-size: var(--text-xl);
}

.chart-filters {
  margin-bottom: 20px;
}

.chart-wrapper {
  margin-bottom: 20px;
}

.api-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin-bottom: 20px;
  padding: 15px;
  background-color: var(--bg-soft);
  border-radius: 6px;
}

.detail-item {
  display: flex;
  flex-direction: column;
}

.detail-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
  margin-bottom: 4px;
}

.detail-value {
  font-size: var(--text-lg);
  font-weight: bold;
  color: var(--text-strong);
}

.detail-value.success {
  color: var(--success);
}

.detail-value.danger {
  color: var(--danger);
}

.recent-failures {
  margin-top: 20px;
}

.recent-failures h3 {
  margin-bottom: 10px;
  color: var(--text-strong);
  font-size: var(--text-lg);
}

.log-section {
  background: var(--bg-surface);
  border-radius: 8px;
  padding: 20px;
  box-shadow: var(--shadow-base);
}

.log-section h2 {
  margin-bottom: 15px;
  color: var(--text-strong);
  font-size: var(--text-xl);
}

.log-filters {
  margin-bottom: 20px;
}

.error-message {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: help;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.detail-content {
  max-height: 400px;
  overflow-y: auto;
}

.error-pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  background-color: var(--bg-hover);
  padding: 10px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: var(--text-xs);
  max-height: 200px;
  overflow-y: auto;
}
</style>