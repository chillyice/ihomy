<template>
  <Teleport to="body">
  <div class="reader-overlay" :class="{ dark: isDark, fullscreen: isFullscreen }">
    <!-- Top Bar -->
    <div class="reader-bar">
      <div class="bar-left">
        <button class="r-btn" @click="toggleToc" :title="$t('library.toc')">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
        </button>
        <span class="reader-title">{{ book?.title }}</span>
      </div>
      <div class="bar-right">
        <button v-if="book?.fileFormat === 'EPUB'" class="r-btn" @click="toggleSettings" :title="$t('library.fontSize')">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7V4h16v3"/><path d="M9 20h6"/><path d="M12 4v16"/></svg>
        </button>
        <button v-if="book?.fileFormat === 'EPUB'" class="r-btn" @click="toggleBookmarkPanel" :title="$t('library.bookmark')">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
        </button>
        <button class="r-btn" @click="addBookmark" :title="$t('library.addBookmark')">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14"/></svg>
        </button>
        <button class="r-btn" @click="toggleFullscreen" :title="isFullscreen ? $t('library.exitFullscreen') : $t('library.fullscreen')">
          <svg v-if="!isFullscreen" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M21 8V5a2 2 0 0 0-2-2h-3"/><path d="M3 16v3a2 2 0 0 0 2 2h3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/></svg>
          <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M8 3v3a2 2 0 0 1-2 2H3"/><path d="M21 8h-3a2 2 0 0 1-2-2V3"/><path d="M3 16h3a2 2 0 0 1 2 2v3"/><path d="M16 21v-3a2 2 0 0 1 2-2h3"/></svg>
        </button>
        <button class="r-btn" @click="close" :title="$t('common.close')">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <!-- Main Area -->
    <div class="reader-main">
      <!-- Left: TOC Drawer -->
      <transition name="slide-left">
        <div v-if="showToc" class="toc-drawer">
          <div class="drawer-head">{{ $t('library.toc') }}</div>
          <div class="toc-list">
            <div v-for="(item, i) in toc" :key="i" class="toc-item" :class="{ active: activeToc === i }" @click="goToToc(item)">
              <span class="toc-indent" :style="{ width: (item.depth || 0) * 12 + 'px' }"></span>
              <span class="toc-text">{{ item.label }}</span>
            </div>
            <div v-if="!toc.length" class="toc-empty">{{ $t('common.empty') }}</div>
          </div>
        </div>
      </transition>

      <!-- Center: Reader Content -->
      <div class="reader-content" ref="contentRef">
        <iframe v-if="book?.fileFormat === 'PDF'" :src="book.fileUrl" class="pdf-frame" />
        <div v-else-if="book?.fileFormat === 'TXT'" ref="txtRef" class="txt-reader" @click="onTxtClick">{{ currentPageText }}</div>
        <div v-else-if="book?.fileFormat === 'EPUB'" ref="epubRef" class="epub-reader" @click="onEpubClick"></div>
        <div v-else class="unsupported">
          <div>{{ $t('library.unsupportedFormat') }}</div>
          <a :href="book?.fileUrl" :download="book?.title" class="r-btn primary" style="margin-top: 12px">{{ $t('library.download') }}</a>
        </div>

        <!-- Nav arrows for EPUB/TXT -->
        <template v-if="book?.fileFormat === 'EPUB' || book?.fileFormat === 'TXT'">
          <button class="nav-arrow left" @click="prevPage">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M15 18l-6-6 6-6"/></svg>
          </button>
          <button class="nav-arrow right" @click="nextPage">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 18l6-6-6-6"/></svg>
          </button>
        </template>
      </div>

      <!-- Right: Metadata/Bookmarks Sidebar -->
      <transition name="slide-right">
        <div v-if="showSidebar" class="side-drawer">
          <div v-if="activePanel === 'settings'" class="drawer-content">
            <div class="drawer-head">{{ $t('library.fontSize') }}</div>
            <div class="setting-row">
              <span class="set-label">{{ $t('library.fontSize') }}</span>
              <div class="set-controls">
                <button class="r-btn small" @click="changeFontSize(-1)">A-</button>
                <span class="set-val">{{ fontSize }}px</span>
                <button class="r-btn small" @click="changeFontSize(1)">A+</button>
              </div>
            </div>
            <div class="setting-row">
              <span class="set-label">{{ $t('library.lineHeight') }}</span>
              <div class="set-controls">
                <button class="r-btn small" @click="changeLineHeight(-0.1)">−</button>
                <span class="set-val">{{ lineHeight.toFixed(1) }}</span>
                <button class="r-btn small" @click="changeLineHeight(0.1)">+</button>
              </div>
            </div>
            <div class="setting-row">
              <span class="set-label">{{ $t('library.pageMargin') }}</span>
              <div class="set-controls">
                <button class="r-btn small" @click="changeMargin(-10)">−</button>
                <span class="set-val">{{ margin }}px</span>
                <button class="r-btn small" @click="changeMargin(10)">+</button>
              </div>
            </div>
          </div>
          <div v-else-if="activePanel === 'bookmarks'" class="drawer-content">
            <div class="drawer-head">{{ $t('library.bookmark') }}</div>
            <div class="bm-list">
              <div v-for="bm in bookmarks" :key="bm.id" class="bm-item" @click="goToBookmark(bm)">
                <span class="bm-label">{{ bm.label || $t('library.bookmark') }}</span>
                <button class="bm-del" @click.stop="deleteBookmark(bm.id)">
                  <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
                </button>
              </div>
              <div v-if="!bookmarks.length" class="toc-empty">{{ $t('common.empty') }}</div>
            </div>
          </div>
          <div v-else class="drawer-content">
            <div class="drawer-head">{{ $t('library.metadata') }}</div>
            <div class="meta-item"><span class="mi-label">{{ $t('library.bookTitle') }}</span><span class="mi-val">{{ book?.title }}</span></div>
            <div v-if="book?.author" class="meta-item"><span class="mi-label">{{ $t('library.author') }}</span><span class="mi-val">{{ book.author }}</span></div>
            <div class="meta-item"><span class="mi-label">{{ $t('library.filterFormat') }}</span><span class="mi-val">{{ book?.fileFormat }}</span></div>
            <div v-if="book?.fileSize" class="meta-item"><span class="mi-label">{{ $t('library.pageMargin') }}</span><span class="mi-val">{{ formatSize(book.fileSize) }}</span></div>
            <div class="meta-item"><span class="mi-label">{{ $t('library.categoriesLabel') }}</span><span class="mi-val">{{ bookCategoryNames }}</span></div>
          </div>
        </div>
      </transition>
    </div>

    <!-- 全屏模式浮动关闭按钮 -->
    <button v-if="isFullscreen" class="reader-float-close" @click="close" :title="$t('common.close')">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
    </button>
  </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch, inject } from 'vue'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const props = defineProps({ book: Object })
