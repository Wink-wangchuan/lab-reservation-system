<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><OfficeBuilding /></el-icon>
          实验室管理
        </div>
        <div class="toolbar-right">
          <el-input v-model="query.labName" placeholder="按名称搜索" clearable style="width: 180px" @keyup.enter="load" @clear="load" />
          <el-select v-model="query.labType" placeholder="类型" clearable style="width: 140px" @change="load">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button type="primary" @click="openEdit()"><el-icon><Plus /></el-icon> 新增实验室</el-button>
        </div>
      </div>

      <el-table v-if="!isMobile" v-loading="loading" :data="records" stripe border>
        <el-table-column label="编码" prop="labCode" width="110" />
        <el-table-column label="名称" prop="labName" min-width="180" />
        <el-table-column label="类型" prop="labType" width="140" />
        <el-table-column label="位置" prop="location" width="150" />
        <el-table-column label="容量" prop="capacity" width="80" align="center" />
        <el-table-column label="设备描述" prop="equipmentDesc" min-width="340" />
        <el-table-column label="开放预约" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.openStatus" :active-value="1" :inactive-value="0" @change="(v) => toggleOpen(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="启用预约" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enableReserve" :active-value="1" :inactive-value="0" @change="(v) => toggleReserve(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="故障锁定" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.deviceFault" type="danger">已锁定</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 窄屏改用卡片列表：列多且操作列固定，横向滚动会把名称与设备描述挤到不可读 -->
      <div v-else v-loading="loading" class="lab-cards">
        <div v-for="row in records" :key="row.id" class="lab-card">
          <div class="lab-card__head">
            <div class="lab-card__id">
              <span class="lab-card__name">{{ row.labName }}</span>
              <span class="lab-card__code">{{ row.labCode }}</span>
            </div>
            <el-tag v-if="row.deviceFault" type="danger" size="small">故障锁定</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </div>
          <div class="lab-card__meta">
            <span v-if="row.labType">{{ row.labType }}</span>
            <span v-if="row.location">位置：{{ row.location }}</span>
            <span>容量：{{ row.capacity }} 人</span>
          </div>
          <div v-if="row.equipmentDesc" class="lab-card__desc">{{ row.equipmentDesc }}</div>
          <div class="lab-card__switches">
            <div class="lab-card__switch">
              <span>开放预约</span>
              <el-switch v-model="row.openStatus" :active-value="1" :inactive-value="0" size="small" @change="(v) => toggleOpen(row, v)" />
            </div>
            <div class="lab-card__switch">
              <span>启用预约</span>
              <el-switch v-model="row.enableReserve" :active-value="1" :inactive-value="0" size="small" @change="(v) => toggleReserve(row, v)" />
            </div>
          </div>
          <div class="lab-card__actions">
            <el-button size="small" type="primary" plain @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" size="small" type="danger" plain @click="doDelete(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !records.length" description="暂无实验室" :image-size="80" />
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑实验室' : '新增实验室'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" class="form-responsive" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="labName">
          <el-input v-model="form.labName" placeholder="如：软件工程实验室" />
        </el-form-item>
        <el-form-item label="编码" prop="labCode">
          <el-input v-model="form.labCode" placeholder="如：LAB-SE-101" />
        </el-form-item>
        <el-form-item label="类型" prop="labType">
          <el-select v-model="form.labType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="如：实验楼 A 区 3 楼" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="500" />
          <span class="unit">人</span>
        </el-form-item>
        <el-form-item label="负责人" prop="managerId">
          <el-select v-model="form.managerId" placeholder="选择负责人" clearable style="width: 100%">
            <el-option v-for="u in managers" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备描述">
          <el-input v-model="form.equipmentDesc" type="textarea" :rows="2" />
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { labPage, labAdd, labEdit, labDelete, labOpen, labReserve, userOptions } from '@/api'
import { useUserStore } from '@/store/user'

// 窄屏（<=640px）用卡片列表替代宽表格，避免固定操作列把其它列挤出可视区
const isMobile = ref(window.innerWidth <= 640)
function trackWidth() {
  isMobile.value = window.innerWidth <= 640
}

const userStore = useUserStore()
// 删除实验室需要 system:lab:delete，实验室管理员没有该权限 —— 无权限时不渲染删除按钮
const canDelete = computed(() => userStore.hasPerm('system:lab:delete'))

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, labName: '', labType: '' })
// 实验室类型词表：需覆盖库中全部 lab_type 取值，否则筛选与编辑弹窗选不中已有数据
const types = [
  '计算机实验室', '机房', '软件实训室', '网络实训室', '人工智能实验室', '语音实训室',
  '物理实验室', '电子实训室', '电气实训室', '化学实验室', '生物实验室', '智能制造训练室', '其他'
]
const managers = ref([])

// 弹窗
const editVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = ref({})
const rules = {
  labName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  labCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  labType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await labPage({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadManagers() {
  // 负责人下拉只需要 id + 姓名，改用精简接口：实验室管理员无需「用户管理」权限
  managers.value = await userOptions()
}

async function openEdit(row) {
  if (row) {
    form.value = { ...row }
  } else {
    form.value = { labName: '', labCode: '', labType: '', location: '', capacity: 40, managerId: null, equipmentDesc: '', remark: '' }
  }
  if (!managers.value.length) await loadManagers()
  editVisible.value = true
}

function doSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.value.id) {
        await labEdit(form.value)
      } else {
        await labAdd(form.value)
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

async function toggleOpen(row, v) {
  await labOpen(row.id, v)
  ElMessage.success(v === 1 ? '已开放预约' : '已关闭预约')
}

async function toggleReserve(row, v) {
  await labReserve(row.id, v)
  ElMessage.success(v === 1 ? '已启用预约' : '已停用预约')
}

function doDelete(row) {
  ElMessageBox.confirm(`确认删除实验室「${row.labName}」吗？`, '警告', { type: 'warning' }).then(async () => {
    await labDelete(row.id)
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
/* ===== 窄屏卡片列表 ===== */
.lab-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
  margin-top: 4px;
}

.lab-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--card-bg-soft);
}

.lab-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.lab-card__id {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.lab-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lab-card__code {
  font-size: 12px;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

.lab-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 12px;
  color: var(--ink-2);
}

.lab-card__meta > span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.lab-card__desc {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

.lab-card__switches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  padding-top: 2px;
}

.lab-card__switch {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-2);
}

.lab-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 2px;
}

.lab-card__actions .el-button {
  width: 100%;
  margin-left: 0 !important;
}

/* 无删除权限时只剩一个按钮，占满整行避免半格留白 */
.lab-card__actions .el-button:only-child {
  grid-column: span 2;
}
</style>