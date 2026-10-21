<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><User /></el-icon>
          用户管理
        </div>
        <div class="toolbar-right">
          <el-input v-model="query.keyword" placeholder="搜索账号/姓名" clearable style="width: 190px" @keyup.enter="load" @clear="load" />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="load">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
            <el-option label="待审核" :value="2" />
          </el-select>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button type="primary" @click="openEdit()"><el-icon><Plus /></el-icon> 新增用户</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="账号" prop="username" width="130" />
        <el-table-column label="姓名" prop="realName" width="110" />
        <el-table-column label="手机号" prop="phone" width="130" />
        <el-table-column label="邮箱" prop="email" min-width="260" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="r in (row.roleNames || [])" :key="r" class="role-tag" type="primary">{{ r }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <!-- 待审核无法用开关表达，改用标签，审核入口放在操作列 -->
            <el-tag v-if="row.status === 2" type="warning">待审核</el-tag>
            <el-switch
              v-else
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 2" link type="success" @click="doApprove(row)">通过审核</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="resetPwd(row)">重置密码</el-button>
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" class="form-responsive" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="登录账号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :label="0">男</el-radio>
            <el-radio :label="1">女</el-radio>
            <el-radio :label="2">保密</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="状态">
          <!-- 待审核账号不在此处改状态，避免被误改成启用/禁用 -->
          <el-tag v-if="form.status === 2" type="warning">待审核（请在列表中点击「通过审核」）</el-tag>
          <el-switch
            v-else
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="selectedRoleIds" multiple placeholder="分配角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 注册审核：通过的同时为该用户分配角色（注册时仅预绑默认的「学生」角色） -->
    <el-dialog v-model="approveVisible" title="通过注册审核" width="480px" :close-on-click-modal="false">
      <div v-if="approveTarget" class="approve-box">
        <div class="approve-line"><span class="approve-label">账号</span>{{ approveTarget.username }}</div>
        <div class="approve-line"><span class="approve-label">姓名</span>{{ approveTarget.realName }}</div>
        <div class="approve-line"><span class="approve-label">手机号</span>{{ approveTarget.phone || '-' }}</div>
      </div>
      <el-form class="form-responsive" label-width="90px">
        <el-form-item label="分配角色" required>
          <el-select v-model="approveForm.roleIds" multiple placeholder="请选择该账号的角色（至少一个）" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <div class="approve-tip">角色决定该账号登录后可见的菜单与可调用的接口，请确认后再通过审核。</div>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" :loading="approving" @click="submitApprove">通过审核并分配角色</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userPage, userAdd, userEdit, userDelete, userStatus, userResetPwd, userAssignRoles, userApprove, roleList } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, keyword: '', status: null })

const roles = ref([])
const editVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = ref({})
const selectedRoleIds = ref([])
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

// 注册审核弹窗：审核通过时必须为该账号指定角色
const approveVisible = ref(false)
const approving = ref(false)
const approveTarget = ref(null)
const approveForm = ref({ roleIds: [] })

async function load() {
  loading.value = true
  try {
    const data = await userPage({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roles.value = await roleList()
}

function openEdit(row) {
  if (row) {
    form.value = { id: row.id, username: row.username, realName: row.realName, gender: row.gender ?? 0, phone: row.phone, email: row.email, status: row.status, password: '' }
    selectedRoleIds.value = row.roleIds ? [...row.roleIds] : []
  } else {
    form.value = { username: '', password: '123456', realName: '', gender: 0, phone: '', email: '', status: 1 }
    selectedRoleIds.value = []
  }
  editVisible.value = true
}

async function doSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.value.id) {
        await userEdit(form.value)
        if (selectedRoleIds.value.length) {
          await userAssignRoles(form.value.id, selectedRoleIds.value)
        }
      } else {
        const data = await userAdd(form.value)
        // 可能返回新用户id
        const id = data?.id || form.value.id
        if (id && selectedRoleIds.value.length) {
          await userAssignRoles(id, selectedRoleIds.value)
        }
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
  await userStatus(row.id, v)
  ElMessage.success(v === 1 ? '已启用' : '已禁用')
}

/**
 * 打开注册审核弹窗：默认选中该账号注册时预绑的角色（通常为学生），
 * 若没有预绑角色则回退到「学生」角色，管理员可直接确认或改选
 */
function doApprove(row) {
  approveTarget.value = row
  const bound = row.roleIds && row.roleIds.length ? [...row.roleIds] : []
  const fallback = roles.value.find((r) => r.roleCode === 'STUDENT')
  approveForm.value = { roleIds: bound.length ? bound : (fallback ? [fallback.id] : []) }
  approveVisible.value = true
}

async function submitApprove() {
  if (!approveTarget.value) return
  if (!approveForm.value.roleIds.length) {
    ElMessage.warning('请至少为该账号分配一个角色')
    return
  }
  approving.value = true
  try {
    await userApprove(approveTarget.value.id, approveForm.value.roleIds)
    ElMessage.success('已通过审核并完成角色分配')
    approveVisible.value = false
    load()
  } finally {
    approving.value = false
  }
}

function resetPwd(row) {
  ElMessageBox.prompt('请输入新密码（留空则随机生成）', '重置密码', {
    confirmButtonText: '确认重置', cancelButtonText: '取消'
  }).then(async ({ value }) => {
    await userResetPwd(row.id, value)
    ElMessage.success('密码已重置' + (value ? '' : '（已随机生成）'))
  }).catch(() => {})
}

function doDelete(row) {
  ElMessageBox.confirm(`确认删除用户「${row.realName}」吗？`, '警告', { type: 'warning' }).then(async () => {
    await userDelete(row.id)
    ElMessage.success('已删除')
    load()
  }).catch(() => {})
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<style scoped>
.role-tag {
  margin-right: 6px;
}

.approve-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
  border-radius: 10px;
  border: 1px solid var(--hairline);
  background: var(--card-bg-soft);
  font-size: 13px;
  color: var(--ink-1);
}

.approve-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.approve-label {
  color: var(--ink-3);
  min-width: 48px;
}

.approve-tip {
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

@media (max-width: 640px) {
  .approve-box {
    font-size: 12px;
  }
}
</style>