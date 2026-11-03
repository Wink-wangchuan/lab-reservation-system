<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Reading /></el-icon>
          AI 知识库
        </div>
        <div class="toolbar-right">
          <el-select v-model="query.category" placeholder="分类" clearable style="width: 130px" @change="load">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="load">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="标题/关键词/正文" clearable style="width: 190px"
            @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button @click="testVisible = true"><el-icon><Aim /></el-icon> 命中测试</el-button>
          <el-button type="success" plain @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
          <el-button type="warning" plain @click="pickFile"><el-icon><Upload /></el-icon> 导入</el-button>
          <el-button type="primary" @click="openForm()"><el-icon><Plus /></el-icon> 新增</el-button>
          <input ref="fileRef" type="file" accept=".json,application/json" class="hidden-file" @change="doImport" />
        </div>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="分类" prop="category" width="110" />
        <el-table-column label="标题" prop="title" min-width="200" />
        <el-table-column label="触发关键词" prop="keywords" min-width="220" show-overflow-tooltip />
        <el-table-column label="优先级" prop="priority" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="v => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑知识条目' : '新增知识条目'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" filterable allow-create default-first-option style="width: 100%"
            placeholder="选择已有分类或直接输入新分类">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="英文逗号分隔，如：取消,不能取消,撤销" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="9" placeholder="命中后注入 AI 提示词的知识正文" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
          <span class="form-tip">同分时数值越大越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 命中测试 -->
    <el-dialog v-model="testVisible" title="命中测试" width="640px">
      <div class="test-bar">
        <el-input v-model="testQuestion" placeholder="输入一句用户可能问的话，如：预约可以取消吗？"
          @keyup.enter="runTest" />
        <el-button type="primary" :loading="testing" @click="runTest">测试</el-button>
      </div>
      <el-empty v-if="!testLoading && !hits.length" description="暂无命中结果，试试换个说法" :image-size="70" />
      <div v-else v-loading="testLoading" class="hit-list">
        <div v-for="hit in hits" :key="hit.id" class="hit-item">
          <div class="hit-item__head">
            <span class="hit-item__title">{{ hit.title }}</span>
            <el-tag size="small" type="success">命中 {{ hit.score }} 个关键词</el-tag>
          </div>
          <div class="hit-item__cat">{{ hit.category }}</div>
          <div class="hit-item__content">{{ hit.content }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  knowledgePage, knowledgeCategories, knowledgeCreate, knowledgeUpdate,
  knowledgeDelete, knowledgeSetStatus, knowledgeTest, knowledgeExport, knowledgeImport
} from '@/api'

const loading = ref(false)
const saving = ref(false)
const records = ref([])
const total = ref(0)
const categories = ref([])
const query = reactive({ current: 1, size: 10, category: '', status: undefined, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await knowledgePage({
      current: query.current,
      size: query.size,
      category: query.category || undefined,
      status: query.status,
      keyword: query.keyword || undefined
    })
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = (await knowledgeCategories()) || []
  } catch {
    categories.value = []
  }
}

async function toggleStatus(row, checked) {
  try {
    await knowledgeSetStatus(row.id, checked ? 1 : 0)
    row.status = checked ? 1 : 0
    ElMessage.success(checked ? '已启用，AI 立即生效' : '已停用')
  } catch {
    // 失败时保持原状态
  }
}

// ===== 新增 / 编辑 =====
const formVisible = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, category: '', title: '', keywords: '', content: '', priority: 60, status: 1 })
const rules = {
  category: [{ required: true, message: '请选择或输入分类', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  keywords: [{ required: true, message: '请输入触发关键词', trigger: 'blur' }],
  content: [{ required: true, message: '请输入知识正文', trigger: 'blur' }]
}

function openForm(row) {
  Object.assign(form, row
    ? { id: row.id, category: row.category, title: row.title, keywords: row.keywords, content: row.content, priority: row.priority ?? 0, status: row.status ?? 1 }
    : { id: null, category: query.category || '', title: '', keywords: '', content: '', priority: 60, status: 1 })
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
      category: form.category, title: form.title, keywords: form.keywords,
      content: form.content, priority: form.priority, status: form.status
    }
    if (form.id) {
      await knowledgeUpdate(form.id, payload)
    } else {
      await knowledgeCreate(payload)
    }
    ElMessage.success('已保存，AI 立即生效')
    formVisible.value = false
    loadCategories()
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await knowledgeDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ===== 命中测试 =====
const testVisible = ref(false)
const testQuestion = ref('')
const testing = ref(false)
const testLoading = ref(false)
const hits = ref([])

async function runTest() {
  if (!testQuestion.value.trim()) {
    ElMessage.warning('请输入测试问题')
    return
  }
  testing.value = true
  testLoading.value = true
  try {
    hits.value = (await knowledgeTest(testQuestion.value.trim(), 8)) || []
  } finally {
    testing.value = false
    testLoading.value = false
  }
}

// ===== 导入 / 导出 =====
const fileRef = ref(null)

function pickFile() {
  fileRef.value?.click()
}

async function doExport() {
  const data = (await knowledgeExport()) || []
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `知识库_${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
}

async function doImport(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  let items
  try {
    items = JSON.parse(await file.text())
  } catch {
    ElMessage.error('文件不是合法的 JSON')
    return
  }
  if (!Array.isArray(items)) {
    ElMessage.error('导入文件应为条目的 JSON 数组')
    return
  }
  try {
    await ElMessageBox.confirm(`将导入 ${items.length} 条，同 ID 条目会被覆盖，是否继续？`, '导入确认', { type: 'warning' })
  } catch {
    return
  }
  const written = await knowledgeImport(items)
  ElMessage.success(`已导入 ${written} 条，AI 立即生效`)
  loadCategories()
  load()
}

onMounted(() => {
  load()
  loadCategories()
})
</script>

<style scoped lang="scss">
.hidden-file {
  display: none;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--ink-3);
}

.test-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.hit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
}

.hit-item {
  padding: 10px 12px;
  border: 1px solid var(--hairline);
  border-radius: 10px;
  background: var(--card-bg-soft);
}

.hit-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hit-item__title {
  font-weight: 600;
  color: var(--ink-1);
}

.hit-item__cat {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-3);
}

.hit-item__content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--ink-2);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
