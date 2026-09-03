<template>
  <div class="admin-user-container">
    <div class="header">
      <h2>用户管理</h2>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名或邮箱"
          style="width: 300px; margin-right: 10px;"
          @input="searchUsers"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="refreshUsers">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <el-divider />

    <div class="user-table">
      <el-table
        :data="userList"
        v-loading="loading"
        stripe
        style="width: 100%"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'primary'">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="register_time" label="注册时间" width="180"
        sortable :sort-orders="['ascending', 'descending']" :sort-by="sortByRegisterTime">
          <template #default="{ row }">
            {{ formatDate(row.register_time) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row, $index }">
            <!-- 重置密码按钮 这个对话框要调整-->
            <el-popconfirm
              title="确定将密码重置为666666吗？"
              @confirm="resetPassword(row.user_id)"
            >
              <template #reference>
                <el-button
                  size="small"
                  type="warning"
                  link
                  style="margin-left: 8px;"
                  v-if="row.user_id !== currentUserId && row.role !== 'admin'"
                >
                  <el-icon><Key /></el-icon>重置密码
                </el-button>
              </template>
            </el-popconfirm>
            <!-- 删除按钮 -->
            <el-popconfirm
              title="确定要删除这个用户吗？"
              @confirm="deleteUser(row.user_id, $index)"
            >
              <template #reference>
                <el-button
                  size="small"
                  type="danger"
                  link
                  v-if="row.user_id !== currentUserId && row.role !== 'admin'"
                >
                  <el-icon><Delete /></el-icon>删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 30, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalUsers"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage} from 'element-plus'
import {
  Search,
  Refresh,
  Delete,
  Key
} from '@element-plus/icons-vue'
import axios from 'axios'
import { API_BASE_URL } from '@/config';

// 状态管理
const loading = ref(false)
const searchKeyword = ref('')
const userList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const totalUsers = ref(0)
const currentUserId = ref(1) // 假设当前管理员ID为1，实际应从登录信息获取
// 在状态管理部分添加排序相关状态
const sortField = ref('register_time')
const sortOrder = ref('descending') // 默认降序，最新的在前

// 初始化加载用户列表
onMounted(() => {
  fetchUsers()
})

// 获取用户列表
const fetchUsers = async () => {
  loading.value = true
  try {
    const response = await axios.get(`${API_BASE_URL}/api/admin/users`, {
      params: {
        page: currentPage.value,
        pageSize: pageSize.value,
        keyword: searchKeyword.value,
        sortField: sortField.value,
        sortOrder: sortOrder.value
      }
    })
    
    if (response.data.success) {
      userList.value = response.data.data.users
      totalUsers.value = response.data.data.pagination.total
    } else {
      ElMessage.error(response.data.message || '获取用户列表失败')
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

// 添加排序方法
const sortByRegisterTime = (a, b) => {
  const dateA = new Date(a.register_time).getTime()
  const dateB = new Date(b.register_time).getTime()
  return sortOrder.value === 'ascending' ? dateA - dateB : dateB - dateA
}

// 处理排序变化
const handleSortChange = ({ prop, order }) => {
  if (prop === 'register_time') {
    sortField.value = prop
    sortOrder.value = order
    fetchUsers()
  }
}

// 搜索用户
const searchUsers = () => {
  currentPage.value = 1
  fetchUsers()
}

// 刷新用户列表
const refreshUsers = () => {
  searchKeyword.value = ''
  currentPage.value = 1
  fetchUsers()
}

// 分页大小变化
const handleSizeChange = (val) => {
  pageSize.value = val
  fetchUsers()
}

// 当前页变化
const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchUsers()
}

// 重置密码方法
const resetPassword = async (userId) => {
  try {
    // 调用重置密码接口
    const response = await axios.patch(`${API_BASE_URL}/api/admin/users/${userId}/reset-password`);
    
    if (response.data.success) {
      ElMessage({
        type: 'success',
        message: response.data.message,
        duration: 3000,
        showClose: true
      });
      
      // 记录操作日志（可选）
      console.log(`管理员已重置用户 ${userId} 的密码`);
    } else {
      ElMessage.error(response.data.message || '重置密码失败');
    }
  } catch (error) {
    if (error !== 'cancel') { // 如果不是用户取消操作
      console.error('重置密码失败:', error);
      ElMessage.error('重置密码失败');
    }
  }
};

// 删除用户
const deleteUser = async (userId) => {
  try {
    const response = await axios.delete(`${API_BASE_URL}/api/admin/users/${userId}`)
    
    if (response.data.success) {
      ElMessage.success('用户删除成功')
      fetchUsers()
    } else {
      ElMessage.error(response.data.message || '删除失败')
    }
  } catch (error) {
    console.error('删除用户失败:', error)
    ElMessage.error('删除失败')
  }
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.admin-user-container {
  padding: 20px;
  background-color: var(--bg-surface);
  border-radius: 8px;
  min-height: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-table {
  margin-bottom: 20px;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.el-button--link {
  padding: 0;
  height: auto;
}
</style>