const emit = defineEmits(['close', 'statusChanged'])

const { t } = useI18n()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)
const isDark = ref(document.documentElement.classList.contains('dark'))
const isFullscreen = ref(false)
const showToc = ref(false)
const showSidebar = ref(false)
const activePanel = ref('metadata')
const contentRef = ref(null)
const epubRef = ref(null)
const txtRef = ref(null)
const toc = ref([])
const activeToc = ref(-1)
const bookmarks = ref([])
const borrow = ref(null)

// TXT reader
const txtContent = ref('')
const txtPage = ref(0)
const charsPerPage = 3000

// EPUB reader state
let epubRendition = null
let epubBook = null

// Reader settings
const fontSize = ref(parseInt(localStorage.getItem('ihomy:reader:fontSize') || '16'))
const lineHeight = ref(parseFloat(localStorage.getItem('ihomy:reader:lineHeight') || '1.8'))
const margin = ref(parseInt(localStorage.getItem('ihomy:reader:margin') || '40'))

const totalPages = computed(() => Math.max(1, Math.ceil(txtContent.value.length / charsPerPage)))
const currentPageText = computed(() => {
  if (!txtContent.value) return ''
  const start = txtPage.value * charsPerPage
  return txtContent.value.slice(start, start + charsPerPage)
})

