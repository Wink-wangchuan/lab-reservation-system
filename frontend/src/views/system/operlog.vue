<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Document /></el-icon>
          操作日志
        </div>
        <div class="toolbar-right">
          <el-input v-model="query.title" placeholder="操作模块" clearable style="width: 140px" @keyup.enter="load" @clear="load" />
          <el-input v-model="query.operName" placeholder="操作人" clearable style="width: 130px" @keyup.enter="load" @clear="load" />
          <el-select v-model="query.businessType" placeholder="类型" clearable style="width: 120px" @change="load">
            <el-option label="超管操作" :value="1" />
            <el-option label="业务操作" :value="2" />
          </el-select>
          <el-select v-model="query.status" placeholder="结果" clearable style="width: 110px" @change="load">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
      </div>

      <el-table v-if="!isMobile" v-loading="loading" :data="records" stripe border>
        <el-table-column label="模块" prop="title" width="120" />
        <el-table-column label="操作人" prop="operName" width="100" />
        <el-table-column label="IP" prop="operIp" width="130" />
        <el-table-column label="请求方法" prop="requestMethod" width="110" />
        <el-table-column label="接口地址" prop="operUrl" min-width="260" />
        <el-table-column label="耗时(ms)" prop="costTime" width="100" align="center" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" prop="operTime" width="170" />
      </el-table>

      <!-- 窄屏改用卡片列表：日志列多且参数过长，只展示关键信息，详情放到弹窗 -->
      <div v-else v-loading="loading" class="log-cards">
        <div v-for="row in records" :key="row.id" class="log-card">
          <div class="log-card__head">
            <span class="log-card__title">{{ row.title || '-' }}</span>
            <el-tag size="small" type="info" effect="plain">{{ businessTypeLabel(row.businessType) }}</el-tag>
          </div>
          <div class="log-card__row">
            <span class="log-card__oper">{{ row.operName || '-' }}</span>
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
            <span class="log-card__cost">耗时 {{ row.costTime ?? 0 }} ms</span>
          </div>
          <div class="log-card__sub">
            <span>{{ row.operIp || '-' }}</span>
            <span>{{ row.operTime }}</span>
          </div>
          <div v-if="row.operUrl" class="log-card__url">{{ row.operUrl }}</div>
          <div class="log-card__actions">
            <el-button size="small" type="primary" plain @click="openDetail(row)">详情</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !records.length" description="暂无日志" :image-size="80" />
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

    <!-- 操作详情：请求参数/返回结果可能很长，统一在弹窗内滚动查看 -->
    <el-dialog v-model="detailVisible" title="操作详情" width="560px" :close-on-click-modal="false">
      <div v-if="detailRow" class="log-detail">
        <div class="log-detail__line"><span class="log-detail__label">模块</span>{{ detailRow.title || '-' }}</div>
        <div class="log-detail__line"><span class="log-detail__label">类型</span>{{ businessTypeLabel(detailRow.businessType) }}</div>
        <div class="log-detail__line"><span class="log-detail__label">操作人</span>{{ detailRow.operName || '-' }}</div>
        <div class="log-detail__line">
          <span class="log-detail__label">结果</span>
          <el-tag size="small" :type="detailRow.status === 1 ? 'success' : 'danger'">
            {{ detailRow.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </div>
        <div class="log-detail__line"><span class="log-detail__label">耗时</span>{{ detailRow.costTime ?? 0 }} ms</div>
        <div class="log-detail__line"><span class="log-detail__label">时间</span>{{ detailRow.operTime }}</div>
        <div class="log-detail__line"><span class="log-detail__label">IP</span>{{ detailRow.operIp || '-' }}</div>
        <div class="log-detail__line"><span class="log-detail__label">请求方法</span>{{ detailRow.requestMethod || '-' }}</div>
        <div class="log-detail__line"><span class="log-detail__label">接口地址</span>{{ detailRow.operUrl || '-' }}</div>
        <div v-if="detailRow.operParam" class="log-detail__block">
          <div class="log-detail__label">请求参数</div>
          <pre class="log-detail__pre">{{ detailRow.operParam }}</pre>
        </div>
        <div v-if="detailRow.errorMsg" class="log-detail__block">
          <div class="log-detail__label">异常信息</div>
          <pre class="log-detail__pre">{{ detailRow.errorMsg }}</pre>
        </div>
        <div v-else-if="detailRow.jsonResult" class="log-detail__block">
          <div class="log-detail__label">返回结果</div>
          <pre class="log-detail__pre">{{ detailRow.jsonResult }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { operLogPage } from '@/api'

// 窄屏（<=640px）用卡片列表替代宽表格，参数等长内容收进详情弹窗
const isMobile = ref(window.innerWidth <= 640)
function trackWidth() {
  isMobile.value = window.innerWidth <= 640
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, title: '', operName: '', businessType: null, status: null })

const detailVisible = ref(false)
const detailRow = ref(null)

const BUSINESS_TYPE_LABEL = { INSERT: '新增', UPDATE: '修改', DELETE: '删除', QUERY: '查询' }
function businessTypeLabel(type) {
  if (!type) return '-'
  return BUSINESS_TYPE_LABEL[String(type).toUpperCase()] || type
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const data = await operLogPage({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
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
.log-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
  margin-top: 4px;
}

.log-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--card-bg-soft);
}

.log-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.log-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
  min-width: 0;
  overflow-wrap: anywhere;
}

.log-card__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.log-card__oper {
  font-size: 13px;
  color: var(--ink-1);
  font-weight: 500;
  overflow-wrap: anywhere;
}

.log-card__cost {
  font-size: 12px;
  color: var(--ink-3);
  margin-left: auto;
}

.log-card__sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 10px;
  font-size: 11.5px;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

/* 接口地址单行省略，完整内容在详情中查看 */
.log-card__url {
  font-size: 11.5px;
  color: var(--ink-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.log-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 2px;

  .el-button {
    width: 100%;
    margin-left: 0 !important;
  }
}

/* ===== 详情弹窗 ===== */
.log-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-1);
}

.log-detail__line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  overflow-wrap: anywhere;
}

.log-detail__label {
  flex-shrink: 0;
  min-width: 56px;
  color: var(--ink-3);
}

.log-detail__block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.log-detail__pre {
  margin: 0;
  padding: 8px 10px;
  max-height: 180px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-2);
  background: var(--card-bg-soft);
  border: 1px solid var(--hairline);
  border-radius: 8px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>