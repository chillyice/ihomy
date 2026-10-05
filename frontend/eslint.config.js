// ESLint 扁平配置(Vue3 + 浏览器环境)。
// 只做「正确性」检查:格式(缩进/换行/属性顺序)交给 Prettier,不进 lint 规则,避免全仓重排。
// 存量代码是逐步收口的,新增/改动文件应做到零 error;`npm run lint` 只以 error 退出。
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default [
  {
    ignores: ['dist/**', 'coverage/**', 'public/**', 'node_modules/**', '**/*.min.js'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        // 构建期由 vite define 注入(页脚/运维页版本号展示)
        __APP_VERSION__: 'readonly',
      },
    },
    rules: {
      // 页面组件按路由命名(Home/Login/Vault...),不强制多词名
      'vue/multi-word-component-names': 'off',
      // 未使用变量是打磨项而非缺陷:提示但不拦构建
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }],
      'no-empty': ['error', { allowEmptyCatch: true }],
    },
  },
]
