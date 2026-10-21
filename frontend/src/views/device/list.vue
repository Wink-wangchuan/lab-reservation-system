<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Monitor /></el-icon>
          设备台账
        </div>
        <div class="toolbar-right">
          <el-input v-model="query.keyword" placeholder="搜索名称/编码" clearable style="width: 180px" @keyup.enter="load" @clear="load" />
          <el-select v-model="query.labId" placeholder="所属实验室" clearable style="width: 160px" @change="load">
            <el-option v-for="l in labs" :key="l.id" :label="l.labName" :value="l.id" />
          </el-select>
          <el-select v-model="query.faultStatus" placeholder="故障状态" clearable style="width: 130px" @change="load">
            <el-option label="正常" :value="0" />
            <el-option label="故障" :value="1" />
          </el-select>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button type="primary" @click="openEdit()"><el-icon><Plus /></el-icon> 新增设备配置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="编码" prop="deviceCode" width="250" />
        <el-table-column label="名称" prop="deviceName" min-width="150" />
        <el-table-column label="类型" prop="deviceType" width="120" />
        <el-table-column label="数量" width="90" align="center">
          <template #default="{ row }">{{ row.quantity || 1 }}</template>
        </el-table-column>
        <el-table-column label="所属实验室" prop="labName" width="170" />
        <el-table-column label="品牌型号" prop="brandModel" min-width="270" />
        <el-table-column label="采购日期" prop="purchaseDate" width="110" />
        <el-table-column label="故障" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.faultStatus === 1" type="danger">故障</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="故障描述" prop="faultDesc" min-width="220" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'primary' : 'info'">{{ row.status === 1 ? '在用' : row.status === 2 ? '停用' : '报废' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑设备配置' : '新增设备配置'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" class="form-responsive" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="deviceCode">
          <el-input v-model="form.deviceCode" placeholder="如：DEV-101-001 ~ DEV-101-060" />
        </el-form-item>
        <el-form-item label="名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="如：教学台式机" />
        </el-form-item>
        <el-form-item label="类型" prop="deviceType">
          <el-select v-model="form.deviceType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in deviceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="所属实验室" prop="labId">
          <el-select v-model="form.labId" placeholder="选择实验室" style="width: 100%">
            <el-option v-for="l in labs" :key="l.id" :label="l.labName" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="品牌型号">
          <el-input v-model="form.brandModel" placeholder="如：联想 ThinkCentre M760" />
        </el-form-item>
        <el-form-item label="采购日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1000" style="width: 200px" />
          <span class="unit">元</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="在用" :value="1" />
            <el-option label="停用" :value="2" />
            <el-option label="报废" :value="3" />
          </el-select>
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
import { ref, onMounted } from 'vue'
import { devicePage, deviceAdd, deviceEdit, deviceDelete, labList } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const labs = ref([])
const query = ref({ current: 1, size: 10, keyword: '', labId: null, faultStatus: null })
// 设备类型词表：需覆盖库中全部 device_type 取值，否则筛选与编辑弹窗选不中已有数据
const deviceTypes = ['计算机', '服务器', '网络设备', '存储设备', '显示设备', '实验仪器', '实训器械', '物联网设备', '其他']

const editVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = ref({})
const rules = {
  deviceCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  deviceName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  labId: [{ required: true, message: '请选择实验室', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    const data = await devicePage({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadLabs() {
  labs.value = await labList()
}

function openEdit(row) {
  if (row) {
    form.value = { ...row }
  } else {
    form.value = { deviceCode: '', deviceName: '', deviceType: '计算机', labId: null, brandModel: '', purchaseDate: null, price: 0, status: 1, quantity: 1, remark: '' }
  }
  editVisible.value = true
}

function doSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.value.id) {
        await deviceEdit(form.value)
      } else {
        await deviceAdd(form.value)
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
  ElMessageBox.confirm(`确认删除设备「${row.deviceName}」吗？`, '警告', { type: 'warning' }).then(async () => {
    await deviceDelete(row.id)
    ElMessage.success('已删除')
    load()
  }).catch(() => {})
}

onMounted(() => {
  load()
  loadLabs()
})
</script>