import { defineStore } from 'pinia'
import { isAndroidApp, isNativeApp } from '@/utils/platform'

export const APP_ICON_IDS = ['window-dark', 'window-light', 'leaf-dark', 'leaf-light']
const DEFAULT_ICON = 'window-dark'
const STORAGE_KEY = 'ihomy:app-icon'

export function appIconImage(id) {
  return `/app-icons/${id}/icon-96.png`
}

function storedIcon() {
  const saved = localStorage.getItem(STORAGE_KEY)
  return APP_ICON_IDS.includes(saved) ? saved : DEFAULT_ICON
}

function applyLinks(id) {
  document.querySelector('link[rel="icon"]')?.setAttribute('href', appIconImage(id))
  document.querySelector('link[rel="apple-touch-icon"]')?.setAttribute('href', `/app-icons/${id}/apple-touch.png`)
  if (isNativeApp()) return
  let manifest = document.querySelector('link[rel="manifest"]')
  if (!manifest) {
    manifest = document.createElement('link')
    manifest.rel = 'manifest'
    document.head.appendChild(manifest)
  }
  manifest.setAttribute('href', `/app-icons/${id}/manifest.webmanifest`)
}

export const useAppIconStore = defineStore('appIcon', {
  state: () => ({ id: storedIcon(), busy: false }),
  getters: {
    image: (state) => appIconImage(state.id),
  },
  actions: {
    async initialize() {
      applyLinks(this.id)
      if (!isAndroidApp()) return
      this.busy = true
      try {
        const { launcherIcon } = await import('@/native/appIcon')
        const { id } = await launcherIcon.getIcon()
        if (APP_ICON_IDS.includes(id) && this.id !== id) this.apply(id)
      } catch {
        // An older Android shell has no launcher icon plugin.
      } finally {
        this.busy = false
      }
    },
    apply(id) {
      this.id = id
      applyLinks(id)
      localStorage.setItem(STORAGE_KEY, id)
    },
    async select(id) {
      if (!APP_ICON_IDS.includes(id) || id === this.id || this.busy) return
      this.busy = true
      try {
        if (isAndroidApp()) {
          const { launcherIcon } = await import('@/native/appIcon')
          await launcherIcon.setIcon({ id })
        }
        this.apply(id)
      } finally {
        this.busy = false
      }
    },
  },
})
