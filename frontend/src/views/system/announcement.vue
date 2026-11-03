<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><BellFilled /></el-icon>
          系统公告
        </div>
        <div class="toolbar-right">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="load">
            <el-option label="生效" :value="1" />
            <el-option label="下线" :value="0" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="标题/正文" clearable style="width: 180px"
            @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button type="primary" @click="openForm()"><el-icon><Plus /></el-icon> 新增公告</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="标题" prop="title" min-width="200" />
        <el-table-column label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small">{{ levelMap[row.level] || row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" min-width="160">
          <template #default="{ row }">
            {{ row.startTime ? row.startTime : '立即' }}
            <span v-if="row.endTime"> ~ {{ row.endTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="v => toggleStatus(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="form.id ? '编辑公告' : '新增公告'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="150" show-word-limit />
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="form.level">
            <el-radio value="INFO">普通</el-radio>
            <el-radio value="WARNING">提醒</el-radio>
            <el-radio value="URGENT">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示立即生效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示长期有效" style="width: 100%" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">生效</el-radio>
            <el-radio :value="0">下线</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  announcementPage, announcementCreate, announcementUpdate,
  announcementDelete, announcementSetStatus
} from '@/api'

const loading = ref(false)
const saving = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: undefined, keyword: '' })

const levelMap = { INFO: '普通', WARNING: '提醒', URGENT: '紧急' }
const levelTypeMap = { INFO: 'info', WARNING: 'warning', URGENT: 'danger' }
function levelType(l) { return levelTypeMap[l] || 'info' }

async function load() {
  loading.value = true
  try {
    const data = await announcementPage({
      current: query.current,
      size: query.size,
      status: query.status,
      keyword: query.keyword || undefined
    })
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function toggleStatus(row, checked) {
  try {
    await announcementSetStatus(row.id, checked ? 1 : 0)
    row.status = checked ? 1 : 0
    ElMessage.success(checked ? '公告已生效' : '公告已下线')
  } catch {
    // 保持原状态
  }
}

// ===== 新增 / 编辑 =====
const formVisible = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null, title: '', content: '', level: 'INFO', status: 1,
  startTime: null, endTime: null
})
const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告正文', trigger: 'blur' }]
}

function openForm(row) {
  Object.assign(form, row
    ? {
      id: row.id, title: row.title, content: row.content, level: row.level || 'INFO',
      status: row.status ?? 1, startTime: row.startTime || null, endTime: row.endTime || null
    }
    : { id: null, title: '', content: '', level: 'INFO', status: 1, startTime: null, endTime: null })
  formVisible.value = true
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title,
      content: form.content,
      level: form.level,
      status: form.status,
      startTime: form.startTime || null,
      endTime: form.endTime || null
    }
    if (form.id) {
      await announcementUpdate(form.id, payload)
    } else {
      await announcementCreate(payload)
    }
    ElMessage.success('已保存')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await announcementDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
