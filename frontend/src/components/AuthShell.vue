<template>
  <div class="auth-page">
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>

    <div class="auth-card glass-card">
      <div class="auth-head">
        <div class="logo">
          <el-icon :size="40"><Cpu /></el-icon>
        </div>
        <h1 class="auth-title">{{ title }}</h1>
        <p class="auth-sub">{{ subtitle }}</p>
      </div>

      <slot />

      <div v-if="$slots.footer" class="auth-footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<script setup>
// 登录 / 注册 / 忘记密码三个整页共用的外壳：
// 光斑背景 + 液态玻璃卡片 + 品牌头 + 底部插槽，避免三处重复维护
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' }
})
</script>

<style scoped lang="scss">
.auth-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--bg-gradient);
  padding: 24px 16px;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      linear-gradient(120deg, rgba(91, 140, 255, 0.06), transparent 40%),
      linear-gradient(-120deg, rgba(122, 91, 255, 0.06), transparent 40%);
    z-index: 0;
  }
}

// 漂浮光斑
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.6;
  z-index: 0;
  animation: float 9s ease-in-out infinite;
}
.orb-1 {
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(91, 140, 255, 0.5), transparent 70%);
  top: 12%;
  left: 16%;
}
.orb-2 {
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(122, 91, 255, 0.5), transparent 70%);
  bottom: 14%;
  right: 18%;
  animation-delay: -3s;
}
.orb-3 {
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(80, 210, 200, 0.4), transparent 70%);
  top: 60%;
  left: 50%;
  animation-delay: -6s;
}

@keyframes float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(20px, -30px) scale(1.08);
  }
}

.auth-card {
  position: relative;
  z-index: 2;
  width: 420px;
  max-width: 100%;
  padding: 36px 36px 24px;
}

.auth-head {
  text-align: center;
  margin-bottom: 22px;
}

.logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 22px;
  background: var(--accent-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 28px rgba(91, 140, 255, 0.45);
  position: relative;
  // 建立层叠上下文，让下面的高光只压在底色上、不盖住图标
  isolation: isolate;

  &::after {
    content: '';
    position: absolute;
    inset: 6px;
    border-radius: 16px;
    background: linear-gradient(160deg, rgba(255, 255, 255, 0.5), transparent 50%);
    pointer-events: none;
    z-index: -1;
  }
}

.auth-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink-1);
  margin: 0 0 6px;
  letter-spacing: 0.5px;
}

.auth-sub {
  font-size: 12px;
  color: var(--ink-3);
  letter-spacing: 1px;
  margin: 0;
}

.auth-footer {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12.5px;
  color: var(--ink-3);
}

@media (max-width: 480px) {
  .auth-card {
    padding: 28px 20px 18px;
  }

  .logo {
    width: 62px;
    height: 62px;
    border-radius: 18px;
  }

  .auth-title {
    font-size: 18px;
  }
}
</style>