const bookCategoryNames = computed(() => {
  if (!props.book?.categoryIds?.length) return t('library.uncategorized')
  // We don't have category names here, just show count
  return props.book.categoryIds.length + ' ' + t('library.categoriesLabel')
})

const formatSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// === Init ===
onMounted(async () => {
  sunLight?.suspendEffects()
  if (props.book?.fileFormat === 'EPUB') {
    await nextTick()
    await initEpub()
  } else if (props.book?.fileFormat === 'TXT') {
    await loadTxt()
  }

  if (userStore.isLoggedIn) {
    try {
      borrow.value = await libraryApi.getBorrow(props.book.id)
      // Restore reading position
      if (borrow.value?.cfi) {
        if (props.book.fileFormat === 'EPUB' && epubRendition) {
          epubRendition.display(borrow.value.cfi)
        } else if (props.book.fileFormat === 'TXT') {
          const page = parseInt(borrow.value.cfi)
          if (!isNaN(page) && page < totalPages.value) txtPage.value = page
        }
      }
    } catch (e) {}
    loadBookmarks()
    // Set status to READING
    if (borrow.value?.status !== 'READING') {
      setBorrowStatus('READING')
    }
  }

  // Observe dark mode changes
  darkObserver = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
    applyEpubTheme()
  })
  darkObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })

  // Keyboard navigation
  document.addEventListener('keydown', onKeyDown)
})

let darkObserver = null
onBeforeUnmount(() => {
  saveProgress()
  sunLight?.restoreEffects()
  if (epubRendition) epubRendition.destroy()
  if (darkObserver) darkObserver.disconnect()
  document.removeEventListener('keydown', onKeyDown)
})

const onKeyDown = (e) => {
  if (e.key === 'ArrowLeft') prevPage()
  else if (e.key === 'ArrowRight') nextPage()
  else if (e.key === 'Escape') {
    if (isFullscreen.value) { isFullscreen.value = false; applyFullscreen() }
    else close()
  }
}

// === EPUB ===
const initEpub = async () => {
  if (!epubRef.value || !props.book?.fileUrl) return
  try {
    const ePub = (await import('epubjs')).default
    epubBook = ePub(props.book.fileUrl)
    epubRendition = epubBook.renderTo(epubRef.value, { width: '100%', height: '100%', allowScriptedContent: true })
    await epubRendition.display()
    applyEpubTheme()

    // Load TOC
    const navigation = await epubBook.loaded.navigation
    toc.value = navigation.toc.map(item => ({ label: item.label.trim(), href: item.href, depth: 0 }))

    // Track location changes
    epubRendition.on('relocated', (location) => {
      const cfi = location.start.cfi
      const percentage = location.start.percentage
      // Update progress
      if (userStore.isLoggedIn) {
        libraryApi.updateBorrow(props.book.id, { progress: Math.round(percentage * 100), cfi }).catch(() => {})
      }
    })
  } catch (e) {
    ElMessage.error(t('library.loadFailed'))
  }
}

const applyEpubTheme = () => {
  if (!epubRendition) return
  const theme = isDark.value ? 'dark' : 'light'
  epubRendition.themes.register('dark', {
    body: { background: '#1a1a2e', color: '#E8DCC8' },
    a: { color: '#d4b298' },
  })
  epubRendition.themes.register('light', {
    body: { background: '#fff', color: '#333' },
  })
  epubRendition.themes.select(theme)
  epubRendition.themes.fontSize(`${fontSize.value}px`)
  epubRendition.themes.override('line-height', lineHeight.value.toString())
}

