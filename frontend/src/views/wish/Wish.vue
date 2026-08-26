<!-- 愿望单页:家庭共享愿望,可标记实现/放弃,附分类标签与提出人 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('wish.title') }]" />

    <div class="list-header">
      <el-button type="primary" @click="openEditor()">{{ $t('wish.add') }}</el-button>
    </div>

    <el-tabs v-model="filter">
      <el-tab-pane :label="$t('more.all')" name="all" />
      <el-tab-pane :label="$t('wish.pending')" name="open" />
      <el-tab-pane :label="$t('wish.achieved')" name="done" />
    </el-tabs>

    <div v-loading="loading">
      <div v-if="filtered.length" class="wish-grid">
        <div v-for="w in filtered" :key="w.id" class="wish-card card" :class="'st' + (w.status === 'ACHIEVED' ? 1 : w.status === 'ABANDONED' ? 2 : 0)">
          <div class="wish-top">
            <span class="wish-title">{{ w.title }}</span>
            <el-tag :type="statusTag(w.status)" size="small">{{ statusText(w.status) }}</el-tag>
          </div>
          <div v-if="w.reason" class="wish-reason">{{ w.reason }}</div>
          <div v-if="w.category" class="wish-cats">
            <el-tag v-for="c in w.category.split(',')" :key="c" size="small" effect="plain">{{ c }}</el-tag>
          </div>
          <div class="wish-meta">
            <span>{{ $t('wish.requested', { name: w.requesterName }) }}</span>
            <span v-if="w.status === 'ACHIEVED' && w.achievedAt">{{ $t('wish.achievedAt', { date: w.achievedAt.slice(0, 10) }) }}</span>
            <span v-else>{{ w.createdAt.slice(0, 10) }}</span>
          </div>
          <div class="wish-actions">
            <el-button v-if="w.status === 'PENDING'" size="small" type="success" plain @click="onSetStatus(w, 1)">{{ $t('wish.achieved') }}</el-button>
            <el-button v-if="w.status === 'PENDING'" size="small" type="info" plain @click="onSetStatus(w, 2)">{{ $t('wish.abandon') }}</el-button>
            <el-button v-if="w.status !== 'PENDING'" size="small" @click="onSetStatus(w, 0)">{{ $t('wish.restore') }}</el-button>
            <el-button size="small" text @click="openEditor(w)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" text type="danger" @click="onDelete(w)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('wish.noData')" />
    </div>

    <el-dialog v-model="editor.visible" append-to-body :title="editor.form.id ? $t('wish.edit') : $t('wish.add')" width="440px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('wish.name')">
          <el-input v-model="editor.form.title" :placeholder="$t('wish.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('wish.reason')">
          <el-input v-model="editor.form.reason" type="textarea" :rows="2" :placeholder="$t('wish.reasonPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('wish.category')">
          <el-select v-model="editor.cats" multiple filterable allow-create default-first-option :placeholder="$t('wish.categoryPlaceholder')" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 愿望单:愿望卡片网格 + 状态 tab;标记实现/放弃走 PUT status
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { wishApi } from '@/api'
import { dictText } from '@/utils/dict'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()

const CATEGORIES = ['生日礼物', '家庭用品', '旅行', '数码设备', '美食', '其他']

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const filter = ref('all')
const editor = reactive({ visible: false, form: {}, cats: [] })

const statusText = (s) => dictText(t, 'wishStatus', s)
const statusTag = (s) => ({ PENDING: '', ACHIEVED: 'success', ABANDONED: 'info' })[s] || 'info'

const filtered = computed(() => {
  if (filter.value === 'all') return list.value
  return list.value.filter((w) => (filter.value === 'done' ? w.status === 'ACHIEVED' : w.status === 'PENDING'))
})

const load = async () => {
  loading.value = true
  try {
    list.value = await wishApi.list()
  } finally {
    loading.value = false
  }
}

const openEditor = (w) => {
  editor.visible = true
  editor.form = w
    ? { id: w.id, title: w.title, reason: w.reason || '', category: w.category || '' }
    : { id: null, title: '', reason: '', category: '' }
  editor.cats = editor.form.category ? editor.form.category.split(',') : []
}

const onSave = async () => {
  if (!editor.form.title?.trim()) return ElMessage.warning(t('wish.titleRequired'))
  saving.value = true
  try {
    const { id, ...data } = editor.form
    data.category = editor.cats.join(',')
    if (id) await wishApi.update(id, data)
    else await wishApi.create(data)
    ElMessage.success(t('common.success'))
    editor.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

const onSetStatus = async (w, status) => {
  await wishApi.update(w.id, { status })
  ElMessage.success(status === 1 ? t('wish.achievedMsg') : status === 2 ? t('wish.abandonedMsg') : t('wish.restoredMsg'))
  await load()
}

const onDelete = async (w) => {
  await ElMessageBox.confirm(t('wish.deleteConfirm', { title: w.title }), t('common.deleteConfirm'), { type: 'warning', closeOnClickModal: true })
  await wishApi.remove(w.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 愿望卡片网格:状态/理由/分类/操作 */
.wish-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.wish-card {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wish-card.st1 {
  opacity: 0.65;
}
.wish-card.st1 .wish-title {
  text-decoration: line-through;
}
.wish-card.st2 {
  opacity: 0.45;
}
.wish-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.wish-title {
  font-weight: 600;
}
.wish-reason {
  color: #666;
  font-size: 13px;
}
.wish-cats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.wish-meta {
  color: #999;
  font-size: 12px;
  display: flex;
  justify-content: space-between;
}
.wish-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .wish-actions .el-button { flex: 1; min-width: 60px; }
  .wish-meta { flex-direction: column; gap: 4px; }
}
</style>