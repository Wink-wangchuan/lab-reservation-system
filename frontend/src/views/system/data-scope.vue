<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Lock /></el-icon>
          数据权限
        </div>
        <div class="toolbar-right">
          <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable style="width: 180px"
            @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="为管理员/教师限定可见实验室：配置后其预约、报修、实验室台账只显示授权范围内的数据；不配置表示不限制（超级管理员始终不受限）。"
        class="tip"
      />

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="用户名" prop="username" width="130" />
        <el-table-column label="姓名" prop="realName" width="110" />
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">{{ roleNames(row) }}</template>
        </el-table-column>
        <el-table-column label="数据范围" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="scopes[row.id]" type="success" size="small">已限定 {{ scopes[row.id].length }} 个实验室</el-tag>
            <el-tag v-else type="info" size="small">不限制</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openScope(row)">设置范围</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="scopeVisible" :title="`数据范围 · ${currentUser?.realName || ''}`" width="520px">
      <el-select v-model="selectedLabs" multiple filterable placeholder="留空表示不限制（可见全部实验室）" style="width: 100%">
        <el-option v-for="l in labs" :key="l.id" :label="`${l.labName}（${l.labCode || l.location || '-'}）`" :value="l.id" />
      </el-select>
      <div class="scope-tip">清空即取消限制；超级管理员无论配置如何都不受限。</div>
      <template #footer>
        <el-button @click="scopeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveScope">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { userPage, labList, userLabs, userAssignLabs } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })
const scopes = ref({})

const labs = ref([])
const scopeVisible = ref(false)
const currentUser = ref(null)
const selectedLabs = ref([])
const saving = ref(false)

function roleNames(row) {
  return (row.roleNames || row.roles?.map((r) => r.roleName) || []).join('、') || '-'
}

async function load() {
  loading.value = true
  try {
    const data = await userPage({
      current: query.current,
      size: query.size,
      keyword: query.keyword || undefined
    })
    records.value = data.records || []
    total.value = data.total || 0
    // 逐行拉取数据范围用于展示（用户量不大，串行可接受）
    const next = {}
    await Promise.all(records.value.map(async (u) => {
      try {
        const ids = await userLabs(u.id)
        if (ids?.length) next[u.id] = ids
      } catch {
        // 单行失败不阻塞列表
      }
    }))
    scopes.value = next
  } finally {
    loading.value = false
  }
}

async function openScope(row) {
  currentUser.value = row
  if (!labs.value.length) {
    labs.value = (await labList()) || []
  }
  selectedLabs.value = (await userLabs(row.id)) || []
  scopeVisible.value = true
}

async function saveScope() {
  if (!currentUser.value) return
  saving.value = true
  try {
    await userAssignLabs(currentUser.value.id, selectedLabs.value)
    ElMessage.success('数据范围已更新')
    const next = { ...scopes.value }
    if (selectedLabs.value.length) next[currentUser.value.id] = [...selectedLabs.value]
    else delete next[currentUser.value.id]
    scopes.value = next
    scopeVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.tip {
  margin-bottom: 12px;
}

.scope-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
