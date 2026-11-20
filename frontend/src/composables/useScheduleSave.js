import { ref } from 'vue'

/** 分享与主记录是两次独立写入，重试时沿用已有记录。 */
export function useScheduleSave(saveAudience, onSaved, onComplete) {
  const saving = ref(false)
  const pendingShare = ref(null)

  async function submit(saveRecord) {
    if (saving.value || (!pendingShare.value && !saveRecord)) return
    saving.value = true
    try {
      const saved = pendingShare.value || (await saveRecord())
      try {
        await saveAudience(saved.kind, saved.id)
      } catch {
        if (!pendingShare.value) onSaved(saved)
        pendingShare.value = saved
        return
      }
      pendingShare.value = null
      onComplete()
      onSaved(saved)
    } finally {
      saving.value = false
    }
  }

  function reset() {
    pendingShare.value = null
  }

  return { saving, pendingShare, submit, reset }
}
