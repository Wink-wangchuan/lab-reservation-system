<template>
  <div class="page-container">
    <div class="profile-grid">
      <!-- 资料概览 -->
      <div class="glass-card profile-card">
        <el-skeleton v-if="loading && !profile.userId" :rows="5" animated />
        <template v-else>
          <div class="avatar-wrap">
            <el-avatar :size="76" :src="profile.avatar">
              {{ (profile.realName || '用').slice(-1) }}
            </el-avatar>
            <div class="avatar-glow"></div>
          </div>
          <div class="p-name">{{ profile.realName || '-' }}</div>
          <div class="p-account">@{{ profile.username || '-' }}</div>
          <div class="p-roles">
            <el-tag v-for="(r, i) in profile.roleNames || []" :key="i" effect="light" round>{{ r }}</el-tag>
            <el-tag type="info" effect="plain" round>{{ profile.permissionCount || 0 }} 项权限</el-tag>
          </div>
          <div class="p-info">
            <div class="pi-row"><span>性别</span><b>{{ genderText }}</b></div>
            <div class="pi-row"><span>手机号</span><b>{{ profile.phone || '未填写' }}</b></div>
            <div class="pi-row"><span>邮箱</span><b>{{ profile.email || '未填写' }}</b></div>
            <div class="pi-row"><span>注册时间</span><b>{{ profile.createTime || '-' }}</b></div>
          </div>
          <div class="p-actions">
            <el-button v-if="canReserve" type="primary" @click="router.push('/reservation/apply')">
              <el-icon><Calendar /></el-icon> 发起预约
            </el-button>
            <el-button v-if="canCheckin" @click="router.push('/checkin/mine')">
              <el-icon><Stamp /></el-icon> 我的签到
            </el-button>
          </div>
        </template>
      </div>

      <!-- 数据统计 + 设置 -->
      <div class="right-col">
        <div class="glass-card">
          <div class="page-title">
            <el-icon><DataAnalysis /></el-icon>
            我的数据
          </div>
          <div class="stat-grid">
            <div v-for="c in statCards" :key="c.key" class="stat-item">
              <div class="si-value">{{ c.value }}</div>
              <div class="si-label">{{ c.label }}</div>
            </div>
          </div>
        </div>

        <div class="glass-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="资料设置" name="profile">
              <el-form
                ref="infoFormRef"
                class="form-responsive"
                :model="infoForm"
                :rules="infoRules"
                label-width="90px"
              >
                <el-form-item label="真实姓名" prop="realName">
                  <el-input v-model="infoForm.realName" maxlength="32" placeholder="请输入姓名" />
                </el-form-item>
                <el-form-item label="性别">
                  <el-radio-group v-model="infoForm.gender">
                    <el-radio :value="0">男</el-radio>
                    <el-radio :value="1">女</el-radio>
                    <el-radio :value="2">保密</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="infoForm.phone" maxlength="11" placeholder="请输入手机号" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="infoForm.email" maxlength="64" placeholder="请输入邮箱" />
                </el-form-item>
                <el-form-item label="头像地址">
                  <el-input v-model="infoForm.avatar" maxlength="255" placeholder="可填写头像图片 URL（选填）" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="savingInfo" @click="saveInfo">保存资料</el-button>
                  <el-button @click="resetInfo">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="修改密码" name="password">
              <el-form
                ref="pwdFormRef"
                class="form-responsive"
                :model="pwdForm"
                :rules="pwdRules"
                label-width="90px"
              >
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="savingPwd" @click="savePwd">修改密码</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { profileDetail, profileUpdate, profileChangePwd, profileStats } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// 快捷入口按权限显隐：教师/实验室管理员没有「提交预约」或「我的签到」权限时
// 不展示对应按钮，避免点进去落在无权页面上
const canReserve = computed(() => userStore.hasPerm('reservation:submit'))
const canCheckin = computed(() => userStore.hasPerm('checkin:mine'))

const loading = ref(false)
const profile = ref({})
const stats = ref({})
const activeTab = ref('profile')

const infoFormRef = ref()
const pwdFormRef = ref()
const savingInfo = ref(false)
const savingPwd = ref(false)
const infoForm = ref({ realName: '', gender: 0, phone: '', email: '', avatar: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const infoRules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) =>
        value === pwdForm.value.newPassword ? cb() : cb(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}

const genderText = computed(() => {
  const g = profile.value.gender
  return g === 0 ? '男' : g === 1 ? '女' : '保密'
})

const statCards = computed(() => {
  const s = stats.value
  return [
    { key: 'resv', label: '预约总数', value: s.reservationTotal ?? 0 },
    { key: 'pending', label: '待审核', value: s.pendingReservation ?? 0 },
    { key: 'approved', label: '已通过', value: s.approvedReservation ?? 0 },
    { key: 'completed', label: '已完成', value: s.completedReservation ?? 0 },
    { key: 'repair', label: '我的报修', value: s.repairTotal ?? 0 },
    { key: 'unread', label: '未读消息', value: s.unreadNotice ?? 0 }
  ]
})

function fillForm(p) {
  infoForm.value = {
    realName: p.realName || '',
    gender: p.gender ?? 0,
    phone: p.phone || '',
    email: p.email || '',
    avatar: p.avatar || ''
  }
}

async function load() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([profileDetail(), profileStats()])
    profile.value = p || {}
    stats.value = s || {}
    fillForm(profile.value)
  } finally {
    loading.value = false
  }
}

function resetInfo() {
  fillForm(profile.value)
}

function saveInfo() {
  infoFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingInfo.value = true
    try {
      const data = await profileUpdate({ ...infoForm.value })
      profile.value = data || profile.value
      ElMessage.success('资料已更新')
      // 同步本地用户缓存，保证顶栏姓名/头像即时刷新
      userStore.userInfo = { ...(userStore.userInfo || {}), realName: profile.value.realName, avatar: profile.value.avatar }
    } finally {
      savingInfo.value = false
    }
  })
}

function savePwd() {
  pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingPwd.value = true
    try {
      await profileChangePwd({
        oldPassword: pwdForm.value.oldPassword,
        newPassword: pwdForm.value.newPassword
      })
      ElMessage.success('密码修改成功')
      pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    } finally {
      savingPwd.value = false
    }
  })
}

onMounted(load)
</script>

<style scoped lang="scss">
.profile-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.profile-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.avatar-wrap {
  position: relative;
  margin-top: 4px;
}

.avatar-glow {
  position: absolute;
  inset: -14px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(91, 140, 255, 0.28), transparent 68%);
  pointer-events: none;
}

.p-name {
  font-size: 19px;
  font-weight: 800;
  color: var(--ink-1);
}

.p-account {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: -4px;
}

.p-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  margin: 4px 0 2px;
}

.p-info {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 4px 4px;
  border-top: 1px dashed var(--glass-border);
}

.pi-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;

  span {
    color: var(--ink-3);
  }

  b {
    color: var(--ink-1);
    font-weight: 600;
    max-width: 60%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.p-actions {
  width: 100%;
  display: flex;
  gap: 10px;
  margin-top: 10px;

  .el-button {
    flex: 1;
    margin-left: 0;
  }
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.stat-item {
  position: relative;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: var(--card-bg);
  overflow: hidden;
}

.si-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--ink-1);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.si-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

@media (max-width: 1024px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
