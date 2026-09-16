import { ref } from 'vue'

// 暖居首页「已添加」的模块 code 集合(模块级单例):
// WarmHome 随布局变化写入,WarmLayout 侧栏在编辑模式下据此标记「已拖入冲突」状态。
export const addedCodes = ref(new Set())
