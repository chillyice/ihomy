<template>
  <!-- 家庭设置:基本信息(户主可编辑) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.family') }}</div>
    <el-form :model="family" label-position="top">
      <el-form-item :label="$t('settings.familyName')">
        <el-input v-model="family.name" />
      </el-form-item>
      <el-form-item :label="$t('settings.description')">
        <el-input v-model="family.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item :label="$t('settings.cover')">
        <div class="upload-row">
          <el-upload :show-file-list="false" :http-request="uploadCover" accept="image/*">
            <el-button>{{ $t('settings.uploadCover') }}</el-button>
          </el-upload>
          <img v-if="family.coverImage" :src="family.coverImage" class="cover-preview" :alt="$t('settings.cover')" />
          <el-button v-if="family.coverImage" link type="danger" @click="family.coverImage = ''">{{ $t('common.remove') }}</el-button>
        </div>
      </el-form-item>
      <el-form-item :label="$t('settings.coverText')">
        <el-input v-model="family.coverText" />
      </el-form-item>
      <el-form-item :label="$t('settings.coverSubtitle')">
        <el-input v-model="family.coverSubtitle" />
      </el-form-item>
      <el-form-item :label="$t('settings.visitorPublic')">
        <el-switch v-model="family.isPublic" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item :label="$t('settings.shareLink')">
        <div class="share-row">
          <el-input v-model="shareUrl" readonly>
            <template #append>
              <el-button class="ghost-btn" @click="copyShare">{{ $t('settings.copy') }}</el-button>
            </template>
          </el-input>
        </div>
        <div class="share-tip">{{ $t('settings.shareTip') }}</div>
      </el-form-item>
      <div class="form-footer">
        <el-button type="primary" :loading="familySaving" @click="saveFamily">{{ $t('settings.saveFamily') }}</el-button>
      </div>
    </el-form>
  </div>

  <BgmSettings />

  <!-- 切换家庭:在当前加入的家庭之间切换(全部主题通用) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('nav.switchFamily') }}</div>
    <div v-if="families.length" class="family-switch-list">
      <div v-for="f in families" :key="f.familyId" class="family-switch-item">
        <span class="family-switch-name">{{ f.name }}</span>
        <el-tag v-if="f.isCurrent" size="small" type="success">{{ $t('settings.currentFamily') }}</el-tag>
        <el-button v-else size="small" type="primary" plain @click="switchFamily(f.familyId)">{{ $t('settings.switchTo') }}</el-button>
      </div>
    </div>
    <el-empty v-else :description="$t('common.empty')" :image-size="40" />
  </div>

  <!-- 创建新家庭:放在家庭设置最下方 -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.createFamily') }}</div>
    <button class="create-family-btn" @click="showCreateFamily = true">{{ $t('settings.createFamily') }}</button>
    <p class="share-tip">{{ $t('settings.createFamilyHint') }}</p>
  </div>

  <!-- 创建新家庭弹窗 -->
  <el-dialog
    v-model="showCreateFamily"
    :title="$t('settings.createFamily')"
    width="380px"
    append-to-body
    @keyup.esc="showCreateFamily = false"
  >
    <div class="fm-hint">{{ $t('settings.createFamilyDialogHint') }}</div>
    <el-input
      ref="familyNameInputRef"
      v-model="newFamilyName"
      :placeholder="$t('settings.familyNamePh')"
      @keyup.enter="confirmCreateFamily"
    />
    <template #footer>
      <el-button @click="showCreateFamily = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :disabled="!newFamilyName.trim() || creatingFamily" :loading="creatingFamily" @click="confirmCreateFamily">
        {{ creatingFamily ? $t('settings.creating') : $t('settings.create') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { familyApi, fileApi, authApi } from '@/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import BgmSettings from './BgmSettings.vue'

const { t } = useI18n()
const userStore = useUserStore()

const family = reactive({
  name: '',
  description: '',
  coverImage: '',
  coverText: '',
  coverSubtitle: '',
  isPublic: 1,
  musicUrl: '',
  musicTitle: '',
})
const shareToken = ref('')
const familySaving = ref(false)

// 家庭分享链接:使用 16 位混淆 token 而非裸家庭 ID,防 ID 遍历
const shareUrl = computed(() => {
  if (!shareToken.value) return ''
  const base = `${location.origin}${location.pathname}`.replace(/\/$/, '')
  return `${base}/?hid=${shareToken.value}`
})

const saveFamily = async () => {
  familySaving.value = true
  try {
    await familyApi.update({ ...family })
    ElMessage.success(t('settings.familySaved'))
  } finally {
    familySaving.value = false
  }
}

const uploadCover = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    family.coverImage = data.url
    ElMessage.success(t('settings.coverUploaded'))
  } catch {
    ElMessage.error(t('settings.uploadFailed'))
  }
}

const copyShare = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success(t('settings.linkCopied'))
  } catch {
    ElMessage.error(t('settings.copyFailed'))
  }
}

const showCreateFamily = ref(false)
const newFamilyName = ref('')
const creatingFamily = ref(false)
const familyNameInputRef = ref(null)

watch(showCreateFamily, (v) => {
  if (v) {
    newFamilyName.value = ''
    nextTick(() => familyNameInputRef.value?.focus?.())
  }
})

const confirmCreateFamily = async () => {
  const name = newFamilyName.value.trim()
  if (!name || creatingFamily.value) return
  creatingFamily.value = true
  try {
    await familyApi.create({ name })
    ElMessage.success(t('settings.familyCreated'))
    location.reload()
  } catch (e) {
    ElMessage.error(e.message || t('settings.createFailed'))
  } finally {
    creatingFamily.value = false
  }
}

// 切换家庭:列出当前账号加入的所有家庭,点击非当前家庭切换(与侧栏切换家庭同口径)
const families = ref([])
const loadFamilies = async () => {
  if (!userStore.isLoggedIn) return
  try {
    families.value = (await authApi.families()) || []
  } catch (e) {
    families.value = []
  }
}
const switchFamily = async (familyId) => {
  try {
    await userStore.switchFamily(familyId, true)
    ElMessage.success(t('nav.switchFamily') + ' ✓')
    location.reload()
  } catch (e) {
    ElMessage.error(e.message || t('common.failed'))
  }
}

onMounted(async () => {
  const f = await familyApi.get().catch(() => null)
  if (f) {
    Object.assign(family, {
      name: f.name || '',
      description: f.description || '',
      coverImage: f.coverImage || '',
      coverText: f.coverText || '',
      coverSubtitle: f.coverSubtitle || '',
      isPublic: f.isPublic ?? 1,
      musicUrl: f.musicUrl || '',
      musicTitle: f.musicTitle || '',
    })
    shareToken.value = f.shareToken || ''
  }
  loadFamilies()
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
  background: var(--color-card);
}
.share-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
html.dark .share-tip {
  color: #9a9088;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.cover-preview {
  max-width: 180px;
  max-height: 90px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--color-border);
}
/* 表单提交按钮:右下角对齐 */
.form-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

/* 创建新家庭按钮 */
.create-family-btn {
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  background: #f3eee6;
  color: #3a2e22;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}
.create-family-btn:hover {
  background: #e8e0d2;
}
html.dark .create-family-btn {
  background: rgba(232, 220, 200, 0.1);
  color: #e8dcc8;
}
html.dark .create-family-btn:hover {
  background: rgba(232, 220, 200, 0.15);
}
.fm-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  margin-bottom: 16px;
}

/* 切换家庭列表 */
.family-switch-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.family-switch-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--color-card-2);
  border-radius: 10px;
}
.family-switch-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
