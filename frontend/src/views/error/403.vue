<template>
  <div class="no-perm-page">
    <div class="glass-card no-perm-card">
      <div class="icon-wrap">
        <el-icon :size="34"><Lock /></el-icon>
      </div>
      <h1>无访问权限</h1>
      <p>当前账号没有访问此页面的权限。如需使用，请联系管理员分配对应角色或权限。</p>
      <div class="actions">
        <el-button type="primary" @click="router.push('/profile')">返回个人中心</el-button>
        <el-button @click="router.push('/dashboard')">返回驾驶舱</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped lang="scss">
.no-perm-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: var(--bg-gradient);
}

.no-perm-card {
  width: 420px;
  max-width: 100%;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 36px 30px;
}

.icon-wrap {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  background: var(--accent-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 28px rgba(91, 140, 255, 0.45);
}

h1 {
  margin: 6px 0 0;
  font-size: 20px;
  color: var(--ink-1);
}

p {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-3);
}

.actions {
  display: flex;
  gap: 10px;

  .el-button {
    margin-left: 0;
  }
}
</style>
