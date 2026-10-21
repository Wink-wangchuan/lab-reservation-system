<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Bell /></el-icon>
          消息中心
          <span v-if="unread > 0" class="unread-chip">{{ unread }} 条未读</span>
        </div>
        <div class="toolbar-right">
          <el-radio-group v-model="query.readFlag" @change="reload">
            <el-radio-button :value="''">全部</el-radio-button>
            <el-radio-button :value="0">未读</el-radio-button>
            <el-radio-button :value="1">已读</el-radio-button>
          </el-radio-group>
          <el-select v-model="query.noticeType" placeholder="类型" clearable style="width: 130px" @change="reload">
            <el-option label="系统通知" value="SYSTEM" />
            <el-option label="预约通知" value="RESERVATION" />
            <el-option label="报修通知" value="REPAIR" />
          </el-select>
          <el-button :disabled="!unread" @click="doReadAll">
            <el-icon><Check /></el-icon> 全部已读
          </el-button>
          <el-button v-if="canSend" type="primary" @click="openSend">
            <el-icon><Promotion /></el-icon> 发送消息
          </el-button>
        </div>
      </div>

      <el-skeleton v-if="loading && !records.length" :rows="6" animated />

      <template v-else>
        <div v-if="records.length" class="msg-list">
          <div
            v-for="m in records"
            :key="m.id"
            class="msg-item"
            :class="{ unread: m.readFlag === 0 }"
            @click="openDetail(m)"
          >
            <div class="msg-icon" :class="'type-' + m.noticeType">
              <el-icon :size="18"><component :is="typeIcon(m.noticeType)" /></el-icon>
            </div>
            <div class="msg-body">
              <div class="msg-top">
                <span class="msg-title">{{ m.title }}</span>
                <span class="msg-type">{{ m.noticeTypeDesc }}</span>
                <span class="msg-time">{{ m.createTime }}</span>
              </div>
              <div class="msg-content">{{ m.content }}</div>
            </div>
            <div class="msg-actions">
              <el-button link type="danger" @click.stop="doDelete(m)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <el-empty v-else description="暂无消息" />

        <div class="table-pagination">
          <el-pagination
            v-model:current-page="query.current"
            v-model:page-size="query.size"
            :total="total"
            layout="total, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            @current-change="load"
          />
        </div>
      </template>
    </div>

    <!-- 消息详情 -->
    <el-dialog v-model="detailVisible" :title="current?.title || '消息详情'" width="520px" align-center>
      <div v-if="current" class="detail-panel">
        <div class="detail-meta">
          <el-tag :type="typeTag(current.noticeType)" effect="light">{{ current.noticeTypeDesc }}</el-tag>
          <span class="detail-time">{{ current.createTime }}</span>
        </div>
        <div class="detail-content">{{ current.content }}</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发送消息（管理员/教师） -->
    <el-dialog v-model="sendVisible" title="发送站内消息" width="560px" :close-on-click-modal="false">
      <el-form ref="sendFormRef" class="form-responsive" :model="sendForm" :rules="sendRules" label-width="90px">
        <el-form-item label="接收范围" prop="scope">
          <el-radio-group v-model="sendForm.scope">
            <el-radio value="all">全体广播</el-radio>
            <el-radio value="one">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="sendForm.scope === 'one'" label="接收人" prop="userId">
          <el-select v-model="sendForm.userId" placeholder="选择用户" filterable style="width: 100%">
            <el-option
              v-for="u in users" :key="u.id"
              :label="`${u.realName}（${u.username}）`" :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="sendForm.noticeType" style="width: 100%">
            <el-option label="系统通知" value="SYSTEM" />
            <el-option label="预约通知" value="RESERVATION" />
            <el-option label="报修通知" value="REPAIR" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" maxlength="64" show-word-limit placeholder="请输入消息标题" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="sendForm.content" type="textarea" :rows="4" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="doSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import {
  noticeMine, noticeUnreadCount, noticeRead, noticeReadAll,
  noticeDelete, noticeSend, userOptions
} from '@/api'

const userStore = useUserStore()
const canSend = computed(() => userStore.hasPerm('notice:send') || userStore.permissions.includes('*'))

const loading = ref(false)
const records = ref([])
const total = ref(0)
const unread = ref(0)
const query = ref({ current: 1, size: 10, readFlag: '', noticeType: '' })

const detailVisible = ref(false)
const current = ref(null)

