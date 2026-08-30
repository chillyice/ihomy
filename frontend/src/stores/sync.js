// 后台映射同步任务的全局监听:SyncDialog 切后台/关闭后继续轮询,完成及时弹通知;
// 相册页 watch doneCount 自动刷新。taskId 存 sessionStorage,页面刷新后恢复监听(服务重启任务丢失则清除)。
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { storageApi } from '@/api'
import i18n from '@/i18n'

const KEY = 'ihomy:syncTasks'

export const useSyncStore = defineStore('sync', () => {
  const tasks = ref(JSON.parse(sessionStorage.getItem(KEY) || '[]'))
  const doneCount = ref(0)
  let timer = null

  function persist() {
    sessionStorage.setItem(KEY, JSON.stringify(tasks.value))
  }

  function watch(taskId) {
    if (!tasks.value.includes(taskId)) {
      tasks.value.push(taskId)
      persist()
    }
    ensureTimer()
  }

  function ensureTimer() {
    if (timer || !tasks.value.length) return
    timer = setInterval(poll, 3000)
  }

  async function poll() {
    for (const id of [...tasks.value]) {
      try {
        const p = await storageApi.syncProgress(id)
        if (p.status === 'DONE' || p.status === 'FAILED') {
          tasks.value = tasks.value.filter((x) => x !== id)
          persist()
          const t = i18n.global.t
          if (p.status === 'DONE') {
            ElNotification.success({ title: t('storage.syncDone'), message: p.message || '', duration: 5000 })
            doneCount.value++
          } else {
            ElNotification.error({ title: t('storage.syncFailed'), message: p.message || '', duration: 6000 })
          }
        }
      } catch {
        // 任务不存在(服务重启导致内存进度丢失):移除监听
        tasks.value = tasks.value.filter((x) => x !== id)
        persist()
      }
    }
    if (!tasks.value.length && timer) {
      clearInterval(timer)
      timer = null
    }
  }

  ensureTimer()
  return { tasks, doneCount, watch }
})
