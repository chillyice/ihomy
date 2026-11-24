<!--
  「上次在…」:暖居手机全部动态首条(warm-mobile)/ 手机首页顶部(mobile)/ 侧栏停靠(dock)/ 平板浮卡(float)。
  数据来自 utils/lastDoing(跟账号走)，没有可续做记录时隐藏。
  相对时间每分钟刷新一次(模板里不读 Date.now 做 computed)。
-->
<template>
  <section v-if="rows.length" class="last-doing" :class="[`is-${variant}`, { 'is-compact': compact }]" :aria-label="t('lastDoing.title')">
    <template v-if="compact">
      <el-tooltip :content="rows[0]?.title" placement="right">
        <button type="button" class="ld-compact" :aria-label="t('lastDoing.title')" @click="go(rows[0])">
          <el-icon><component :is="kindIcon(rows[0]?.kind)" /></el-icon>
        </button>
      </el-tooltip>
    </template>
    <template v-else>
      <div v-if="!warmMobile" class="ld-head paper-heading">
        <span class="ld-head-title" :title="variant === 'dock' ? $t('lastDoing.hint') : undefined">{{ $t('lastDoing.title') }}</span>
        <button
          v-if="variant === 'float'"
          type="button"
          class="ld-collapse"
          :title="$t('lastDoing.collapse')"
          :aria-label="$t('lastDoing.collapse')"
          @click.stop="emit('collapse')"
        >
          <el-icon><CaretRight /></el-icon>
        </button>
      </div>
      <button v-for="it in rows" :key="it.kind + ':' + it.id" type="button" class="ld-row" @click="go(it)">
        <span v-if="warmMobile" class="ld-label">{{ $t('lastDoing.title') }}</span>
        <el-icon v-else class="ld-icon"><component :is="kindIcon(it.kind)" /></el-icon>
        <span class="ld-body">
          <strong class="ld-title">{{ it.title }}</strong>
          <small class="ld-meta">{{ metaOf(it) }}</small>
        </span>
        <el-icon v-if="warmMobile || variant === 'mobile'" class="ld-arrow"><ArrowRight /></el-icon>
      </button>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, CaretRight, EditPen, Notebook, Reading, VideoPlay } from '@element-plus/icons-vue'
import { buildDoingPath, useLastDoing, visibleDoing } from '@/utils/lastDoing'
import { formatRelativeTime } from '@/utils/datetime'

const props = defineProps({
  // mobile:手机首页顶部整行;dock:桌面侧栏卡片;float:平板/窄屏浮卡
  variant: { type: String, default: 'mobile' },
  // 侧栏折叠成图标条时退化为一个图标按钮
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['collapse'])

const router = useRouter()
const { t } = useI18n()
const { items } = useLastDoing()

const now = ref(Date.now())
let timer = null
onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 60_000)
})
onBeforeUnmount(() => clearInterval(timer))

const ICONS = { BOOK: Reading, CINEMA: VideoPlay, BLOG: EditPen, DIARY: Notebook }
const MAX_ROWS = { 'warm-mobile': 1, mobile: 1, dock: 2, float: 1 }

const warmMobile = computed(() => props.variant === 'warm-mobile')
const rows = computed(() => visibleDoing(items.value, now.value).slice(0, MAX_ROWS[props.variant] ?? 1))

const kindIcon = (kind) => ICONS[kind] || Reading

const metaOf = (it) => {
  let lead = t(`lastDoing.${it.kind.toLowerCase()}`)
  if (it.kind === 'BOOK' && it.progress != null) lead = t('lastDoing.progress', { p: it.progress })
  else if (it.kind === 'CINEMA' && it.episodeCode) lead = it.episodeCode
  return [lead, formatRelativeTime(t, it.at)].filter(Boolean).join(' · ')
}

const go = (it) => {
  if (it) router.push(buildDoingPath(it))
}
</script>

<style scoped>
.last-doing {
  box-sizing: border-box;
  color: var(--color-text);
}

/* ===== 手机:首页最上方一条 ===== */
.is-mobile {
  margin: 10px 12px 2px;
  padding: 8px 12px 8px 10px;
  border-radius: 14px;
  border: 1px solid var(--color-line);
  background: linear-gradient(120deg, rgba(var(--color-brand-rgb), 0.1), var(--color-card));
  box-shadow: var(--shadow);
}
.is-mobile .ld-head {
  margin-bottom: 2px;
}
.is-mobile .ld-row {
  padding: 4px 0;
}

/* 暖居手机把续做位置装进一张带书签的纸卡，晨暮随主题用色。 */
.is-warm-mobile {
  margin: 0 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px 14px 14px 6px;
  background-color: var(--color-card);
  background-image: var(--caption-paper);
  background-size: auto, var(--home-grain-size);
  box-shadow: 0 2px var(--album-fold);
}
.ld-label {
  flex: none;
  padding: 3px 6px;
  border-inline-start: 2px solid var(--color-brand);
  border-radius: 2px;
  background: rgba(var(--color-brand-rgb), 0.1);
  color: var(--color-text);
  font-size: 12px;
}
.is-warm-mobile .ld-row {
  min-height: 52px;
  padding: 6px 12px;
  gap: 10px;
}
.is-warm-mobile .ld-title {
  font-size: 15px;
}
.is-warm-mobile .ld-meta {
  font-size: 12px;
}
.is-warm-mobile button:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 1px;
}

/* ===== 桌面侧栏：高度随记录条数变化 ===== */
.is-dock {
  display: flex;
  flex-direction: column;
  flex: none;
  margin: 6px 10px;
  padding: 5px 8px;
  border-radius: 14px;
  border: 1px solid var(--color-line);
  background: rgba(var(--color-brand-rgb), 0.06);
}
.is-dock .ld-head {
  padding-bottom: 2px;
}
.is-dock .ld-row {
  padding: 3px 4px;
  border-radius: 10px;
  line-height: 1.25;
}
.is-dock .ld-row:hover {
  background: var(--color-line);
}
.is-dock .ld-title {
  font-size: 13px;
}
.is-dock .ld-meta {
  font-size: 11.5px;
}
.is-dock.is-compact {
  margin: 4px auto;
  padding: 0;
  border: 0;
  background: transparent;
}

/* ===== 平板/窄屏:贴边浮卡 ===== */
.is-float {
  width: 100%;
  padding: 3px 8px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: var(--shadow-hover);
  display: flex;
  flex-direction: column;
}
.is-float .ld-head {
  padding: 0 2px 2px;
}
.is-float .ld-row {
  min-height: 32px;
  padding: 2px;
  line-height: 1.2;
}

.ld-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 0 2px 4px;
}
.ld-head-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
}
.ld-collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.ld-collapse:hover {
  background: var(--color-line);
}

.ld-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.ld-icon {
  flex: none;
  font-size: 17px;
  color: var(--color-brand);
}
.ld-body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}
.ld-title {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ld-meta {
  font-size: 11.5px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ld-arrow {
  flex: none;
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 折叠侧栏里的紧凑图标键 */
.ld-compact {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin: 0;
  padding: 0;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  background: rgba(var(--color-brand-rgb), 0.08);
  color: var(--color-brand);
  cursor: pointer;
}
.ld-compact:hover {
  background: rgba(var(--color-brand-rgb), 0.16);
}
.ld-compact .el-icon {
  font-size: 20px;
}

.is-float .ld-title {
  font-size: 12px;
}
.is-float .ld-meta {
  font-size: 11px;
}
</style>
