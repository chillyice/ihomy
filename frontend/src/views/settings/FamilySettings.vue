<template>
  <!-- 家庭设置:基本信息(户主可编辑) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.family') }}</div>
    <el-form :model="family" label-position="top">
      <el-form-item :label="$t('settings.familyName')">
        <el-input v-model="family.name" maxlength="50" />
      </el-form-item>
      <el-form-item :label="$t('settings.description')">
        <el-input v-model="family.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item :label="$t('settings.cover')">
        <div class="upload-row">
          <el-upload ref="coverUploadRef" :show-file-list="false" :auto-upload="false" :on-change="pickCover" accept="image/*">
            <el-button>{{ $t('settings.uploadCover') }}</el-button>
          </el-upload>
          <img v-if="family.coverImage" :src="family.coverImage" class="cover-preview" :alt="$t('settings.cover')" />
          <el-button v-if="family.coverImage" link type="danger" @click="family.coverImage = ''">{{ $t('common.remove') }}</el-button>
        </div>
        <div class="share-tip">{{ $t('settings.coverTip') }}</div>
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

  <!-- 亲友圈:只维护「谁和谁能互相分享」,加入本身不共享任何内容 -->
  <div v-if="userStore.isLoggedIn" class="card settings-card">
    <div class="section-label">{{ t('circle.title') }}</div>
    <p class="share-tip">{{ t('circle.hint') }}</p>

    <!-- 家庭级策略:只有家长能改 -->
    <div v-if="canManageFamily" class="circle-policy">
      <el-form label-position="top">
        <el-form-item :label="t('circle.enabled')">
          <el-switch v-model="family.circleEnabled" :active-value="1" :inactive-value="0" @change="saveCirclePolicy" />
        </el-form-item>
        <el-form-item :label="t('circle.policy')">
          <el-select v-model="family.sharePolicy" style="width: 100%" @change="saveCirclePolicy">
            <el-option value="SELF_ONLY" :label="t('circle.policySelfOnly')" />
            <el-option value="OWNER_ONLY" :label="t('circle.policyOwnerOnly')" />
            <el-option value="OFF" :label="t('circle.policyOff')" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="share-tip">{{ t('circle.policyHint') }}</p>
    </div>

    <div class="circle-toolbar">
      <template v-if="canManageFamily">
        <el-button size="small" @click="showCreateCircle = true">{{ t('circle.create') }}</el-button>
        <el-button size="small" @click="showJoinCircle = true">{{ t('circle.join') }}</el-button>
      </template>
      <span v-else class="share-tip">{{ t('circle.manageOnly') }}</span>
    </div>

    <div v-if="circles.length" class="circle-list">
      <div v-for="c in circles" :key="c.id" class="circle-item">
        <div class="circle-head">
          <span class="circle-name">{{ c.name }}</span>
          <el-tag v-if="c.mine" size="small" type="success">{{ t('circle.ownerTag') }}</el-tag>
          <span class="circle-count">{{ t('circle.familyCount', { n: c.families.length }) }}</span>
        </div>
        <div class="circle-families">
          <span v-for="f in c.families" :key="f.familyId" class="circle-family" :class="{ self: f.self }">
            {{ f.self ? t('circle.selfTag') : f.alias || f.familyName }}
          </span>
        </div>
        <div class="circle-actions">
          <el-button v-if="c.mine && canManageFamily" size="small" @click="generateInvite(c)">{{ t('circle.invite') }}</el-button>
          <el-button v-if="canManageFamily" size="small" @click="editAlias(c)">{{ t('circle.alias') }}</el-button>
          <el-button v-if="c.mine && canManageFamily" size="small" type="danger" plain @click="disbandCircle(c)">
            {{ t('circle.disband') }}
          </el-button>
          <el-button v-else-if="canManageFamily" size="small" type="danger" plain @click="leaveCircle(c)">
            {{ t('circle.leave') }}
          </el-button>
          <template v-if="c.mine && canManageFamily">
            <el-button
              v-for="f in c.families.filter((x) => !x.self)"
              :key="f.familyId"
              size="small"
              text
              type="danger"
              @click="removeFamily(c, f)"
            >
              {{ t('circle.removeFamily') }} {{ f.alias || f.familyName }}
            </el-button>
          </template>
        </div>
      </div>
    </div>
    <el-empty v-else :description="t('circle.empty')" :image-size="40" />
  </div>

  <!-- 分享记录:本家庭分享给其他家庭的内容,可随时收回 -->
  <div v-if="userStore.isLoggedIn" class="card settings-card">
    <div class="section-label">{{ t('circle.auditTitle') }}</div>
    <p class="share-tip">{{ t('circle.auditHint') }}</p>
    <template v-if="audit.items.length">
      <div class="audit-list">
        <div v-for="row in audit.items" :key="row.id" class="audit-row">
          <span class="audit-kind">{{ kindLabel(row.contentType) }}</span>
          <span class="audit-to">{{ t('circle.sharedTo', { name: row.targetFamilyName || '—' }) }}</span>
          <span v-if="row.circleName" class="audit-circle">{{ row.circleName }}</span>
          <span class="audit-time">{{ formatDateTime(row.createdAt) }}</span>
        </div>
      </div>
      <el-pagination
        v-if="audit.total > audit.size"
        class="audit-pager"
        small
        layout="prev, pager, next"
        :current-page="audit.page"
        :page-size="audit.size"
        :total="audit.total"
        @current-change="loadAudit"
      />
      <div v-if="canManageFamily" class="form-footer">
        <el-button type="danger" plain @click="revokeAllShares">{{ t('circle.revokeAll') }}</el-button>
      </div>
    </template>
    <el-empty v-else :description="t('circle.auditEmpty')" :image-size="40" />
  </div>

  <!-- 新建亲友圈 -->
  <el-dialog v-model="showCreateCircle" :title="t('circle.create')" width="380px" append-to-body @keyup.esc="showCreateCircle = false">
    <p class="fm-hint">{{ t('circle.createHint') }}</p>
    <el-input
      ref="circleNameInputRef"
      v-model="newCircleName"
      :placeholder="t('circle.namePlaceholder')"
      maxlength="30"
      @keyup.enter="confirmCreateCircle"
    />
    <template #footer>
      <el-button @click="showCreateCircle = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :disabled="!newCircleName.trim() || creatingCircle" :loading="creatingCircle" @click="confirmCreateCircle">
        {{ t('settings.create') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 凭邀请码加入 -->
  <el-dialog v-model="showJoinCircle" :title="t('circle.join')" width="380px" append-to-body @keyup.esc="showJoinCircle = false">
    <p class="fm-hint">{{ t('circle.joinHint') }}</p>
    <el-input v-model="joinCode" :placeholder="t('circle.codePlaceholder')" maxlength="8" @keyup.enter="confirmJoinCircle" />
    <template #footer>
      <el-button @click="showJoinCircle = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :disabled="!joinCode.trim() || joiningCircle" :loading="joiningCircle" @click="confirmJoinCircle">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 邀请码展示 -->
  <el-dialog v-model="inviteDialog.visible" :title="t('circle.invite')" width="380px" append-to-body>
    <p class="fm-hint">{{ t('circle.inviteHint') }}</p>
    <el-input v-model="inviteDialog.code" readonly>
      <template #append>
        <el-button class="ghost-btn" @click="copyInviteCode">{{ t('circle.copy') }}</el-button>
      </template>
    </el-input>
    <p class="share-tip">{{ t('circle.inviteExpires', { time: formatDateTime(inviteDialog.expiresAt) }) }}</p>
    <template #footer>
      <el-button type="primary" @click="inviteDialog.visible = false">{{ t('common.close') }}</el-button>
    </template>
  </el-dialog>

  <BgmSettings />

  <!-- 切换家庭:在当前加入的家庭之间切换(全部主题通用) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('nav.switchFamily') }}</div>
    <div v-if="families.length" class="family-switch-list">
      <div v-for="f in families" :key="f.familyId" class="family-switch-item">
        <el-avatar :size="56" shape="square" :src="f.coverImage || undefined" class="family-switch-cover">{{
          (f.name || 'i').charAt(0)
        }}</el-avatar>
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

  <!-- 封面裁剪:横图 16:9,与顶栏相框/移动端横幅同口径 -->
  <AvatarCropper
    ref="coverCropperRef"
    :ratio="16 / 9"
    :output-width="1280"
    mime-type="image/jpeg"
    file-name="cover"
    :title="$t('settings.coverCropTitle')"
    :cancel-text="$t('common.cancel')"
    :confirm-text="$t('common.confirm')"
    @cropped="onCoverCropped"
  />
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { familyApi, fileApi, authApi, circleApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import BgmSettings from './BgmSettings.vue'
import AvatarCropper from '@/components/AvatarCropper.vue'
import { formatDateTime } from '@/utils/datetime'

const { t } = useI18n()
const userStore = useUserStore()
const appStore = useAppStore()

const family = reactive({
  name: '',
  description: '',
  coverImage: '',
  isPublic: 1,
  musicUrl: '',
  musicTitle: '',
  circleEnabled: 1,
  sharePolicy: 'SELF_ONLY',
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
    // 封面「移除」提交的是空串(null 在本接口表示「不修改」),后端把空白归一成 NULL 并删除旧文件
    const updated = await familyApi.update({ ...family })
    // 顶栏/侧栏展示的家庭名与简介来自 appStore,保存后当场同步,不必等刷新
    if (appStore.family && updated) Object.assign(appStore.family, updated)
    ElMessage.success(t('settings.familySaved'))
  } finally {
    familySaving.value = false
  }
}

// ---------- 亲友圈 ----------
const canManageFamily = computed(() => userStore.hasPerm('family:manage'))
const circles = ref([])
const showCreateCircle = ref(false)
const newCircleName = ref('')
const creatingCircle = ref(false)
const circleNameInputRef = ref(null)
const showJoinCircle = ref(false)
const joinCode = ref('')
const joiningCircle = ref(false)
const inviteDialog = reactive({ visible: false, code: '', expiresAt: '' })

const loadCircles = async () => {
  if (!userStore.isLoggedIn) return
  try {
    circles.value = (await circleApi.list()) || []
  } catch {
    circles.value = []
  }
}

// 家庭级策略:只提交这两项,不动基本信息(后端按字段部分更新)
const saveCirclePolicy = async () => {
  try {
    await familyApi.update({ circleEnabled: family.circleEnabled, sharePolicy: family.sharePolicy })
    ElMessage.success(t('circle.saved'))
  } catch {
    // 具体原因由请求层提示
  }
}

watch(showCreateCircle, (v) => {
  if (v) {
    newCircleName.value = ''
    nextTick(() => circleNameInputRef.value?.focus?.())
  }
})

const confirmCreateCircle = async () => {
  const name = newCircleName.value.trim()
  if (!name || creatingCircle.value) return
  creatingCircle.value = true
  try {
    const created = await circleApi.create({ name })
    showCreateCircle.value = false
    ElMessage.success(t('circle.created'))
    await loadCircles()
    if (created?.inviteCode) {
      inviteDialog.code = created.inviteCode
      inviteDialog.expiresAt = created.inviteExpiresAt || ''
      inviteDialog.visible = true
    }
  } catch {
    // 请求层已提示(如已达上限)
  } finally {
    creatingCircle.value = false
  }
}

const confirmJoinCircle = async () => {
  const code = joinCode.value.trim()
  if (!code || joiningCircle.value) return
  joiningCircle.value = true
  try {
    await circleApi.join(code)
    showJoinCircle.value = false
    joinCode.value = ''
    ElMessage.success(t('circle.joined'))
    loadCircles()
  } catch {
    // 请求层已提示(邀请码无效/已过期/圈已满)
  } finally {
    joiningCircle.value = false
  }
}

const generateInvite = async (c) => {
  try {
    const data = await circleApi.invite(c.id)
    inviteDialog.code = data.code
    inviteDialog.expiresAt = data.expiresAt
    inviteDialog.visible = true
  } catch {
    /* 请求层已提示 */
  }
}

const copyInviteCode = async () => {
  try {
    await navigator.clipboard.writeText(inviteDialog.code)
    ElMessage.success(t('circle.copied'))
  } catch {
    ElMessage.error(t('circle.copyFailed'))
  }
}

const editAlias = async (c) => {
  try {
    const { value } = await ElMessageBox.prompt(t('circle.aliasHint'), t('circle.alias'), {
      inputValue: c.myAlias || '',
      inputPlaceholder: t('circle.alias'),
      confirmButtonText: t('common.save'),
      cancelButtonText: t('common.cancel'),
    })
    await circleApi.setAlias(c.id, (value || '').trim())
    ElMessage.success(t('circle.aliasSaved'))
    loadCircles()
  } catch {
    /* 取消或请求层已提示 */
  }
}

const leaveCircle = async (c) => {
  try {
    await ElMessageBox.confirm(t('circle.leaveConfirm', { name: c.name }), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await circleApi.leave(c.id)
    ElMessage.success(t('circle.left'))
    loadCircles()
    loadAudit(1)
  } catch {
    /* 请求层已提示 */
  }
}

const disbandCircle = async (c) => {
  try {
    await ElMessageBox.confirm(t('circle.disbandConfirm', { name: c.name }), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await circleApi.disband(c.id)
    ElMessage.success(t('circle.disbanded'))
    loadCircles()
    loadAudit(1)
  } catch {
    /* 请求层已提示 */
  }
}

const removeFamily = async (c, f) => {
  const name = f.alias || f.familyName || ''
  try {
    await ElMessageBox.confirm(t('circle.removeConfirm', { name }), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await circleApi.removeFamily(c.id, f.familyId)
    ElMessage.success(t('circle.removed'))
    loadCircles()
    loadAudit(1)
  } catch {
    /* 请求层已提示 */
  }
}

// ---------- 分享记录 ----------
const audit = reactive({ items: [], total: 0, page: 1, size: 10 })
const KIND_LABELS = {
  photo: 'circle.kindPhoto',
  album: 'circle.kindAlbum',
  video: 'circle.kindVideo',
  blog: 'circle.kindBlog',
  diary: 'circle.kindDiary',
  wish: 'circle.kindWish',
  book: 'circle.kindBook',
}
const kindLabel = (code) => (KIND_LABELS[code] ? t(KIND_LABELS[code]) : code)

const loadAudit = async (page = 1) => {
  if (!userStore.isLoggedIn) return
  try {
    const data = await familyApi.shareAudit({ page, size: audit.size })
    audit.items = data.items || []
    audit.total = data.total || 0
    audit.page = data.page || page
    audit.size = data.size || audit.size
  } catch {
    audit.items = []
    audit.total = 0
  }
}

const revokeAllShares = async () => {
  try {
    await ElMessageBox.confirm(t('circle.revokeAllConfirm'), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const data = await familyApi.revokeAllShares()
    ElMessage.success(t('circle.revokedAll', { n: data.revoked ?? 0 }))
    loadAudit(1)
  } catch {
    /* 请求层已提示 */
  }
}

const coverUploadRef = ref(null)
const coverCropperRef = ref(null)

// 选图后先进裁剪(16:9),确认才上传;取消不发请求
const pickCover = (uploadFile) => {
  const raw = uploadFile?.raw
  if (!raw) return
  if (raw.type && !raw.type.startsWith('image/')) {
    ElMessage.error(t('settings.uploadFailed'))
    return
  }
  // 清空 el-upload 内部列表:再选同一张图也能触发 on-change
  coverUploadRef.value?.clearFiles()
  coverCropperRef.value?.open(raw)
}

const onCoverCropped = async (file) => {
  try {
    const data = await fileApi.upload(file)
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
      isPublic: f.isPublic ?? 1,
      musicUrl: f.musicUrl || '',
      musicTitle: f.musicTitle || '',
      circleEnabled: f.circleEnabled ?? 1,
      sharePolicy: f.sharePolicy || 'SELF_ONLY',
    })
    shareToken.value = f.shareToken || ''
  }
  loadFamilies()
  loadCircles()
  loadAudit(1)
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
/* 家庭封面(无封面时 el-avatar 回落首字母):圆角与卡片口径一致 */
.family-switch-cover {
  border-radius: 14px;
  flex-shrink: 0;
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

/* ---------- 亲友圈 ---------- */
.circle-policy {
  margin: 12px 0 4px;
  padding: 12px 14px 4px;
  border-radius: 12px;
  background: var(--color-card-2);
}
.circle-policy :deep(.el-form-item) {
  margin-bottom: 12px;
}
.circle-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}
.circle-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.circle-item {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-card-2);
  border: 1px solid var(--color-border);
}
.circle-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.circle-name {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--color-text);
}
.circle-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.circle-families {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.circle-family {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  color: var(--color-text-secondary);
  background: rgba(var(--color-brand-rgb), 0.08);
}
.circle-family.self {
  color: var(--color-brand);
  background: rgba(var(--color-brand-rgb), 0.16);
}
.circle-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}

/* ---------- 分享记录 ---------- */
.audit-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
}
.audit-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--color-text);
}
.audit-row + .audit-row {
  border-top: 1px solid var(--color-border);
}
.audit-kind {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 8px;
  background: rgba(var(--color-brand-rgb), 0.1);
  color: var(--color-brand);
}
.audit-to {
  font-weight: 500;
}
.audit-circle {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.audit-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.audit-pager {
  margin-top: 10px;
  justify-content: center;
}

@media (max-width: 768px) {
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
