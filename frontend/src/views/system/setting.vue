<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Setting /></el-icon>
          系统设置
        </div>
        <div class="toolbar-right">
          <el-tag v-if="maintenanceOn" type="danger" effect="dark">维护模式已开启</el-tag>
          <el-button @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="配置修改后立即生效：维护模式与定时任务开关带 60 秒缓存，外部通知 webhook 只显示是否已配置，不显示明文。"
        class="tip"
      />

      <div v-for="group in groups" :key="group.name" class="config-group">
        <div class="config-group__title">{{ group.name }}</div>
        <div v-for="item in group.items" :key="item.configKey" class="config-row">
          <div class="config-row__meta">
            <div class="config-row__label">{{ item.label || item.configKey }}</div>
            <div class="config-row__desc">{{ item.description || item.configKey }}</div>
          </div>

          <div class="config-row__control">
            <template v-if="item.valueType === 'BOOLEAN'">
              <el-switch
                :model-value="item.configValue === 'true'"
                @change="v => save(item, v ? 'true' : 'false')"
              />
            </template>
            <template v-else-if="item.valueType === 'SECRET'">
              <el-tag :type="item.configured ? 'success' : 'info'" size="small">
                {{ item.configured ? '已配置' : '未配置' }}
              </el-tag>
              <el-button link type="primary" @click="setSecret(item)">
                {{ item.configured ? '重新设置' : '去设置' }}
              </el-button>
              <el-button v-if="item.configured" link type="danger" @click="save(item, '')">清除</el-button>
            </template>
            <template v-else>
              <el-input v-model="item.configValue" class="config-input" @keyup.enter="save(item, item.configValue)" />
              <el-button link type="primary" @click="save(item, item.configValue)">保存</el-button>
            </template>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !groups.length" description="暂无配置项" :image-size="80" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { sysConfigList, sysConfigUpdate } from '@/api'

const loading = ref(false)
const items = ref([])

const groups = computed(() => {
  const map = new Map()
  items.value.forEach((item) => {
    const name = item.groupName || '基础设置'
    if (!map.has(name)) map.set(name, [])
    map.get(name).push(item)
  })
  return [...map.entries()].map(([name, list]) => ({ name, items: list }))
})

const maintenanceOn = computed(() =>
  items.value.some((i) => i.configKey === 'system.maintenance' && i.configValue === 'true'))

async function load() {
  loading.value = true
  try {
    items.value = (await sysConfigList()) || []
  } finally {
    loading.value = false
  }
}

async function save(item, value) {
  try {
    await sysConfigUpdate({ configKey: item.configKey, configValue: value ?? '' })
    if (item.valueType !== 'SECRET') {
      item.configValue = value ?? ''
    } else {
      item.configured = !!value
    }
    ElMessage.success('已保存，配置立即生效')
  } catch {
    // 失败提示已由拦截器统一处理
  }
}

async function setSecret(item) {
  try {
    const { value } = await ElMessageBox.prompt(
      `输入「${item.label || item.configKey}」的新值（留空表示清除）`,
      '设置密钥类配置',
      { inputType: 'password', confirmButtonText: '保存', cancelButtonText: '取消' }
    )
    await save(item, value)
  } catch {
    // 用户取消
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.tip {
  margin-bottom: 12px;
}

.config-group {
  margin-bottom: 18px;
}

.config-group__title {
  margin: 0 0 8px;
  padding-left: 8px;
  border-left: 3px solid var(--brand, #409eff);
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 4px;
  border-bottom: 1px dashed var(--hairline);
}

.config-row__meta {
  min-width: 0;
}

.config-row__label {
  font-size: 14px;
  color: var(--ink-1);
}

.config-row__desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

.config-row__control {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.config-input {
  width: 260px;
}

@media (max-width: 640px) {
  .config-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .config-row__control {
    width: 100%;
  }

  .config-input {
    width: 100%;
    flex: 1;
  }
}
</style>
