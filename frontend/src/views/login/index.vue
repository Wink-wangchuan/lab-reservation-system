<template>
  <AuthShell title="智慧实验室预约管理系统" subtitle="LabWise Reservation System · 登录">
    <el-tabs v-model="activeTab" class="auth-tabs" stretch>
      <!-- 密码登录 -->
      <el-tab-pane label="密码登录" name="password">
        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          size="large"
          @keyup.enter="onPasswordLogin"
        >
          <el-form-item prop="username">
            <el-input v-model="pwdForm.username" placeholder="请输入用户名" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="pwdForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-button class="submit-btn" type="primary" :loading="loading" @click="onPasswordLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>
      </el-tab-pane>

      <!-- 验证码登录 -->
      <el-tab-pane label="验证码登录" name="code">
        <el-form
          ref="codeFormRef"
          :model="codeForm"
          :rules="codeRules"
          size="large"
          @keyup.enter="onCodeLogin"
        >
          <el-form-item prop="account">
            <el-input v-model="codeForm.account" placeholder="用户名 / 手机号" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="code">
            <div class="code-row">
              <el-input
                v-model="codeForm.code"
                maxlength="6"
                placeholder="6 位验证码"
                prefix-icon="Key"
              />
              <el-button :disabled="counting" :loading="sending" @click="onSendCode">
                {{ text }}
              </el-button>
            </div>
          </el-form-item>
          <el-button class="submit-btn" type="primary" :loading="loading" @click="onCodeLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <span>还没有账号？<span class="link" @click="router.push('/register')">立即注册</span></span>
      <span class="link" @click="router.push('/forgot')">忘记密码</span>
    </template>
  </AuthShell>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { sendCaptcha } from '@/api'
import AuthShell from '@/components/AuthShell.vue'
import { useCountdown } from '@/utils/useCountdown'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { counting, text, start } = useCountdown(60)

const activeTab = ref('password')
const loading = ref(false)
const sending = ref(false)

const pwdFormRef = ref()
const codeFormRef = ref()
const pwdForm = ref({ username: '', password: '' })
const codeForm = ref({ account: '', code: '' })

const pwdRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const codeRules = {
  account: [{ required: true, message: '请输入用户名或手机号', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

function afterLogin() {
  ElMessage.success('登录成功，欢迎回来！')
  router.push(route.query.redirect || '/dashboard')
}

/** 密码登录 */
function onPasswordLogin() {
  if (loading.value) return
  pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.doLogin(pwdForm.value.username, pwdForm.value.password)
      afterLogin()
    } catch {
      // 错误已由响应拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}

/**
 * 获取验证码
 *
 * <p>演示环境后端会直接把验证码随响应返回，这里用通知展示；
 * 生产环境应由短信通道下发，前端不应拿到验证码本身。</p>
 */
async function onSendCode() {
  if (counting.value || sending.value) return
  const valid = await codeFormRef.value.validateField('account').then(() => true).catch(() => false)
  if (!valid) return
  sending.value = true
  try {
    const data = await sendCaptcha({ scene: 'LOGIN', account: codeForm.value.account.trim() })
    start()
    ElNotification({
      title: '验证码已生成',
      message: `本次验证码：${data.code}，${Math.round(data.expireSeconds / 60)} 分钟内有效。`
        + '演示环境未接入短信通道，验证码由服务端直接返回。',
      type: 'success',
      duration: 15000
    })
  } catch {
    // 错误已由响应拦截器统一提示
  } finally {
    sending.value = false
  }
}

/** 验证码登录 */
function onCodeLogin() {
  if (loading.value) return
  codeFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.doLoginByCode(codeForm.value.account.trim(), codeForm.value.code.trim())
      afterLogin()
    } catch {
      // 错误已由响应拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}

// 切换登录方式时清理上一方式的加载态，避免按钮 loading 串扰
watch(activeTab, () => {
  loading.value = false
})
</script>

<style scoped lang="scss">
.auth-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 18px;
  }

  :deep(.el-tabs__item) {
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-3);
  }

  :deep(.el-tabs__item.is-active) {
    color: var(--accent);
  }
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;

  .el-input {
    flex: 1;
    min-width: 0;
  }

  .el-button {
    flex-shrink: 0;
    min-width: 104px;
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 12px;
  margin-top: 4px;
  background: var(--accent-gradient);
  border: none;
  box-shadow: 0 8px 20px rgba(91, 140, 255, 0.4);
}

.link {
  color: var(--accent);
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.75;
  }
}
</style>
