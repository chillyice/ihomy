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
      <p class="share-tip share-lead">{{ $t('shareInbox.lead') }}</p>

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
        <p v-if="payload.skipped" class="share-tip">{{ $t('shareInbox.skipped', { n: payload.skipped }) }}</p>

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

      <div class="share-actions">
        <el-button :disabled="savingText || savingFiles" @click="discard">{{ $t('shareInbox.discard') }}</el-button>
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
import { albumApi, diaryApi, photoApi, wishApi } from '@/api'

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
let offShare = null

const images = computed(() => ((payload.value && payload.value.files) || []).filter((f) => String(f.mime || '').startsWith('image/')))

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
