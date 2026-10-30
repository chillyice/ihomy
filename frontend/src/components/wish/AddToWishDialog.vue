<!-- 加入愿望单弹窗(V10.92):放映厅详情/纪念日/小游戏三个入口共用,来源三字段由调用方传入 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="t('wish.add')" width="460px" destroy-on-close>
    <el-form :model="form" label-position="top">
      <el-form-item v-if="source.type" :label="t('wish.sourceLabel')">
        <div class="aw-source">
          <el-tag size="small" effect="plain">{{ dictText(t, 'wishSource', source.type) }}</el-tag>
          <span v-if="source.title" class="aw-source-title">{{ source.title }}</span>
        </div>
      </el-form-item>
      <el-form-item :label="t('wish.name')">
        <el-input v-model="form.title" maxlength="100" :placeholder="titlePlaceholder || t('wish.titlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('wish.reason')">
        <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="500" :placeholder="t('wish.reasonPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('wish.category')">
        <el-select
          v-model="cats"
          multiple
          filterable
          allow-create
          default-first-option
          :placeholder="t('wish.categoryPlaceholder')"
          style="width: 100%"
        >
          <el-option v-for="c in WISH_CATEGORIES" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 打开时按来源重置表单:标题可预填也可只给占位;分类默认值由入口给(如放映厅「影视」)
import { computed, h, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { wishApi } from '@/api'
import { dictText } from '@/utils/dict'
import { WISH_CATEGORIES } from '@/utils/wish'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 来源 { type: CINEMA/ANNIVERSARY/GAME/PLAN, id: 来源记录ID, title: 来源标题 } */
  source: { type: Object, default: () => ({}) },
  defaultTitle: { type: String, default: '' },
  titlePlaceholder: { type: String, default: '' },
  defaultCategory: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const { t } = useI18n()
const router = useRouter()

const saving = ref(false)
const form = reactive({ title: '', reason: '' })
const cats = ref([])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.title = props.defaultTitle
    form.reason = ''
    cats.value = props.defaultCategory ? [props.defaultCategory] : []
  },
)

// 成功提示带「去愿望单看看」:命令式提示不在组件作用域内,链接样式走内联
const goWishlist = () => {
  ElMessage.closeAll()
  router.push('/wish')
}

const onSave = async () => {
  if (!form.title.trim()) return ElMessage.warning(t('wish.titleRequired'))
  saving.value = true
  try {
    await wishApi.create({
      title: form.title.trim(),
      reason: form.reason.trim() || null,
      category: cats.value.join(','),
      sourceType: props.source?.type || null,
      sourceId: props.source?.id != null ? String(props.source.id) : null,
      sourceTitle: props.source?.title || null,
    })
    ElMessage({
      type: 'success',
      duration: 3500,
      message: h('span', null, [
        `${t('wish.added')} `,
        h(
          'a',
          {
            style: 'color: var(--color-accent); cursor: pointer; margin-left: 4px;',
            onClick: goWishlist,
          },
          t('wish.goWishlist'),
        ),
      ]),
    })
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.aw-source {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.aw-source-title {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