const onEpubClick = (e) => {
  const rect = contentRef.value?.getBoundingClientRect()
  if (!rect) return
  const x = e.clientX - rect.left
  if (x < rect.width * 0.3) prevPage()
  else if (x > rect.width * 0.7) nextPage()
}

// === TXT ===
const loadTxt = async () => {
  try {
    const res = await fetch(props.book.fileUrl)
    txtContent.value = await res.text()
  } catch (e) {
    ElMessage.error(t('library.loadFailed'))
  }
}

const onTxtClick = (e) => {
  const rect = txtRef.value?.getBoundingClientRect()
  if (!rect) return
  const x = e.clientX - rect.left
  if (x < rect.width * 0.3) prevPage()
  else if (x > rect.width * 0.7) nextPage()
}

// === Navigation ===
const prevPage = () => {
  if (props.book?.fileFormat === 'EPUB' && epubRendition) {
    epubRendition.prev()
  } else if (props.book?.fileFormat === 'TXT') {
    if (txtPage.value > 0) txtPage.value--
  }
}

const nextPage = () => {
  if (props.book?.fileFormat === 'EPUB' && epubRendition) {
    epubRendition.next()
  } else if (props.book?.fileFormat === 'TXT') {
    if (txtPage.value < totalPages.value - 1) txtPage.value++
  }
}

const goToToc = (item) => {
  if (props.book?.fileFormat === 'EPUB' && epubRendition) {
    epubRendition.display(item.href)
  }
  showToc.value = false
}

// === Bookmarks ===
const loadBookmarks = async () => {
  if (!userStore.isLoggedIn) return
  try { bookmarks.value = await libraryApi.getBookmarks(props.book.id) || [] } catch (e) {}
}

const addBookmark = async () => {
  if (!userStore.isLoggedIn) return
  let cfi = ''
  if (props.book.fileFormat === 'EPUB' && epubRendition) {
    cfi = epubRendition.location?.start?.cfi || ''
  } else if (props.book.fileFormat === 'TXT') {
    cfi = String(txtPage.value)
  }
  if (!cfi) return ElMessage.warning(t('library.bookmark'))
  try {
    await libraryApi.addBookmark(props.book.id, { cfi, label: '' })
    ElMessage.success(t('common.saveSuccess'))
    await loadBookmarks()
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  }
}

const deleteBookmark = async (id) => {
  try {
    await libraryApi.deleteBookmark(id)
    await loadBookmarks()
  } catch (e) {}
}

const goToBookmark = (bm) => {
  if (props.book.fileFormat === 'EPUB' && epubRendition) {
    epubRendition.display(bm.cfi)
  } else if (props.book.fileFormat === 'TXT') {
    const page = parseInt(bm.cfi)
    if (!isNaN(page)) txtPage.value = page
  }
  showSidebar.value = false
}

// === Settings ===
const changeFontSize = (delta) => {
  fontSize.value = Math.max(12, Math.min(28, fontSize.value + delta))
  localStorage.setItem('ihomy:reader:fontSize', String(fontSize.value))
  applyEpubTheme()
  applyTxtStyle()
}
const changeLineHeight = (delta) => {
  lineHeight.value = Math.max(1.2, Math.min(3.0, Math.round((lineHeight.value + delta) * 10) / 10))
  localStorage.setItem('ihomy:reader:lineHeight', String(lineHeight.value))
  applyEpubTheme()
  applyTxtStyle()
}
const changeMargin = (delta) => {
  margin.value = Math.max(0, Math.min(100, margin.value + delta))
  localStorage.setItem('ihomy:reader:margin', String(margin.value))
  applyTxtStyle()
}
const applyTxtStyle = () => {
  if (txtRef.value) {
    txtRef.value.style.fontSize = fontSize.value + 'px'
    txtRef.value.style.lineHeight = lineHeight.value
    txtRef.value.style.padding = `0 ${margin.value}px`
  }
}

