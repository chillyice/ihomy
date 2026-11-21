import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useUserStore } from '@/stores/user'

/** Retain the query and selection when the responsive shell remounts. Results are always fetched again. */
export const useItemSearchStore = defineStore('itemSearch', () => {
  const user = useUserStore()
  const query = ref('')
  const selection = ref(null)
  const submitted = ref('')
  watch(
    () => [user.isLoggedIn, user.userInfo?.id, user.userInfo?.familyId, user.userInfo?.role].join('/'),
    () => {
      query.value = submitted.value = ''
      selection.value = null
    },
    { flush: 'sync' },
  )
  return { query, selection, submitted }
})
