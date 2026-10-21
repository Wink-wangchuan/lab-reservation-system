<template>
  <el-menu
    class="side-menu"
    :default-active="active"
    router
    :collapse="collapse"
    background-color="transparent"
    text-color="var(--ink-2)"
    active-text-color="#fff"
  >
    <template v-for="item in menus" :key="item.path">
      <el-menu-item :index="item.path">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.title }}</span>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup>
// 侧边导航（桌面端侧栏与移动端抽屉共用同一份实现，避免菜单两处维护）
defineProps({
  menus: { type: Array, default: () => [] },
  active: { type: String, default: '' },
  collapse: { type: Boolean, default: false }
})
</script>

<style scoped lang="scss">
.side-menu {
  border-right: none !important;
  --el-menu-text-color: var(--ink-2);
  --el-menu-active-color: #fff;
  --el-menu-item-height: 48px;

  .el-menu-item {
    border-radius: 12px;
    margin: 4px 2px;
    font-weight: 500;

    &.is-active {
      background: var(--accent-gradient);
      box-shadow: 0 6px 16px rgba(91, 140, 255, 0.35);
    }

    // 取消菜单项悬停/聚焦时的背景高光（仅保留当前页选中态高亮）
    &:not(.is-active):hover,
    &:not(.is-active):focus {
      background-color: transparent;
    }
  }
}
</style>