// === UI Toggles ===
const toggleToc = () => {
  showToc.value = !showToc.value
  if (showToc.value) { showSidebar.value = false }
}
const toggleSettings = () => {
  activePanel.value = 'settings'
  showSidebar.value = !showSidebar.value
  if (showSidebar.value) { showToc.value = false }
}
const toggleBookmarkPanel = () => {
  activePanel.value = 'bookmarks'
  showSidebar.value = !showSidebar.value
  if (showSidebar.value) { showToc.value = false }
}
const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
  applyFullscreen()
}
const applyFullscreen = () => {
  nextTick(() => {
    if (epubRendition) epubRendition.resize()
  })
}

// === Save/Close ===
const saveProgress = async () => {
  if (!userStore.isLoggedIn) return
  let cfi = null
  let progress = null
  if (props.book.fileFormat === 'EPUB' && epubRendition?.location) {
    cfi = epubRendition.location.start?.cfi
    progress = Math.round((epubRendition.location.start?.percentage || 0) * 100)
  } else if (props.book.fileFormat === 'TXT') {
    cfi = String(txtPage.value)
    progress = Math.round((txtPage.value / totalPages.value) * 100)
  }
  if (cfi) {
    try { await libraryApi.updateBorrow(props.book.id, { cfi, progress }) } catch (e) {}
  }
}

const setBorrowStatus = async (status) => {
  try {
    borrow.value = await libraryApi.updateBorrow(props.book.id, { status })
  } catch (e) {}
}

const close = () => {
  saveProgress()
  emit('close')
}

// Watch TXT page to save style
watch(txtPage, () => applyTxtStyle())
</script>

