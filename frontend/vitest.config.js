import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'

// 单元测试配置:只跑纯逻辑(utils),不加载 VitePWA/AutoImport 等构建插件
// 例外:widgets/registry.js 会静态 import 样式 SFC(布局身份的唯一真相源),用下面的桩把 .vue 变成空组件,
// 这样「注册表元信息 / 文案键 / 尺寸档位」这类约束能在纯 node 环境里验证,不用起浏览器与 vue 插件
const vueStub = {
  name: 'vue-sfc-stub',
  enforce: 'pre',
  load(id) {
    if (id.endsWith('.vue')) return 'export default { name: "VueStub", render: () => null }'
  },
}

export default defineConfig({
  plugins: [vueStub],
  test: {
    environment: 'node',
    include: ['src/**/*.spec.js'],
    // 用例都是纯函数:用线程池而不是默认的进程池,每个 spec 的启动开销更小
    // (V14.43 实测全量 1687 条 31.3s → 24.3s,隔离保持开启,不共享模块状态)
    pool: 'threads',
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
