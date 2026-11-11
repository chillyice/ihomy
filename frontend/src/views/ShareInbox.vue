<!-- 分享到家庭:接住系统分享面板带进来的文字/图片,由用户选落点(愿望/日记/相册)。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('shareInbox.title') }]" />

    <div v-if="!payload" class="card share-empty">
      <div class="section-label">{{ $t('shareInbox.title') }}</div>
      <p class="share-tip">{{ $t('shareInbox.empty') }}</p>
      <el-button @click="goHome">{{ $t('shareInbox.backHome') }}</el-button>
    </div>

    <template v-else>
      <p class="share-tip share-lead">{{ payload.mode === 'open' ? $t('shareInbox.openLead') : $t('shareInbox.lead') }}</p>

      <div v-if="text" class="card share-card">
        <div class="section-label">{{ $t('shareInbox.textTitle') }}</div>
        <el-input v-model="text" type="textarea" :rows="5" maxlength="20000" show-word-limit />
        <div class="share-row">
          <el-radio-group v-model="textTarget">
            <el-radio value="wish">{{ $t('shareInbox.toWish') }}</el-radio>
            <el-radio value="diary">{{ $t('shareInbox.toDiary') }}</el-radio>
          </el-radio-group>
        </div>
        <el-input
          v-if="textTarget === 'wish'"
          v-model="wishTitle"
          maxlength="120"
          show-word-limit
          :placeholder="$t('shareInbox.wishTitlePlaceholder')"
          class="share-title"
        />
        <div class="share-row">
          <el-button type="primary" :loading="savingText" :disabled="savingFiles" @click="saveText">
            {{ $t('shareInbox.saveText') }}
          </el-button>
        </div>
      </div>

      <div v-if="images.length" class="card share-card">
        <div class="section-label">{{ $t('shareInbox.filesTitle', { n: images.length }) }}</div>
        <div class="share-thumbs">
          <div v-for="(f, i) in images" :key="i" class="share-thumb">
            <img v-if="f.thumb" :src="f.thumb" :alt="f.name" />
            <span v-else class="share-thumb-name">{{ f.name }}</span>
          </div>
        </div>
        <p v-if="payload.skippedImages" class="share-tip">{{ $t('shareInbox.skippedImages', { n: payload.skippedImages }) }}</p>

        <div class="share-row">
          <el-select v-model="albumId" :placeholder="$t('shareInbox.albumPick')" class="share-album">
            <el-option v-for="a in albums" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
        </div>
        <div class="share-row">
          <el-input v-model="newAlbumName" :placeholder="$t('shareInbox.albumNewPlaceholder')" maxlength="40" />
        </div>
        <div class="share-row">
          <el-button type="primary" :loading="savingFiles" :disabled="savingText" @click="saveImages">
            {{ savingFiles ? $t('shareInbox.uploading', { i: uploaded, n: images.length }) : $t('shareInbox.saveImages') }}
          </el-button>
        </div>
      </div>

      <div v-if="docs.length" class="card share-card">
        <div class="section-label">{{ $t('shareInbox.docsTitle', { n: docs.length }) }}</div>
        <ul class="share-docs">
          <li v-for="d in docs" :key="d.index" class="share-doc">
            <div class="share-doc-info">
              <span class="share-doc-name">{{ d.name }}</span>
              <span class="share-doc-meta">{{ humanSize(d.size) }}</span>
            </div>
            <span v-if="targetOf(d) === 'unsupported'" class="share-doc-note">{{ $t('shareInbox.unsupportedType') }}</span>
            <span v-else-if="savedDocs.includes(d.index)" class="share-doc-note">
              {{ targetOf(d) === 'video' ? $t('shareInbox.savedToVideo') : $t('shareInbox.savedToLibrary') }}
            </span>
            <el-button
              v-else
              :loading="savingDocIndex === d.index"
              :disabled="savingText || savingFiles || savingAllDocs"
              @click="saveDoc(d)"
            >
              {{ targetOf(d) === 'video' ? $t('shareInbox.saveToVideo') : $t('shareInbox.saveToLibrary') }}
            </el-button>
          </li>
        </ul>
        <p v-if="payload.skippedDocs" class="share-tip">{{ $t('shareInbox.skippedDocs', { n: payload.skippedDocs }) }}</p>
        <div v-if="supportedDocs.length > 1" class="share-row">
          <el-button type="primary" :loading="savingAllDocs" :disabled="savingText || savingFiles" @click="saveAllDocs">
            {{ savingAllDocs ? $t('shareInbox.preparing') : $t('shareInbox.saveAll') }}
          </el-button>
        </div>
      </div>

      <div class="share-actions">
        <el-button :disabled="savingText || savingFiles || savingAllDocs" @click="discard">{{ $t('shareInbox.discard') }}</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { albumApi, diaryApi, libraryApi, photoApi, videoApi, wishApi } from '@/api'