<style scoped>
.reader-overlay { position: fixed; inset: 0; z-index: 200; background: #fff; color: #333; display: flex; flex-direction: column; }
.reader-overlay.dark { background: #1a1a2e; color: #E8DCC8; }

.reader-bar { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; border-bottom: 1px solid rgba(0,0,0,0.08); flex-shrink: 0; min-height: 48px; }
.reader-overlay.dark .reader-bar { border-bottom-color: rgba(255,255,255,0.08); }
.bar-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.bar-right { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }
.reader-title { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.r-btn { width: 34px; height: 34px; border: none; background: transparent; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: inherit; transition: background 0.2s; }
.r-btn:hover { background: rgba(0,0,0,0.06); }
.reader-overlay.dark .r-btn:hover { background: rgba(255,255,255,0.08); }
.r-btn.small { width: 26px; height: 26px; }
.r-btn.primary { background: #b88c6e; color: #fff; padding: 0 12px; width: auto; height: 34px; font-size: 13px; }
.reader-overlay.dark .r-btn.primary { background: #d4b298; color: #2a2018; }

.reader-main { flex: 1; display: flex; overflow: hidden; position: relative; }

.toc-drawer { width: 240px; flex-shrink: 0; border-right: 1px solid rgba(0,0,0,0.08); overflow-y: auto; padding: 10px; }
.reader-overlay.dark .toc-drawer { border-right-color: rgba(255,255,255,0.08); }
.drawer-head { font-size: 13px; font-weight: 600; margin-bottom: 8px; padding: 4px 8px; color: inherit; opacity: 0.7; }
.toc-list { display: flex; flex-direction: column; gap: 2px; }
.toc-item { padding: 6px 8px; border-radius: 6px; cursor: pointer; font-size: 13px; transition: background 0.2s; display: flex; align-items: center; }
.toc-item:hover { background: rgba(0,0,0,0.04); }
.reader-overlay.dark .toc-item:hover { background: rgba(255,255,255,0.06); }
.toc-item.active { background: rgba(184,140,110,0.12); color: #b88c6e; }
.reader-overlay.dark .toc-item.active { background: rgba(212,178,152,0.15); color: #d4b298; }
.toc-indent { flex-shrink: 0; }
.toc-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.toc-empty { font-size: 12px; opacity: 0.5; padding: 12px 8px; text-align: center; }

.reader-content { flex: 1; position: relative; overflow: hidden; }
.pdf-frame { width: 100%; height: 100%; border: none; }
.txt-reader { height: 100%; overflow-y: auto; white-space: pre-wrap; word-wrap: break-word; padding: 0 40px; }
.epub-reader { width: 100%; height: 100%; }
.unsupported { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; font-size: 14px; opacity: 0.6; }

.nav-arrow { position: absolute; top: 50%; transform: translateY(-50%); width: 40px; height: 60px; border: none; background: rgba(0,0,0,0.05); border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: inherit; opacity: 0; transition: opacity 0.2s, background 0.2s; }
.reader-content:hover .nav-arrow { opacity: 0.5; }
.nav-arrow:hover { opacity: 1 !important; background: rgba(0,0,0,0.1); }
.reader-overlay.dark .nav-arrow { background: rgba(255,255,255,0.05); }
.reader-overlay.dark .nav-arrow:hover { background: rgba(255,255,255,0.1); }
.nav-arrow.left { left: 8px; }
.nav-arrow.right { right: 8px; }

.side-drawer { width: 260px; flex-shrink: 0; border-left: 1px solid rgba(0,0,0,0.08); overflow-y: auto; padding: 10px; }
.reader-overlay.dark .side-drawer { border-left-color: rgba(255,255,255,0.08); }
.drawer-content { display: flex; flex-direction: column; gap: 10px; }
.setting-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 8px; }
.set-label { font-size: 13px; }
.set-controls { display: flex; align-items: center; gap: 8px; }
.set-val { font-size: 12px; min-width: 40px; text-align: center; }
.bm-list { display: flex; flex-direction: column; gap: 4px; }
.bm-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 8px; border-radius: 6px; cursor: pointer; font-size: 13px; transition: background 0.2s; }
.bm-item:hover { background: rgba(0,0,0,0.04); }
.reader-overlay.dark .bm-item:hover { background: rgba(255,255,255,0.06); }
.bm-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bm-del { width: 20px; height: 20px; border: none; background: transparent; border-radius: 4px; cursor: pointer; display: flex; align-items: center; justify-content: center; opacity: 0.5; transition: opacity 0.2s; }
.bm-del:hover { opacity: 1; color: #b04a3a; }
.meta-item { display: flex; flex-direction: column; gap: 2px; padding: 4px 8px; }
.mi-label { font-size: 11px; opacity: 0.5; }
.mi-val { font-size: 13px; word-break: break-all; }

.slide-left-enter-active, .slide-left-leave-active { transition: transform 0.25s ease, opacity 0.25s; }
.slide-left-enter-from, .slide-left-leave-to { transform: translateX(-100%); opacity: 0; }
.slide-right-enter-active, .slide-right-leave-active { transition: transform 0.25s ease, opacity 0.25s; }
.slide-right-enter-from, .slide-right-leave-to { transform: translateX(100%); opacity: 0; }

.reader-overlay.fullscreen .reader-bar { display: none; }
.reader-overlay.fullscreen .toc-drawer, .reader-overlay.fullscreen .side-drawer { display: none; }

.reader-float-close { position: fixed; top: 16px; right: 16px; z-index: 210; width: 40px; height: 40px; border: none; border-radius: 50%; background: rgba(0,0,0,0.35); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.2s; backdrop-filter: blur(4px); }
.reader-float-close:hover { background: rgba(0,0,0,0.6); }

@media (max-width: 768px) {
  .toc-drawer, .side-drawer { position: absolute; top: 0; bottom: 0; z-index: 10; background: inherit; width: 80%; max-width: 300px; }
  .toc-drawer { left: 0; border-right: 1px solid rgba(0,0,0,0.1); }
  .side-drawer { right: 0; border-left: 1px solid rgba(0,0,0,0.1); }
}
</style>
