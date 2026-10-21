<template>
  <AuthShell title="注册账号" subtitle="LabWise Reservation System · 新用户注册">
    <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="登录账号（4-20 位字母/数字/下划线）" prefix-icon="User" />
      </el-form-item>
      <el-form-item prop="realName">
        <el-input v-model="form.realName" placeholder="真实姓名" prefix-icon="UserFilled" />
      </el-form-item>
      <el-form-item prop="phone">
        <el-input v-model="form.phone" maxlength="11" placeholder="手机号（用于接收验证码与登录）" prefix-icon="Iphone" />
      </el-form-item>
      <el-form-item prop="code">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="6 位验证码" prefix-icon="Key" />
          <el-button :disabled="counting" :loading="sending" @click="onSendCode">{{ text }}</el-button>
        </div>
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码（6-32 位）" prefix-icon="Lock" show-password />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" show-password />
      </el-form-item>

      <div class="reg-tip">
        注册成功后账号需经管理员审核通过方可登录。
      </div>

      <el-button class="submit-btn" type="primary" :loading="loading" @click="onSubmit">
        {{ loading ? '提交中...' : '注 册' }}
      </el-button>
    </el-form>

    <template #footer>
      <span>已有账号？<span class="link" @click="router.push('/login')">返回登录</span></span>
      <span class="link" @click="router.push('/forgot')">忘记密码</span>
    </template>
  </AuthShell>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, sendCaptcha } from '@/api'
import AuthShell from '@/components/AuthShell.vue'
import { useCountdown } from '@/utils/useCountdown'

const router = useRouter()
const { counting, text, start } = useCountdown(60)

const formRef = ref()
const loading = ref(false)
const sending = ref(false)
const form = ref({
  username: '',
  realName: '',
  phone: '',
  code: '',
  password: '',
  confirmPassword: ''
})

const rules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,20}$/, message: '账号需为 4-20 位字母、数字或下划线', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) =>
        value === form.value.password ? cb() : cb(new Error('两次输入的密码不一致')),
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
    const data = await sendCaptcha({ scene: 'REGISTER', account: form.value.phone.trim() })
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
      await register({
        username: form.value.username.trim(),
        realName: form.value.realName.trim(),
        phone: form.value.phone.trim(),
        password: form.value.password,
        code: form.value.code.trim()
      })
      ElMessageBox.alert('注册成功，请等待管理员审核通过后再登录。', '注册成功', {
        confirmButtonText: '去登录',
        type: 'success',
        showClose: false
      }).finally(() => router.replace('/login'))
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

.reg-tip {
  margin: -4px 0 12px;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-3);
  background: var(--card-bg-soft);
  border: 1px solid var(--glass-border);
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