const { t } = useI18n()
const router = useRouter()

const payload = ref(null)
const text = ref('')
const textTarget = ref('wish')
const wishTitle = ref('')
const albums = ref([])
const albumId = ref(null)
const newAlbumName = ref('')
const savingText = ref(false)
const savingFiles = ref(false)
const uploaded = ref(0)
/** 正在逐个保存的文档下标(null=没在跑) */
const savingDocIndex = ref(null)
const savingAllDocs = ref(false)
/** 已落到站内的文档下标,避免重复保存 */
const savedDocs = ref([])
let offShare = null

const images = computed(() => ((payload.value && payload.value.files) || []).filter((f) => String(f.mime || '').startsWith('image/')))

/** 文档 + 视频(除图片外的所有条目);图片仍走相册卡 */
const docs = computed(() => ((payload.value && payload.value.files) || []).filter((f) => !String(f.mime || '').startsWith('image/')))

const supportedDocs = computed(() => docs.value.filter((d) => targetOf(d) !== 'unsupported'))

/** 落点判定:视频 → 放映厅;PDF/EPUB/TXT/MOBI → 图书馆;其余明确说不支持 */
function targetOf(file) {
  const mime = String(file.mime || '').toLowerCase()
  const name = String(file.name || '').toLowerCase()
  if (mime.startsWith('video/')) return 'video'
  if (
    mime === 'application/pdf' ||
    mime === 'application/epub+zip' ||
    mime === 'text/plain' ||
    mime === 'application/x-mobipocket-ebook' ||
    mime === 'application/vnd.amazon.ebook' ||
    /\.(pdf|epub|txt|mobi)$/.test(name)
  ) {
    return 'library'
  }
  return 'unsupported'
}

