<!-- 家庭公告页:家长维护图片横幅+链接,成员与公开家庭访客可读 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: t('announcement.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span v-if="!userStore.isOwner" class="ann-hint">{{ t('announcement.manageHint') }}</span>
      </div>
      <div class="tb-right">
        <el-button v-if="userStore.isOwner" type="primary" @click="openEditor()">{{ t('announcement.add') }}</el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading">
      <div v-if="list.length" class="ann-grid">
        <div v-for="a in list" :key="a.id" class="ann-card card" :class="{ off: a.enabled === 0 }">
          <div v-a11y-click class="ann-banner" :class="{ clickable: !!a.linkUrl }" @click="openLink(a)">
            <img v-if="a.imageUrl" :src="a.imageUrl" :alt="a.title" loading="lazy" />
            <div v-else class="ann-fallback">{{ a.title }}</div>
            <span v-if="userStore.isOwner && statusText(a)" class="ann-status" :class="statusClass(a)">{{ statusText(a) }}</span>
          </div>
          <div class="ann-body">
            <div class="ann-title">
              <span>{{ a.title }}</span>
              <el-icon v-if="a.linkUrl" class="ann-link-icon"><Link /></el-icon>
            </div>
            <div v-if="a.linkUrl" class="ann-link">{{ a.linkUrl }}</div>
          </div>
          <div v-if="userStore.isOwner" class="ann-actions">
            <el-tooltip :content="t('common.edit')" placement="top" :show-after="300">
              <el-button size="small" text @click="openEditor(a)"
                ><el-icon><Edit /></el-icon
              ></el-button>
            </el-tooltip>
            <el-tooltip :content="t('common.delete')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="onDel(a)"
                ><el-icon><Delete /></el-icon
              ></el-button>
            </el-tooltip>
          </div>
        </div>
      </div>
      <el-empty v-else :description="userStore.isGuest ? t('announcement.guestEmpty') : t('announcement.empty')" />
    </div>

    <el-dialog
      v-model="editor.visible"
      append-to-body
      :title="editor.form.id ? t('announcement.edit') : t('announcement.add')"
      width="520px"
    >
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="t('announcement.name')">
          <el-input v-model="editor.form.title" :placeholder="t('announcement.namePlaceholder')" maxlength="100" />
        </el-form-item>
        <el-form-item :label="t('announcement.image')">
          <div class="img-uploader">
            <el-upload :show-file-list="false" :before-upload="onUpload" accept="image/*">
              <img v-if="editor.form.imageUrl" :src="editor.form.imageUrl" class="img-preview" :alt="t('announcement.image')" />
              <div v-else class="img-btn">{{ t('announcement.uploadImage') }}</div>
            </el-upload>
            <div class="img-side">
              <p class="ann-hint">{{ t('announcement.imageHint') }}</p>
              <el-button v-if="editor.form.imageUrl" link type="danger" @click="editor.form.imageUrl = ''">{{
                t('common.delete')
              }}</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="t('announcement.link')">
          <el-input v-model="editor.form.linkUrl" :placeholder="t('announcement.linkPlaceholder')" />
        </el-form-item>
        <div class="form-row">
          <el-form-item :label="t('announcement.startDate')">
            <el-date-picker
              v-model="editor.form.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              :placeholder="t('announcement.dateHint')"
            />
          </el-form-item>
          <el-form-item :label="t('announcement.endDate')">
            <el-date-picker
              v-model="editor.form.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              :placeholder="t('announcement.dateHint')"
            />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="t('announcement.sortOrder')">
            <el-input-number v-model="editor.form.sortOrder" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="t('announcement.enabled')">
            <el-switch v-model="editor.form.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { announcementApi, fileApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Link } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const list = ref([])
const loading = ref(false)
// 编辑框状态:form 同时承担新增与编辑(以 id 区分)
const editor = reactive({
  visible: false,
  form: { id: null, title: '', imageUrl: '', linkUrl: '', sortOrder: 0, enabled: 1, startDate: null, endDate: null },
})

const load = async () => {
  loading.value = true
  try {
    const params = {}
    if (route.query.hid) params.hid = route.query.hid
    else if (route.query.home_id) params.home_id = route.query.home_id
    list.value = await announcementApi.list(Object.keys(params).length ? params : undefined)
  } finally {
    loading.value = false
  }
}

const todayStr = () => new Date().toISOString().slice(0, 10)
// 家长可见的排期状态;生效中返回空串(不显示角标)
const statusText = (a) => {
  if (a.enabled === 0) return t('announcement.disabled')
  const today = todayStr()
  if (a.startDate && a.startDate > today) return t('announcement.scheduled')
  if (a.endDate && a.endDate < today) return t('announcement.expired')
  return ''
}
const statusClass = (a) => {
  if (a.enabled === 0) return 'off'
  const today = todayStr()
  if (a.startDate && a.startDate > today) return 'scheduled'
  if (a.endDate && a.endDate < today) return 'expired'
  return 'on'
}

const openLink = (a) => {
  if (a.linkUrl) window.open(a.linkUrl, '_blank', 'noopener')
}

// 上传横幅:回填 URL,返回 false 阻止 el-upload 默认提交
const onUpload = async (file) => {
  const data = await fileApi.upload(file)
  editor.form.imageUrl = data.url
  return false
}

const openEditor = (a) => {
  if (a) {
    Object.assign(editor.form, {
      id: a.id,
      title: a.title,
      imageUrl: a.imageUrl || '',
      linkUrl: a.linkUrl || '',
      sortOrder: a.sortOrder ?? 0,
      enabled: a.enabled,
      startDate: a.startDate || null,
      endDate: a.endDate || null,
    })
  } else {
    Object.assign(editor.form, { id: null, title: '', imageUrl: '', linkUrl: '', sortOrder: 0, enabled: 1, startDate: null, endDate: null })
  }
  editor.visible = true
}

const onSave = async () => {
  if (!editor.form.title) return ElMessage.warning(t('announcement.nameRequired'))
  if (editor.form.id) await announcementApi.update(editor.form.id, editor.form)
  else await announcementApi.create(editor.form)
  ElMessage.success(t('announcement.saved'))
  editor.visible = false
  load()
}

const onDel = async (a) => {
  await ElMessageBox.confirm(t('announcement.deleteConfirm', { name: a.title }), t('common.tip'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  await announcementApi.remove(a.id)
  ElMessage.success(t('common.deleted'))
  load()
}

onMounted(load)
</script>

<style scoped>
.ann-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.ann-card {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.ann-card.off {
  opacity: 0.55;
}
.ann-banner {
  position: relative;
  aspect-ratio: 16 / 7;
  background: rgba(31, 58, 95, 0.06);
  overflow: hidden;
}
.ann-banner.clickable {
  cursor: pointer;
}
.ann-banner img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s;
}
.ann-banner.clickable:hover img {
  transform: scale(1.03);
}
.ann-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
}
.ann-status {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  color: #fff;
}
.ann-status.off {
  background: rgba(0, 0, 0, 0.55);
}
.ann-status.expired {
  background: rgba(120, 120, 120, 0.85);
}
.ann-status.scheduled {
  background: rgba(230, 162, 60, 0.9);
}
.ann-body {
  padding: 12px 16px;
}
.ann-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.ann-link {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  word-break: break-all;
}
.ann-link-icon {
  color: var(--color-accent);
}
.ann-actions {
  text-align: right;
  padding: 4px 12px 10px;
}
.ann-actions :deep(.el-button) {
  padding: 5px 6px;
}
.ann-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}
.ann-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.img-uploader {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.img-preview {
  width: 200px;
  height: 88px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  display: block;
}
.img-btn {
  width: 200px;
  height: 88px;
  border-radius: 8px;
  border: 1px dashed var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.img-side {
  flex: 1;
}
.img-side .ann-hint {
  margin: 0 0 6px;
}

@media (max-width: 768px) {
  .ann-grid {
    grid-template-columns: 1fr;
  }
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