const sendVisible = ref(false)
const sendFormRef = ref()
const sending = ref(false)
const users = ref([])
const sendForm = ref({ scope: 'all', userId: null, noticeType: 'SYSTEM', title: '', content: '' })
const sendRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

const TYPE_ICON = { SYSTEM: 'Message', RESERVATION: 'Tickets', REPAIR: 'Tools' }
function typeIcon(t) {
  return TYPE_ICON[t] || 'Message'
}
const TYPE_TAG = { SYSTEM: 'info', RESERVATION: 'primary', REPAIR: 'warning' }
function typeTag(t) {
  return TYPE_TAG[t] || 'info'
}

async function load() {
  loading.value = true
  try {
    const params = { ...query.value }
    // '' 会被后端当作"未筛选"处理，此处显式剔除
    if (params.readFlag === '') delete params.readFlag
    if (!params.noticeType) delete params.noticeType
    const [data, count] = await Promise.all([noticeMine(params), noticeUnreadCount()])
    records.value = data.records || []
    total.value = data.total || 0
    unread.value = count || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.value.current = 1
  load()
}

async function openDetail(m) {
  current.value = m
  detailVisible.value = true
  if (m.readFlag === 0) {
    await noticeRead(m.id)
    m.readFlag = 1
    unread.value = Math.max(0, unread.value - 1)
  }
}

async function doReadAll() {
  await noticeReadAll()
  ElMessage.success('已全部标记为已读')
  load()
}

function doDelete(m) {
  ElMessageBox.confirm(`确认删除消息「${m.title}」吗？`, '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await noticeDelete(m.id)
    ElMessage.success('已删除')
    load()
  }).catch(() => {})
}

async function openSend() {
  sendForm.value = { scope: 'all', userId: null, noticeType: 'SYSTEM', title: '', content: '' }
  sendVisible.value = true
  if (!users.value.length) {
    try {
      // 接收人下拉只需 id + 姓名：用精简接口，避免要求「用户管理」权限
      users.value = await userOptions()
    } catch {
      users.value = []
    }
  }
}

function doSend() {
  sendFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (sendForm.value.scope === 'one' && !sendForm.value.userId) {
      ElMessage.warning('请选择接收人')
      return
    }
    sending.value = true
    try {
      await noticeSend({
        userId: sendForm.value.scope === 'one' ? sendForm.value.userId : null,
        title: sendForm.value.title,
        content: sendForm.value.content,
        noticeType: sendForm.value.noticeType
      })
      ElMessage.success('消息已发送')
      sendVisible.value = false
      load()
    } finally {
      sending.value = false
    }
  })
}

onMounted(load)
</script>

<style scoped lang="scss">
.unread-chip {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #ff5c7a, #ff8a5c);
  padding: 2px 10px;
  border-radius: 999px;
}

.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: var(--card-bg);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: var(--card-bg-hover);
    transform: translateX(2px);
  }

  &.unread {
    border-color: rgba(91, 140, 255, 0.35);
    background: rgba(91, 140, 255, 0.08);

    .msg-title {
      font-weight: 700;
    }

    .msg-title::before {
      content: '';
      display: inline-block;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #ff5c7a;
      margin-right: 7px;
      vertical-align: middle;
    }
  }
}

.msg-icon {
  width: 40px;
  height: 40px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  background: linear-gradient(135deg, #5b8cff, #7a5bff);

  &.type-RESERVATION { background: linear-gradient(135deg, #5b8cff, #35b3e0); }
  &.type-REPAIR { background: linear-gradient(135deg, #ff8a5c, #ff4d6d); }
  &.type-SYSTEM { background: linear-gradient(135deg, #22c1c3, #35e0a1); }
}

.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.msg-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-1);
}

.msg-type {
  font-size: 11px;
  color: var(--ink-3);
  border: 1px solid var(--glass-border);
  padding: 1px 8px;
  border-radius: 999px;
}

.msg-time {
  font-size: 11px;
  color: var(--ink-3);
  margin-left: auto;
}

.msg-content {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--ink-2);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.msg-actions {
  flex-shrink: 0;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.detail-time {
  font-size: 12px;
  color: var(--ink-3);
}

.detail-content {
  font-size: 13.5px;
  color: var(--ink-2);
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 640px) {
  .msg-time {
    margin-left: 0;
    width: 100%;
  }

  .msg-item {
    padding: 12px;
    gap: 10px;
  }
}
</style>
