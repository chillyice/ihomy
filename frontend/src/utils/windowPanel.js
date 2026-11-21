import { computed, onScopeDispose, ref } from 'vue'

const activePanel = ref('')

/** Only the currently open panel may release its slot. */
export function useWindowPanel(name) {
  const open = computed({
    get: () => activePanel.value === name,
    set(value) {
      if (value) activePanel.value = name
      else if (activePanel.value === name) activePanel.value = ''
    },
  })
  onScopeDispose(() => {
    open.value = false
  })
  return open
}
