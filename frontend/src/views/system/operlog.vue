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

      <el-table v-loading="loading" :data="records" stripe border>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { operLogPage } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, title: '', operName: '', businessType: null, status: null })

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

onMounted(load)
</script>