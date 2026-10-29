<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Avatar /></el-icon>
          角色管理
        </div>
        <div class="toolbar-right">
          <el-input v-model="query.keyword" placeholder="搜索角色" clearable style="width: 180px" @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button type="primary" @click="openEdit()"><el-icon><Plus /></el-icon> 新增角色</el-button>
        </div>
      </div>

      <el-table v-if="!isMobile" v-loading="loading" :data="records" stripe border>
        <el-table-column label="角色编码" prop="roleCode" width="160" />
        <el-table-column label="角色名称" prop="roleName" width="160" />
        <el-table-column label="备注" prop="remark" min-width="200" />
        <el-table-column label="排序" prop="sort" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="(v) => toggleStatus(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="openPerms(row)">分配权限</el-button>
            <el-button link type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 窄屏改用卡片列表：固定操作列会把角色名称和备注挤出可视区 -->
      <div v-else v-loading="loading" class="role-cards">
        <div v-for="row in records" :key="row.id" class="role-card">
          <div class="role-card__head">
            <div class="role-card__title">
              <span class="role-card__name">{{ row.roleName || '-' }}</span>
              <code class="role-card__code">{{ row.roleCode }}</code>
            </div>
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              size="small"
              @change="(v) => toggleStatus(row, v)"
            />
          </div>
          <div class="role-card__meta">排序 {{ row.sort ?? 0 }}</div>
          <div class="role-card__remark" :class="{ 'is-empty': !row.remark }">{{ row.remark || '暂无备注' }}</div>
          <div class="role-card__actions">
            <el-button size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" plain @click="openPerms(row)">分配权限</el-button>
            <el-button size="small" type="danger" plain @click="doDelete(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !records.length" description="暂无角色" :image-size="80" />
      </div>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑角色' : '新增角色'" width="480px" :close-on-click-modal="false">
      <el-form ref="formRef" class="form-responsive" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如：TEACHER" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：教师" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="permVisible" :title="`分配权限 - ${currentRole?.roleName || ''}`" width="520px" :close-on-click-modal="false">
      <div class="perm-tree-wrap">
        <el-tree
          ref="permTreeRef"
          :data="permTreeData"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="{ label: 'permName', children: 'children' }"
        />
      </div>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPerms" @click="savePerms">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { rolePage, roleAdd, roleEdit, roleDelete, roleStatus, rolePerms, roleAssignPerms, permTree } from '@/api'

// 窄屏（<=640px）用卡片列表替代宽表格，避免固定操作列把内容挤出可视区
const isMobile = ref(window.innerWidth <= 640)
function trackWidth() {
  isMobile.value = window.innerWidth <= 640
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, keyword: '' })

const editVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = ref({})
const rules = {
  roleCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const permVisible = ref(false)
const permTreeRef = ref()
const permTreeData = ref([])
const currentRole = ref({})
const savingPerms = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await rolePage({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  if (row) {
    form.value = { id: row.id, roleCode: row.roleCode, roleName: row.roleName, sort: row.sort ?? 0, status: row.status, remark: row.remark }
  } else {
    form.value = { roleCode: '', roleName: '', sort: 0, status: 1, remark: '' }
  }
  editVisible.value = true
}

function doSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.value.id) {
        await roleEdit(form.value)
      } else {
        await roleAdd(form.value)
      }
      ElMessage.success('保存成功')
      editVisible.value = false
      load()
    } catch {
    } finally {
      saving.value = false
    }
  })
}

async function toggleStatus(row, v) {
  await roleStatus(row.id, v)
  ElMessage.success(v === 1 ? '已启用' : '已禁用')
}

async function openPerms(row) {
  currentRole.value = row
  permVisible.value = true
  const [tree, hasIds] = await Promise.all([permTree(), rolePerms(row.id)])
  permTreeData.value = tree
  await nextTick()
  // 仅勾选叶子节点
  permTreeRef.value.setCheckedKeys(hasIds)
}

async function savePerms() {
  const checked = permTreeRef.value.getCheckedKeys()
  const half = permTreeRef.value.getHalfCheckedKeys()
  savingPerms.value = true
  try {
    await roleAssignPerms(currentRole.value.id, [...checked, ...half])
    ElMessage.success('权限已保存')
    permVisible.value = false
  } catch {
  } finally {
    savingPerms.value = false
  }
}

function doDelete(row) {
  ElMessageBox.confirm(`确认删除角色「${row.roleName}」吗？`, '警告', { type: 'warning' }).then(async () => {
    await roleDelete(row.id)
    ElMessage.success('已删除')
    load()
  }).catch(() => {})
}

onMounted(() => {
  load()
  window.addEventListener('resize', trackWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', trackWidth)
})
</script>

<style scoped lang="scss">
.perm-tree-wrap {
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 2px;
}

/* ===== 窄屏卡片列表 ===== */
.role-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
  margin-top: 4px;
}

.role-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--card-bg-soft);
}

.role-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.role-card__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.role-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
  overflow-wrap: anywhere;
}

/* 角色编码用等宽字体小标签，避免和名称混在一起 */
.role-card__code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-2);
  background: var(--card-bg);
  border: 1px solid var(--hairline);
  border-radius: 6px;
  padding: 0 6px;
  overflow-wrap: anywhere;
}

.role-card__meta {
  font-size: 12px;
  color: var(--ink-3);
}

.role-card__remark {
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--ink-2);
  overflow-wrap: anywhere;

  &.is-empty {
    color: var(--ink-3);
  }
}

.role-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 2px;

  .el-button {
    width: 100%;
    margin-left: 0 !important;
  }
}
</style>