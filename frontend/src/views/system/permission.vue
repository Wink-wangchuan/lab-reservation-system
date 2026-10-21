<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Lock /></el-icon>
          权限管理
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openEdit()"><el-icon><Plus /></el-icon> 新增顶级权限</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="flatTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="true"
        border
      >
        <el-table-column label="权限名称" prop="permName" min-width="200" />
        <el-table-column label="权限编码" prop="permCode" min-width="200" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.permType === 1 ? 'primary' : 'success'">{{ row.permType === 1 ? '菜单' : '按钮' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" prop="path" min-width="160" />
        <el-table-column label="图标" prop="icon" width="90" />
        <el-table-column label="排序" prop="sort" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.permType === 1" link type="warning" @click="openEdit(null, row)">加子项</el-button>
            <el-button link type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑权限' : '新增权限'" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" class="form-responsive" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级">
          <el-input :model-value="parentLabel" disabled placeholder="顶级" />
        </el-form-item>
        <el-form-item label="名称" prop="permName">
          <el-input v-model="form.permName" placeholder="权限名称" />
        </el-form-item>
        <el-form-item label="编码" prop="permCode">
          <el-input v-model="form.permCode" placeholder="如：system:user:add" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.permType">
            <el-radio :label="1">菜单</el-radio>
            <el-radio :label="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="如：/system/user" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="图标名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { permTree, permAdd, permEdit, permDelete } from '@/api'

const loading = ref(false)
const flatTree = ref([])

const editVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = ref({})
const parentLabel = ref('顶级')
const rules = {
  permName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  permCode: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    flatTree.value = await permTree()
  } finally {
    loading.value = false
  }
}

function openEdit(row, parent) {
  if (row) {
    form.value = { id: row.id, parentId: row.parentId, permName: row.permName, permCode: row.permCode, permType: row.permType, path: row.path, icon: row.icon, sort: row.sort, status: row.status }
    parentLabel.value = '顶级'
  } else if (parent) {
    form.value = { parentId: parent.id, permName: '', permCode: '', permType: 2, path: '', icon: '', sort: 0, status: 1 }
    parentLabel.value = parent.permName
  } else {
    form.value = { parentId: 0, permName: '', permCode: '', permType: 1, path: '', icon: '', sort: 0, status: 1 }
    parentLabel.value = '顶级'
  }
  editVisible.value = true
}

function doSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.value.id) {
        await permEdit(form.value)
      } else {
        await permAdd(form.value)
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

function doDelete(row) {
  ElMessageBox.confirm(`确认删除权限「${row.permName}」吗？`, '警告', { type: 'warning' }).then(async () => {
    await permDelete(row.id)
    ElMessage.success('已删除')
    load()
  }).catch(() => {})
}

onMounted(load)
</script>