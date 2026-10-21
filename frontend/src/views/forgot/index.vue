<template>
  <AuthShell title="找回密码" subtitle="LabWise Reservation System · 重置登录密码">
    <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
      <el-form-item prop="phone">
        <el-input v-model="form.phone" maxlength="11" placeholder="请输入注册时绑定的手机号" prefix-icon="Iphone" />
      </el-form-item>
      <el-form-item prop="code">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="6 位验证码" prefix-icon="Key" />
          <el-button :disabled="counting" :loading="sending" @click="onSendCode">{{ text }}</el-button>
        </div>
      </el-form-item>
      <el-form-item prop="newPassword">
        <el-input v-model="form.newPassword" type="password" placeholder="新密码（6-32 位）" prefix-icon="Lock" show-password />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" placeholder="确认新密码" prefix-icon="Lock" show-password />
      </el-form-item>

      <el-button class="submit-btn" type="primary" :loading="loading" @click="onSubmit">
        {{ loading ? '提交中...' : '重置密码' }}
      </el-button>
    </el-form>

    <template #footer>
      <span>想起密码了？<span class="link" @click="router.push('/login')">返回登录</span></span>
      <span class="link" @click="router.push('/register')">注册账号</span>
    </template>
  </AuthShell>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { resetPassword, sendCaptcha } from '@/api'
import AuthShell from '@/components/AuthShell.vue'
import { useCountdown } from '@/utils/useCountdown'

const router = useRouter()
const { counting, text, start } = useCountdown(60)

const formRef = ref()
const loading = ref(false)
const sending = ref(false)
const form = ref({ phone: '', code: '', newPassword: '', confirmPassword: '' })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) =>
        value === form.value.newPassword ? cb() : cb(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}

async function onSendCode() {
  if (counting.value || sending.value) return
  const valid = await formRef.value.validateField('phone').then(() => true).catch(() => false)
  if (!valid) return
  sending.value = true
  try {
    const data = await sendCaptcha({ scene: 'RESET', account: form.value.phone.trim() })
    start()
    ElNotification({
      title: '验证码已生成',
      message: data.code
        ? `本次验证码：${data.code}，${Math.round(data.expireSeconds / 60)} 分钟内有效。演示环境未接入短信通道，验证码由服务端直接返回。`
        : `验证码已发送，${Math.round(data.expireSeconds / 60)} 分钟内有效，请查收短信。`,
      type: 'success',
      duration: 15000
    })
  } catch {
    // 错误已由响应拦截器统一提示
  } finally {
    sending.value = false
  }
}

function onSubmit() {
  if (loading.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await resetPassword({
        phone: form.value.phone.trim(),
        code: form.value.code.trim(),
        newPassword: form.value.newPassword
      })
      ElMessage.success('密码已重置，请使用新密码登录')
      router.replace('/login')
    } catch {
      // 错误已由响应拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
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
