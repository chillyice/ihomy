<!-- 银行流水 CSV 导入:选文件 → 列映射 → 预览校验 → 导入结果(成功/失败 + 前 20 条错误) -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="min(820px, 95vw)" :title="$t('book.import.title')" @closed="reset">
    <el-steps :active="step" finish-status="success" simple class="im-steps">
      <el-step :title="$t('book.import.stepFile')" />
      <el-step :title="$t('book.import.stepMap')" />
      <el-step :title="$t('book.import.stepPreview')" />
      <el-step :title="$t('book.import.stepResult')" />
    </el-steps>

    <!-- 第一步:选文件 -->
    <div v-if="step === 0" class="im-file">
      <p class="im-hint">{{ $t('book.import.fileHint') }}</p>
      <input ref="fileInput" type="file" accept=".csv,text/csv" class="im-input" @change="onFileChange" />
      <el-button type="primary" @click="fileInput?.click()">{{ $t('book.import.chooseFile') }}</el-button>
      <p v-if="fileName" class="im-picked">{{ $t('book.import.picked', { name: fileName }) }}</p>
    </div>

    <!-- 第二步:列映射 -->
    <div v-else-if="step === 1" class="im-map">
      <div class="im-headrow">
        <el-checkbox v-model="hasHeader">{{ $t('book.import.hasHeader') }}</el-checkbox>
        <span class="im-hint">{{ $t('book.import.mapHint') }}</span>
      </div>
      <el-form label-position="top" class="im-form">
        <el-form-item v-for="f in FIELDS" :key="f.key" :label="fieldLabel(f)" :required="f.required">
          <el-select v-model="mapping[f.key]" style="width: 100%">
            <el-option :label="$t('book.import.notMapped')" :value="-1" />
            <el-option v-for="(h, i) in headers" :key="i" :label="columnLabel(h, i)" :value="i" />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="im-hint">{{ $t('book.import.typeOptional') }}</p>
    </div>

    <!-- 第三步:预览 + 校验 -->
    <div v-else-if="step === 2" class="im-preview">
      <div class="im-stats">
        <span>{{ $t('book.import.totalRows', { n: preview.total }) }}</span>
        <span class="is-ok">{{ $t('book.import.validRows', { n: preview.valid }) }}</span>
        <span :class="{ 'is-bad': preview.invalid > 0 }">{{ $t('book.import.invalidRows', { n: preview.invalid }) }}</span>
      </div>
      <el-table :data="preview.samples" size="small" max-height="320">
        <el-table-column :label="$t('book.import.line')" width="64">
          <template #default="{ row }">{{ row.line }}</template>
        </el-table-column>
        <el-table-column :label="$t('book.import.check')" min-width="180">
          <template #default="{ row }">
            <span v-if="row.ok" class="is-ok">{{ $t('book.import.previewOk') }}</span>
            <span v-else class="is-bad">{{ $t(`book.import.err.${row.message}`) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('book.import.rawCells')" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.cells.join(' | ') }}</template>
        </el-table-column>
      </el-table>
      <p class="im-hint">{{ $t('book.import.previewNote') }}</p>
    </div>

    <!-- 第四步:结果 -->
    <div v-else class="im-result">
      <div class="im-result-nums">
        <span class="is-ok">{{ $t('book.import.successN', { n: result?.success ?? 0 }) }}</span>
        <span :class="{ 'is-bad': Number(result?.failed) > 0 }">{{ $t('book.import.failedN', { n: result?.failed ?? 0 }) }}</span>
      </div>
      <el-table v-if="result?.errors?.length" :data="result.errors" size="small" max-height="260">
        <el-table-column :label="$t('book.import.line')" width="70">
          <template #default="{ row }">{{ row.line }}</template>
        </el-table-column>
        <el-table-column :label="$t('book.import.reason')" min-width="260">
          <template #default="{ row }">{{ row.message }}</template>
        </el-table-column>
      </el-table>
      <p class="im-hint">{{ $t('book.import.resultNote') }}</p>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.close') }}</el-button>
      <el-button v-if="step === 1" @click="step = 0">{{ $t('book.import.prev') }}</el-button>
      <el-button v-if="step === 1" type="primary" :disabled="!columnsReady" @click="step = 2">{{ $t('book.import.next') }}</el-button>
      <el-button v-if="step === 2" @click="step = 1">{{ $t('book.import.prev') }}</el-button>
      <el-button v-if="step === 2" type="primary" :loading="importing" :disabled="!preview.valid" @click="onImport">
        {{ $t('book.import.start') }}
      </el-button>
      <el-button v-if="step === 3" type="primary" @click="onRestart">{{ $t('book.import.again') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// CSV 导入向导:浏览器侧只做预览与即时校验,真正落库由后端再解析一次(结果以服务端为准)
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

import { bookApi } from '@/api'
import { CSV_MAX_BYTES, CSV_MAX_ROWS, EMPTY_MAPPING, decodeCsvBuffer, guessMapping, parseCsv, previewRows } from '@/utils/csvLite'

const props = defineProps({
  visible: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'saved'])

const { t } = useI18n()

const FIELDS = [
  { key: 'dateCol', label: 'book.import.colDate', required: true },
  { key: 'typeCol', label: 'book.import.colType' },
  { key: 'amountCol', label: 'book.import.colAmount', required: true },
  { key: 'categoryCol', label: 'book.import.colCategory' },
  { key: 'accountCol', label: 'book.import.colAccount' },
  { key: 'remarkCol', label: 'book.import.colRemark' },
]

const step = ref(0)
const fileInput = ref(null)
const file = ref(null)
const fileName = ref('')
const parsed = ref({ headers: [], rows: [] })
const mapping = reactive({ ...EMPTY_MAPPING })
const result = ref(null)
const importing = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const headers = computed(() => parsed.value.headers || [])
const hasHeader = computed({
  get: () => (mapping.headerRows || 0) > 0,
  set: (v) => {
    mapping.headerRows = v ? 1 : 0
  },
})
const columnsReady = computed(() => mapping.dateCol >= 0 && mapping.amountCol >= 0)
const preview = computed(() => (columnsReady.value ? previewRows(parsed.value, mapping) : { total: 0, valid: 0, invalid: 0, samples: [] }))

const fieldLabel = (f) => t(f.label)
const columnLabel = (h, i) =>
  hasHeader.value && String(h || '').trim() ? `${t('book.import.columnN', { n: i + 1 })}：${h}` : t('book.import.columnN', { n: i + 1 })

const onFileChange = async (e) => {
  const picked = e.target.files?.[0]
  e.target.value = ''
  if (!picked) return
  if (picked.size > CSV_MAX_BYTES) {
    ElMessage.warning(t('book.import.tooBig'))
    return
  }
  const text = decodeCsvBuffer(await picked.arrayBuffer())
  const data = parseCsv(text)
  const dataRows = Math.max(0, data.rows.length - (mapping.headerRows ? 1 : 0))
  if (dataRows > CSV_MAX_ROWS) {
    ElMessage.warning(t('book.import.tooMany'))
    return
  }
  if (!data.rows.length) {
    ElMessage.warning(t('book.import.emptyFile'))
    return
  }
  file.value = picked
  fileName.value = picked.name
  parsed.value = data
  Object.assign(mapping, guessMapping(data.headers), { headerRows: 1 })
  step.value = 1
}

const onImport = async () => {
  importing.value = true
  try {
    result.value = await bookApi.importCsv(file.value, {
      dateCol: mapping.dateCol,
      typeCol: mapping.typeCol,
      amountCol: mapping.amountCol,
      categoryCol: mapping.categoryCol,
      accountCol: mapping.accountCol,
      remarkCol: mapping.remarkCol,
      headerRows: mapping.headerRows,
    })
    step.value = 3
    emit('saved')
  } finally {
    importing.value = false
  }
}

const onRestart = () => {
  reset()
}

const reset = () => {
  step.value = 0
  file.value = null
  fileName.value = ''
  parsed.value = { headers: [], rows: [] }
  Object.assign(mapping, EMPTY_MAPPING)
  result.value = null
}
</script>

<style scoped>
.im-steps {
  margin-bottom: 16px;
}
.im-file {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0 4px;
}
.im-input {
  display: none;
}
.im-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #999);
}
.im-picked {
  margin: 0;
  font-size: 13px;
  color: var(--color-text);
}
.im-headrow {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.im-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}
.im-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 13px;
  margin-bottom: 10px;
}
.im-result-nums {
  display: flex;
  gap: 20px;
  font-size: 14px;
  margin-bottom: 12px;
}
.is-ok {
  color: var(--color-income);
  font-weight: 600;
}
.is-bad {
  color: var(--color-expense);
  font-weight: 600;
}
@media (max-width: 560px) {
  .im-form {
    grid-template-columns: 1fr;
  }
}
</style>
