import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import globals from 'globals'

/**
 * ESLint 扁平配置（Vue 3 + 浏览器环境）
 *
 * 只启用 essential 级别的 Vue 规则，避免引入成千上万条风格类报错；
 * 真正的格式统一交给 Prettier（见 .prettierrc.json）。
 */
export default [
  { ignores: ['dist/**', 'node_modules/**', 'public/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        // unplugin-auto-import + ElementPlusResolver 注入的全局 API
        ElMessage: 'readonly',
        ElMessageBox: 'readonly',
        ElNotification: 'readonly',
        ElLoading: 'readonly'
      }
    },
    rules: {
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', caughtErrors: 'none' }],
      'no-empty': ['warn', { allowEmptyCatch: true }],
      // 本项目按目录 + index.vue 组织页面，组件名使用单文件路由名，无需强制多词
      'vue/multi-word-component-names': 'off'
    }
  },
  prettier
]
