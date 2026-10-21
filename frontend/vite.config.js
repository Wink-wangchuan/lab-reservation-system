import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板中的 el-* 组件、v-loading 指令，以及 ElMessage 等
    // 命令式 API 均自动导入，样式同步按需加载，避免打进全量包
    AutoImport({ resolvers: [ElementPlusResolver()], dts: false }),
    Components({ resolvers: [ElementPlusResolver()], directives: true, dts: false })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    rollupOptions: {
      output: {
        // 仅显式拆分 echarts 与 vue 生态；element-plus 交由 Rollup 自动分包。
        // 实测：手工把 element-plus 全部强行并入单一 chunk，会破坏 tree-shaking，
        // 产物反而比自动分包大约 368KB，故此处不接手 element-plus。
        manualChunks(id) {
          const path = id.replace(/\\/g, '/')
          if (!path.includes('node_modules')) return
          if (path.includes('node_modules/echarts') || path.includes('node_modules/zrender')) return 'echarts'
          if (
            path.includes('node_modules/vue/') ||
            path.includes('node_modules/vue-router/') ||
            path.includes('node_modules/pinia/') ||
            path.includes('node_modules/@vue/')
          ) return 'vue'
        }
      }
    }
  },
  server: {
    port: 8081,
    host: true,
    open: false,
    proxy: {
      // 后端接口统一走 /api 前缀，代理到 SpringBoot 服务避免跨域
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