/** 大小展示统一口径:1.2 MB;取不到大小给 — */
function humanSize(n) {
  const v = Number(n)
  if (!Number.isFinite(v) || v < 0) return '—'
  if (v < 1024) return `${v} B`
  const units = ['KB', 'MB', 'GB']
  let size = v / 1024
  let i = 0
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[i]}`
}

/** 书名/片名默认取文件名(去扩展名),用户不必先起名 */
function titleOf(name) {
  const base = String(name || '')
    .replace(/\.[^.]+$/, '')
    .trim()
  return base.slice(0, 120) || t('shareInbox.untitled')
}

/** 分享文字第一行做愿望标题(超长截断),用户可改 */
function defaultWishTitle(raw) {
  const line = String(raw || '')
    .split('\n')[0]
    .trim()
  return line.slice(0, 60)
}

onMounted(async () => {
  const { loadSharePayload, onSharePayload } = await import('@/native/shareInbox')
  const p = await loadSharePayload().catch(() => null)
  if (p) applyPayload(p)
  offShare = onSharePayload((next) => applyPayload(next))
  await loadAlbums()
})

onUnmounted(() => {
  if (offShare) offShare()
})

function applyPayload(p) {
  payload.value = p
  text.value = p.text || ''
  wishTitle.value = defaultWishTitle(p.text)
  // 愿望的「理由」后端限 500 字,长文默认落到日记
  textTarget.value = String(p.text || '').length > 500 ? 'diary' : 'wish'
  uploaded.value = 0
  savedDocs.value = []
}

async function loadAlbums() {
  try {
    const list = await albumApi.list()
    albums.value = Array.isArray(list) ? list : (list && list.items) || []
  } catch (e) {
    console.warn('[shareInbox] 相册列表加载失败', e)
  }
}

async function finish(to) {
  const { clearSharePayload } = await import('@/native/shareInbox')
  await clearSharePayload()
  ElMessage.success(t('shareInbox.saveOk'))
  router.replace(to)
}

function goHome() {
  router.replace('/')
}

async function saveText() {
  const content = text.value.trim()
  if (!content) return ElMessage.warning(t('shareInbox.textRequired'))
  savingText.value = true
  try {
    if (textTarget.value === 'wish') {
      const title = wishTitle.value.trim()
      if (!title) return ElMessage.warning(t('shareInbox.titleRequired'))
      await wishApi.create({ title, reason: content.slice(0, 500), category: '' })
      await finish('/wish')
    } else {
      await diaryApi.create({
        content,
        mood: '',
        weather: '',
        date: nowText(),
        visibility: 'PRIVATE',
        doodle: null,
      })
      await finish('/diary')
    }
  } catch (e) {
    console.warn('[shareInbox] 保存分享文字失败', e)
    ElMessage.error(t('shareInbox.fail'))
  } finally {
    savingText.value = false
  }
}

async function saveImages() {
  if (!images.value.length) return
  savingFiles.value = true
  uploaded.value = 0
  try {
    let targetId = albumId.value
    if (!targetId) {
      const name = newAlbumName.value.trim()
      if (!name) return ElMessage.warning(t('shareInbox.albumRequired'))
      const created = await albumApi.create({ name, type: 'public' })
      targetId = created && created.id
      if (!targetId) throw new Error('album id missing')
      albumId.value = targetId
    }
    const { readShareFile } = await import('@/native/shareInbox')
    const files = []
    for (const f of images.value) {
      files.push(await readShareFile(f))
      uploaded.value = files.length
    }
    await photoApi.upload(targetId, files)
    await finish(`/album/${targetId}`)
  } catch (e) {
    console.warn('[shareInbox] 保存分享图片失败', e)
    ElMessage.error(t('shareInbox.fail'))
  } finally {
    savingFiles.value = false
  }
}

/** 单个文档/视频:独立 try,失败只影响这一行 */
async function saveDoc(doc) {
  if (savingAllDocs.value) return
  const target = targetOf(doc)
  if (target === 'unsupported' || savedDocs.value.includes(doc.index)) return
  savingDocIndex.value = doc.index
  try {
    await saveOneDoc(doc, target)
  } finally {
    savingDocIndex.value = null
  }
  // 只剩这一份(或其余都已存过)才跳走,免得把没保存的丢下
  if (supportedDocs.value.every((d) => savedDocs.value.includes(d.index))) {
    await finish(target === 'video' ? '/cinema' : '/library')
  }
}

/** 全部保存:逐个跑,一个失败不影响其余;全部成功才跳转 */
async function saveAllDocs() {
  if (savingAllDocs.value) return
  savingAllDocs.value = true
  let lastTarget = null
  try {
    for (const doc of supportedDocs.value) {
      if (savedDocs.value.includes(doc.index)) continue
      savingDocIndex.value = doc.index
      const target = targetOf(doc)
      if (target === 'unsupported') continue
      const ok = await saveOneDoc(doc, target)
      if (ok) lastTarget = target
    }
  } finally {
    savingDocIndex.value = null
    savingAllDocs.value = false
  }
  if (lastTarget && supportedDocs.value.every((d) => savedDocs.value.includes(d.index))) {
    await finish(lastTarget === 'video' ? '/cinema' : '/library')
  }
}

/** 落一份:取文件 → 上传 → 建记录;返回是否成功 */
async function saveOneDoc(doc, target) {
  try {
    const { readShareFile } = await import('@/native/shareInbox')
    const file = await readShareFile(doc)
    if (target === 'video') {
      const up = await videoApi.upload(file)
      // 分享入库与站内上传同口径:mediaType 默认电影,VIS_FAMILY 由服务端强制
      await videoApi.create({ title: titleOf(doc.name), videoUrl: up && up.url, mediaType: 'movie' })
    } else {
      const up = await libraryApi.upload(file)
      await libraryApi.create({
        title: titleOf(doc.name),
        fileUrl: up && up.url,
        fileFormat: up && up.fileFormat,
        fileSize: up && up.fileSize,
        // 不赌服务端默认值,分享入库显式落家庭可见
        visibility: 'FAMILY',
      })
    }
    if (!savedDocs.value.includes(doc.index)) savedDocs.value.push(doc.index)
    ElMessage.success(t(target === 'video' ? 'shareInbox.savedToVideo' : 'shareInbox.savedToLibrary'))
    return true
  } catch (e) {
    console.warn('[shareInbox] 保存分享文件失败', doc.name, e)
    const msg = String((e && (e.message || e.code)) || '')
    if (/too_large/i.test(msg)) ElMessage.error(t('shareInbox.docTooLarge'))
    else ElMessage.error(t('shareInbox.fail'))
    return false
  }
}

async function discard() {
  const { clearSharePayload } = await import('@/native/shareInbox')
  await clearSharePayload()
  router.replace('/')
}

function nowText() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.share-lead {
  margin: 0 0 12px;
}
.share-card {
  padding: 16px;
  margin-bottom: 14px;
}
.share-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.share-title {
  margin-top: 10px;
}
.share-album {
  width: 100%;
  max-width: 320px;
}
.share-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}
.share-thumb {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-bg, #f2efe9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--color-text-secondary, #8a8a8a);
}
.share-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.share-thumb-name {
  padding: 6px;
  text-align: center;
  word-break: break-all;
}
.share-docs {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
}
.share-doc {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border, #eee7dc);
}
.share-doc:last-child {
  border-bottom: none;
}
.share-doc-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.share-doc-name {
  font-size: 13px;
  color: var(--color-text, #333);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-doc-meta {
  font-size: 11px;
  color: var(--color-text-secondary, #8a8a8a);
}
.share-doc-note {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  flex: none;
}
.share-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
.share-empty {
  padding: 18px;
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  line-height: 1.5;
}
html.dark .share-tip {
  color: #9a9088;
}
</style>
