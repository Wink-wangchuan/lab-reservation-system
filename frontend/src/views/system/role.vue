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

      <el-table v-loading="loading" :data="records" stripe border>
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
import { ref, nextTick, onMounted } from 'vue'
import { rolePage, roleAdd, roleEdit, roleDelete, roleStatus, rolePerms, roleAssignPerms, permTree } from '@/api'

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

onMounted(load)
</script>

<style scoped>
.perm-tree-wrap {
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 2px;
}
</style>