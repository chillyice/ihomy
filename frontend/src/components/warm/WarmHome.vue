<!-- 暖居首页:12 列网格,组件化 + 可编辑(OWNER 增删/拖拽排序),布局持久化 localStorage(与光尘仪表盘同款思路) -->
<template>
  <div class="gc-home" :class="{ 'gc-editing': editMode }">
    <!-- 分享落地:被分享家庭的封面门脸(自己家不挂) -->
    <FamilyCoverBanner v-if="showDoorSign" :family="appStore.family" class="gc-door-sign" />
    <div class="gc-main-head">
      <div>
        <h2 class="gc-h2">{{ greeting }}</h2>
        <div class="gc-subline">
          {{ familyName }} · {{ $t('warm.members', { n: memberCount }) }}
          <router-link v-if="sunLight?.weather?.value?.temp != null" class="gc-weather-link" to="/weather">
            {{ weatherTemp }}° · {{ weatherText }}
          </router-link>
        </div>
      </div>
      <div class="gc-head-btns">
        <template v-if="!editMode">
          <button v-if="canEdit" class="gc-btn ghost sm" @click="startEdit">{{ $t('warm.editHome') }}</button>
          <button class="gc-btn primary sm" @click="$router.push('/blog')">{{ $t('warm.postSomething') }}</button>
          <button class="gc-btn ghost sm" @click="$router.push('/member')">{{ $t('warm.family') }}</button>
        </template>
        <template v-else>
          <button class="gc-btn ghost sm" @click="resetLayout">{{ $t('warm.resetLayout') }}</button>
          <button class="gc-btn primary sm" @click="finishEdit">{{ $t('warm.done') }}</button>
        </template>
      </div>
    </div>

    <!-- 编辑提示条 -->
    <Transition name="gc-fade">
      <div v-if="editMode" class="gc-edit-hint">{{ $t('warm.editHint') }}</div>
    </Transition>

    <div class="gc-grid" ref="gridEl">
      <div
        v-a11y-click
        v-for="w in displayWidgets"
        :key="w.id"
        class="gc-card"
        :data-wid="w.id"
        :data-paper="cardPaper(w.id)"
        :inert="w.kind === 'preview'"
        :class="[
          `gc-c${w.span}`,
          `gc-r${w.row}`,
          {
            'gc-weather': baseOf(w) === 'weather',
            'gc-card-tile': compact(w),
            'gc-card-styled': Boolean(w.component),
            'gc-item-card': baseOf(w) === 'item',
            'gc-photos': baseOf(w) === 'photos',
            'gc-featured': w.featured,
            'gc-card-edit': editMode,
            'gc-card-link': isLinkCard(w),
            'gc-card-preview': w.kind === 'preview',
            'gc-card-dragging': reorderPreview && reorderPreview.id === w.id,
            'gc-daily-card': baseOf(w) === 'daily',
          },
        ]"
        :style="[cardStyle(w), reorderStyle(w)]"
        @pointerdown="onReorderStart($event, w)"
        @dragstart="editMode && $event.preventDefault()"
        @click="onCardClick(w)"
      >
        <!-- 编辑手柄 -->
        <template v-if="editMode && w.kind !== 'preview'">
          <button class="gc-del" :title="$t('warm.removeWidget')" @click.stop="removeWidget(w)">✕</button>
          <div class="gc-grip" :title="$t('warm.dragSort')">⋮⋮</div>
          <div class="gc-resize" :title="$t('warm.resizeWidget')" @pointerdown.stop.prevent="onResizeStart($event, w)" @click.stop></div>
          <!-- 样式切换:同一功能注册了多种样式才出现(原位换样式,尺寸保留) -->
          <template v-if="styleOptions(w).length">
            <button
              class="gc-style-btn"
              :class="{ on: styleMenuId === w.id }"
              :title="$t('warm.switchStyle')"
              :aria-expanded="styleMenuId === w.id"
              @click.stop="toggleStyleMenu(w.id)"
            >
              {{ $t('warm.styleLabel') }}
            </button>
            <div v-if="styleMenuId === w.id" class="gc-style-menu" @click.stop>
              <div class="gc-style-tip">{{ $t('warm.switchStyle') }}</div>
              <button v-for="s in styleOptions(w)" :key="s.id" class="gc-style-item" @click.stop="switchStyle(w, s.id)">
                <span class="gc-style-ic">{{ s.icon }}</span
                >{{ $t(s.styleKey) }}
              </button>
            </div>
          </template>
        </template>

        <!-- 紧凑图标档:1 列纯图标(一行一列),2 列图标 + 最简名称(一行两列) -->
        <div v-if="w.id === '__slot__'" class="gc-preview-body" aria-hidden="true">
          <span v-if="!compact(w)" class="gc-preview-hint">{{ $t('warm.dropHere') }}</span>
        </div>
        <div v-else-if="compact(w)" class="gc-tile">
          <span class="gc-tile-icon">{{ w.icon }}</span>
          <span v-if="w.span >= 2" class="gc-tile-label">{{ w.label }}</span>
        </div>
        <template v-else-if="cardData[cardKey(w)]?.status.value === 'loading'">
          <h3 class="gc-card-h3">{{ w.label }}</h3>
          <div class="gc-empty" role="status">{{ $t('common.loading') }}</div>
        </template>
        <template v-else-if="cardData[cardKey(w)]?.status.value === 'error'">
          <h3 class="gc-card-h3">{{ w.label }}</h3>
          <div class="gc-empty" role="status">
            {{ $t('common.loadFailed') }}
            <button type="button" class="gc-more" @click.stop="cardData[cardKey(w)].retry()">{{ $t('common.retry') }}</button>
          </div>
        </template>

        <!-- 统一渲染出口:注册表里带 component 的样式(新样式只加注册表,这里不用加分支) -->
        <component :is="w.component" v-else-if="w.component" v-bind="w.props(widgetCtx())" />

        <WeatherWindow
          v-else-if="w.id === 'weather'"
          :weather="sunLight?.weather?.value"
          :detail="sunLight?.weatherDetail?.value"
          :night="sunLight?.sunScene?.value?.isNight"
          :background="weatherBg"
          :span="w.span"
          :row="w.row"
        />

        <!-- 家人动态 -->
        <template v-else-if="w.id === 'feed'">
          <h3 class="gc-card-h3">{{ $t('warm.widget.feed') }}</h3>
          <div v-if="feeds.length" class="gc-list">
            <div
              v-for="(f, i) in feeds.slice(0, w.featured ? 3 : nFeed(w))"
              :key="f.type + '-' + (f.id ?? i)"
              class="gc-row"
              v-a11y-click
              @click="!editMode && goFeed(f)"
            >
              <span class="gc-ic" :style="feedChipStyle(f.type)">{{ feedIcon(f.type) }}</span>
              <template v-if="w.featured">
                <span class="gc-feed-entry">
                  <span class="gc-feed-title">{{ feedSummary(f) }}</span>
                  <small>{{ f.authorName || $t('feed.authorFallback') }}</small>
                </span>
              </template>
              <template v-else>
                <span class="gc-val">{{ f.authorName || $t('feed.authorFallback') }}</span>
                <span class="gc-muted">{{ feedSummary(f) }}</span>
              </template>
            </div>
          </div>
          <div v-else class="gc-empty">{{ $t('warm.noFeed') }}</div>
        </template>

        <PhotoCarousel v-else-if="w.id === 'photos'" :photos="photos" :edit-mode="editMode" :variant="w.featured ? 'featured' : 'stack'" />

        <!-- 纪念日 -->
        <template v-else-if="w.id === 'anni'">
          <h3 class="gc-card-h3">{{ $t('warm.widget.anni') }}</h3>
          <template v-if="anniversaries.length">
            <div class="gc-val-lg">{{ anniversaries[0].label }}</div>
            <div class="gc-muted">
              {{ anniversaries[0].date }} · <span class="gc-days">{{ $t('warm.daysLeft', { n: anniversaries[0].days }) }}</span>
            </div>
            <div class="gc-meter"><i :style="{ width: Math.min(100, anniversaries[0].days) + '%' }"></i></div>
            <div v-if="nAnni(w) > 1" class="gc-list gc-anni-more">
              <div v-for="(a, i) in anniversaries.slice(1, nAnni(w))" :key="i" class="gc-row">
                <span class="gc-ic">🎂</span><span class="gc-val">{{ a.label }}</span
                ><span class="gc-muted">{{ $t('warm.daysUnit', { n: a.days }) }}</span>
              </div>
            </div>
          </template>
          <div v-else class="gc-empty">{{ $t('warm.noAnni') }}</div>
        </template>

        <!-- 本月收支 -->
        <template v-else-if="w.id === 'finance'">
          <h3 class="gc-card-h3">
            {{ $t('warm.widget.finance') }}<span class="gc-muted">{{ bookSummary.month || '' }}</span>
          </h3>
          <div class="gc-flex-baseline">
            <div class="gc-val-lg">¥ {{ fmt(balance) }}</div>
            <div class="gc-muted">{{ $t('warm.entries', { n: bookSummary.count ?? 0 }) }}</div>
          </div>
          <div class="gc-fin-row">
            <span class="gc-fin in">+{{ fmt(bookSummary.income) }}</span>
            <span class="gc-fin out">-{{ fmt(bookSummary.expense) }}</span>
          </div>
          <div v-if="financeTotal > 0" class="gc-fin-bar">
            <i class="in" :style="{ width: incomePct + '%' }"></i>
            <i class="out" :style="{ width: 100 - incomePct + '%' }"></i>
          </div>
          <div v-else class="gc-empty" style="padding: 8px 0">{{ $t('warm.noBook') }}</div>
          <!-- 理财持仓:本金不计收支,单列一条,点了直达理财页签 -->
          <button v-if="financeHolding.count" type="button" class="gc-fin-holding" @click.stop="$router.push('/book?tab=finance')">
            <span class="gc-fin-holding-label">{{ $t('warm.financeHolding', { n: financeHolding.count }) }}</span>
            <b>¥ {{ fmt(financeHolding.principal) }}</b>
          </button>
        </template>

        <!-- 寻物(搜索 + 语音 + 户型图预览 / 列表) -->
        <template v-else-if="w.id === 'item'">
          <h3 class="gc-card-h3">
            {{ $t('warm.widget.item') }}<span class="gc-muted">{{ itemHouse?.name || '' }}</span
            ><button class="gc-more" @click.stop="!editMode && $router.push('/item')">{{ $t('warm.itemManage') }}</button>
          </h3>
          <div class="gc-item-search">
            <input
              v-model="itemKeyword"
              class="gc-item-input"
              :placeholder="itemSearching ? $t('warm.searchingAI') : $t('warm.searchPlaceholder')"
              @keyup.enter="onItemSearch"
              @input="!itemKeyword && clearItemSearch()"
            />
            <button
              class="gc-btn ghost sm gc-item-voice"
              :class="{ recording: itemVoiceRecording }"
              :disabled="itemVoiceProcessing"
              :title="$t('warm.voiceFindItem')"
              @click="toggleItemVoice"
            >
              🎤
            </button>
          </div>

          <!-- 高行(≥3 行):户型图预览 + 命中高亮 -->
          <template v-if="itemShowFloorPlan(w)">
            <div class="gc-item-plan" v-a11y-click @click="!editMode && $router.push('/item')">
              <svg v-if="itemFloorPlanView" :viewBox="itemFloorPlanView.viewBox" preserveAspectRatio="xMidYMid meet" class="gc-item-svg">
                <image
                  v-if="itemFloorPlanView.imageUrl"
                  :href="itemFloorPlanView.imageUrl"
                  :transform="itemFloorPlanView.imgTransform"
                  class="gc-item-bg"
                />
                <g v-for="r in itemFloorPlanView.rooms" :key="r.id">
                  <polygon :points="r.points" class="gc-item-room" :class="{ hit: itemHitRoomIds.includes(r.id) }" />
                  <text :x="r.cx" :y="r.cy" class="gc-item-room-label">{{ r.name }}</text>
                </g>
                <g v-for="f in itemFloorPlanView.furnitures" :key="f.id">
                  <rect :x="f.x" :y="f.y" :width="f.w" :height="f.h" rx="2" class="gc-item-furn" />
                  <text v-if="f.w > 26 && f.h > 16" :x="f.x + f.w / 2" :y="f.y + f.h / 2" class="gc-item-furn-label">{{ f.name }}</text>
                </g>
                <g v-for="it in itemFloorPlanView.items" :key="it.id">
                  <circle :cx="it.ax" :cy="it.ay" r="5" class="gc-item-dot" :class="{ hit: itemHitIds.includes(it.id) }" />
                  <text :x="it.ax" :y="it.ay - 9" class="gc-item-dot-label">{{ it.name }}</text>
                </g>
              </svg>
              <div v-else class="gc-empty gc-empty-link">{{ $t('warm.goRegisterFloor') }}</div>
              <div v-if="itemResults.length" class="gc-item-results" @click.stop>
                <div
                  v-for="it in itemResults.slice(0, 5)"
                  :key="it.id"
                  class="gc-row"
                  v-a11y-click
                  @click="!editMode && $router.push('/item')"
                >
                  <span class="gc-ic">📦</span><span class="gc-val">{{ it.name }}</span
                  ><span class="gc-muted">{{ itemPathOf(it) }}</span>
                </div>
              </div>
              <div v-else-if="itemSearched && !itemSearching" class="gc-item-nohit">{{ $t('warm.noItemHit') }}</div>
            </div>
          </template>

          <!-- 矮行(<3 行):列表式搜索结果 -->
          <template v-else>
            <div v-if="itemResults.length" class="gc-list">
              <div
                v-for="it in itemResults.slice(0, nItem(w))"
                :key="it.id"
                class="gc-row"
                v-a11y-click
                @click="!editMode && $router.push('/item')"
              >
                <span class="gc-ic">📦</span><span class="gc-val">{{ it.name }}</span
                ><span class="gc-muted">{{ itemPathOf(it) }}</span>
              </div>
            </div>
            <div v-else-if="itemSearched && !itemSearching" class="gc-empty">{{ $t('warm.noItems') }}</div>
            <div v-else-if="items.length" class="gc-list">
              <div
                v-for="it in items.slice(0, nItem(w))"
                :key="it.id"
                class="gc-row"
                v-a11y-click
                @click="!editMode && $router.push('/item')"
              >
                <span class="gc-ic">📦</span><span class="gc-val">{{ it.name }}</span
                ><span class="gc-tag">{{ it.room_name || it.house_name || '—' }}</span>
              </div>
            </div>
            <div v-else class="gc-empty gc-empty-link" v-a11y-click @click="!editMode && $router.push('/item')">
              {{ $t('warm.noItemsRegistered') }}
            </div>
          </template>
        </template>

        <!-- 悬赏任务 -->
        <template v-else-if="w.id === 'task'">
          <h3 class="gc-card-h3">{{ $t('warm.widget.task') }}</h3>
          <div v-if="tasks.length" class="gc-list">
            <div v-for="t in tasks.slice(0, nTask(w))" :key="t.id" class="gc-row" v-a11y-click @click="!editMode && $router.push('/task')">
              <span class="gc-ic">{{ taskIcon(t.rewardType) }}</span>
              <span class="gc-val">{{ t.title }}</span>
              <span class="gc-tag" :class="TASK_STATUS_TAG[t.status]">{{ taskStatusLabel(t.status) }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">{{ $t('warm.noTask') }}</div>
        </template>

        <!-- 愿望单 -->
        <template v-else-if="w.id === 'wish'">
          <h3 class="gc-card-h3">{{ $t('warm.widget.wish') }}</h3>
          <div v-if="wishes.length" class="gc-list">
            <div
              v-for="wi in wishes.slice(0, nWish(w))"
              :key="wi.id"
              class="gc-row"
              v-a11y-click
              @click="!editMode && $router.push('/wish')"
            >
              <span class="gc-ic">⭐</span>
              <span class="gc-val" :class="{ done: wi.status === 'ACHIEVED' }">{{ wi.title }}</span>
              <span v-if="wi.status === 'ACHIEVED'" class="gc-tag grn">{{ $t('warm.achieved') }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">{{ $t('warm.noWish') }}</div>
        </template>

        <!-- 今日:今天的计划 + 今天的提醒(与家庭计划页同源) -->
        <template v-else-if="w.id === 'reminder'">
          <h3 class="gc-card-h3">{{ $t('warm.widget.reminder') }}</h3>
          <div v-if="reminders.length" class="gc-list">
            <div
              v-for="r in reminders.slice(0, w.featured ? 3 : nReminder(w))"
              :key="r.key"
              class="gc-row"
              v-a11y-click
              @click="!editMode && $router.push('/plan?tab=today')"
            >
              <span class="gc-ic">{{ r.kind === 'reminder' ? '🔔' : '📋' }}</span>
              <span class="gc-val">{{ r.title }}</span>
              <span class="gc-muted">{{ r.time || (r.overdueDays ? $t('plan.today.overdue', { n: r.overdueDays }) : '') }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">{{ $t('warm.noReminder') }}</div>
        </template>

        <!-- 每日一图 / 每日知识(偏好走设置页 ihomy-daily;换一条 = 重新拉取) -->
        <template v-else-if="w.id === 'daily'">
          <h3 class="gc-card-h3">
            {{ dailyImageOn ? $t('daily.image') : $t('daily.knowledge')
            }}<button v-if="dailyKnowledgeOn" class="gc-more" :title="$t('daily.next')" @click.stop="!editMode && reloadDaily()">↻</button>
          </h3>
          <div class="gc-daily">
            <div v-if="dailyImage" :key="dailyImage.url" class="gc-daily-pic">
              <img :src="dailyImage.url" :alt="dailyImage.copyright || ''" loading="lazy" />
              <span v-if="dailyImage.copyright" class="gc-daily-cap">{{ dailyImage.copyright }}</span>
            </div>
            <div v-if="dailyKnowledge" :key="dailyKnowledge" class="gc-daily-know">{{ dailyKnowledge }}</div>
            <div v-if="!dailyImage && !dailyKnowledge" class="gc-empty">{{ dailyEmptyHint }}</div>
          </div>
        </template>

        <!-- 快捷入口(无专属内容的模块拖入后生成) -->
        <template v-else-if="w.kind === 'link'">
          <h3 class="gc-card-h3">{{ w.label }}</h3>
          <div class="gc-link-body" v-a11y-click @click="!editMode && $router.push(w.path)">
            <span class="gc-link-icon">{{ w.icon }}</span>
            <span class="gc-link-text">{{ $t('warm.linkOpen', { label: w.label }) }}</span>
            <span class="gc-link-arrow">→</span>
          </div>
        </template>

        <!-- 侧栏拖入占位，尺寸取自该组件的默认布局 -->
        <template v-else-if="w.kind === 'preview'">
          <div class="gc-preview-body">
            <span class="gc-preview-icon">{{ w.icon }}</span>
            <span class="gc-preview-label">{{ w.label }}</span>
            <span class="gc-preview-hint">{{ w.hint || $t('warm.dropHere') }}</span>
          </div>
        </template>
      </div>
    </div>

    <!-- 添加组件托盘(编辑态) -->
    <Transition name="gc-fade">
      <div v-if="editMode && availableWidgets.length" class="gc-add-tray">
        <div class="gc-add-label">{{ $t('warm.addWidget') }}</div>
        <button v-for="w in availableWidgets" :key="w.id" class="gc-add-item" @click="addWidget(w.id)">
          <span class="gc-add-icon">{{ w.icon }}</span
          >{{ w.label }}<span v-if="w.suffix" class="gc-add-style">· {{ w.suffix }}</span>
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onBeforeUnmount, ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { gsap } from 'gsap'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import FamilyCoverBanner from '@/components/FamilyCoverBanner.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useWarmWidgetDrag } from '@/utils/widgetDragData'
import { useWarmCardReorder } from '@/composables/useWarmCardReorder'
import { pickInsertIndex } from '@/utils/dragInsert'
import { addedCodes } from '@/utils/warmHomeShared'
import { cardPaper } from '@/utils/warmMaterials'
import { bookApi, financeApi, itemApi, taskApi, wishApi, planApi, aiApi } from '@/api'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import { useDaily } from '@/composables/useDaily'
import { buildAgenda } from '@/utils/agenda'
import { feedPath, feedSummary as feedSummaryOf } from '@/utils/feed'
import PhotoCarousel from './widgets/PhotoCarousel.vue'
import { useHomeFeed } from '@/composables/useHomeFeed'
import { useHomeCardData } from '@/composables/useHomeCardData'
import { pickDefaultFloor } from '@/utils/floorPlanGeom'
import { summarizePortfolio } from '@/utils/finance'
import { dictText } from '@/utils/dict'
import { parseWarmLayout } from '@/utils/warmHomeLayout'
import { showCoverBanner } from '@/utils/shareLanding'
import { readPref, syncPref, writePref } from '@/utils/userPref'
import { recentMonthRows } from '@/utils/bookTrend'
import { MODULE_DEFAULT_WIDGET, WIDGETS as WIDGET_DEFS, WIDGET_MODULE, siblingsOf, widgetById } from './widgets/registry'
import { isFeatureVisibleNow } from '@/composables/useFeatureGates'
import WeatherWindow from './widgets/WeatherWindow.vue'

const router = useRouter()
const { t } = useI18n()
const appStore = useAppStore()
const userStore = useUserStore()
const { scope, fetchFeed } = useHomeFeed()
const sunLight = inject(SUN_LIGHT_KEY)

// 每日一图 / 每日知识(公开接口,游客也可看;偏好读设置页 localStorage)
const {
  image: dailyImage,
  knowledge: dailyKnowledge,
  imageOn: dailyImageOn,
  knowledgeOn: dailyKnowledgeOn,
  load: loadDaily,
  reload: reloadDaily,
} = useDaily()
const dailyEmptyHint = computed(() => (dailyImageOn || dailyKnowledgeOn ? t('daily.loading') : t('daily.off')))

defineProps({ weatherBg: { type: String, default: '' } })

const familyName = computed(() => appStore.familyName)
// 分享落地门脸:别人家有封面才挂横幅,自己家不挂(判定见 utils/shareLanding)
const showDoorSign = computed(() => showCoverBanner(appStore.family, userStore.userInfo?.familyId))
const memberCount = computed(() => appStore.stats.memberCount || 0)
const anniversaries = computed(() => appStore.stats.upcomingEvents || [])

// ========== 组件注册表 + 布局(localStorage 持久化) ==========
// 组件与样式定义全在 widgets/registry.js:一个功能(module)可挂多种样式,id 是布局身份
// (默认样式沿用原 id,新样式 '<默认 id>:<style>');带 component 的条目走模板里的统一 <component :is> 出口,
// 所以「加一种样式」= 写 SFC + 注册表一行 + i18n,本文件模板不用再加 v-if 分支
// chip = 该卡的色相锚点(--blob-N:1 陶土 / 2 暖沙 / 3 鼠尾草 / 4 暖米 / 5 暖木),标题竖条与列表图标芯片都取此色
const widgetLabel = (base) => t(base.labelKey)
// 卡片色相锚点 → 内联 --chip,供 .gc-card-h3::before 与 .gc-ic 取色(后代继承)
const cardStyle = (w) => ({ '--chip': `var(--blob-${w.chip || 1})` })

// 全部模块的入口元信息(标题/路径/图标),快捷入口卡片据此渲染。
// 必须覆盖侧栏全部可拖入 code(NAV_PATHS 全集),否则 resolveWidget 返回 null → 拖入后无法落位
const MODULE_META = {
  blog: { titleKey: 'warm.module.blog', path: '/blog', icon: '📝' },
  diary: { titleKey: 'warm.module.diary', path: '/diary', icon: '📖' },
  album: { titleKey: 'warm.module.album', path: '/album', icon: '📷' },
  cinema: { titleKey: 'warm.module.cinema', path: '/cinema', icon: '🎬' },
  music: { titleKey: 'warm.module.music', path: '/music', icon: '🎵' },
  library: { titleKey: 'warm.module.library', path: '/library', icon: '📚' },
  item: { titleKey: 'warm.module.item', path: '/item', icon: '📦' },
  kitchen: { titleKey: 'warm.module.kitchen', path: '/kitchen', icon: '🍳' },
  anniversary: { titleKey: 'warm.module.anniversary', path: '/plan?tab=anniversary', icon: '🎂' },
  points: { titleKey: 'warm.module.points', path: '/points', icon: '🏆' },
  achievement: { titleKey: 'warm.module.achievement', path: '/achievements', icon: '🥇' },
  task: { titleKey: 'warm.module.task', path: '/task', icon: '🎯' },
  reminder: { titleKey: 'warm.module.reminder', path: '/plan?tab=today', icon: '🔔' },
  plan: { titleKey: 'warm.module.plan', path: '/plan', icon: '📋' },
  wish: { titleKey: 'warm.module.wish', path: '/wish', icon: '⭐' },
  book: { titleKey: 'warm.module.book', path: '/book', icon: '💰' },
  tree: { titleKey: 'warm.module.tree', path: '/tree', icon: '🌳' },
  tools: { titleKey: 'warm.module.tools', path: '/tools', icon: '🧰' },
  member: { titleKey: 'warm.module.member', path: '/member', icon: '👨‍👩‍👧' },
  storage: { titleKey: 'warm.module.storage', path: '/storage/files', icon: '🗂' },
  plant: { titleKey: 'warm.module.plant', path: '/plant', icon: '🌱' },
  chat: { titleKey: 'warm.module.chat', path: '/chat', icon: '💬' },
  games: { titleKey: 'warm.module.games', path: '/games', icon: '🎮' },
  vault: { titleKey: 'warm.module.vault', path: '/vault', icon: '🔐' },
  iot: { titleKey: 'warm.module.iot', path: '/iot', icon: '🏠' },
  help: { titleKey: 'warm.module.help', path: '/help', icon: '❓' },
  announcement: { titleKey: 'warm.module.announcement', path: '/announcement', icon: '📢' },
  recycle: { titleKey: 'warm.module.recycle', path: '/recycle', icon: '🗑' },
}

// 布局键:组件用其 id(如 feed / photos:carousel),快捷入口用 'link:<code>'(如 link:diary);每项携带 span(列宽)+ row(行高),可调整
const defaultSpan = (id) => widgetById(id)?.span || 4
// 行高基准:各组件定义在注册表里(天气 8×4、家人动态 4×4、每日内容 3 行、其余 2 行)
const defaultRow = (id) => widgetById(id)?.row || 2
// base = 所属功能的默认组件 id:同一功能的多种样式共用一份数据/一条「已在首页」标记
const baseOf = (w) => w?.base || w?.id
const resolveWidget = (entry) => {
  const id = entry?.id
  if (!id) return null
  const row = entry.row || defaultRow(id)
  if (id.startsWith('link:')) {
    const meta = MODULE_META[id.slice(5)]
    if (!meta) return null
    return {
      id,
      kind: 'link',
      code: id.slice(5),
      label: t(meta.titleKey),
      icon: meta.icon,
      path: meta.path,
      span: entry.span || 4,
      row,
      chip: 3,
    }
  }
  const def = widgetById(id)
  if (!def) return null
  return {
    ...def,
    base: def.module ? MODULE_DEFAULT_WIDGET[def.module] || def.id : def.id,
    label: widgetLabel(def),
    span: entry.span || def.span,
    row,
    featured: entry.featured === true,
  }
}

// 新布局首屏：左侧家庭照片，右侧两张短列表。旧排版继续使用原有卡片样式。
const DEFAULT_LAYOUT = [
  { id: 'photos', span: 8, row: 6, featured: true },
  { id: 'reminder', span: 4, row: 3, featured: true },
  { id: 'feed', span: 4, row: 3, featured: true },
  { id: 'anni', span: 4, row: 3 },
  { id: 'finance', span: 8, row: 3 },
]
const STORAGE_KEY = 'ihomy:guangchen:home:v3'
const LEGACY_KEY = 'ihomy:guangchen:home:v1'
// 账号同步键(服务端 sys_user_pref.pref_key):布局跟人走,换设备登录即恢复
const PREF_KEY = 'home.warm.v3'

// 解析交给 utils/warmHomeLayout(纯函数),组件注册表相关的默认值与合法性在这里注入
const parseStoredLayout = (raw) =>
  parseWarmLayout(raw, {
    spanOf: defaultSpan,
    rowOf: defaultRow,
    isValid: (item) => !!resolveWidget(item),
  })
const loadLayout = () => parseStoredLayout(readPref(STORAGE_KEY)) || parseStoredLayout(readPref(LEGACY_KEY))
// 用户已经动过布局:此后不再用服务端下发的旧排版覆盖页面
const layoutTouched = ref(false)
// 落盘 = 本机缓存 + 账号同步(登录后去抖上传)
const saveLayout = () => {
  layoutTouched.value = true
  writePref(STORAGE_KEY, PREF_KEY, JSON.stringify(layout.value))
}

const layout = ref((loadLayout() || DEFAULT_LAYOUT).map((e) => ({ ...e })))
const widgets = computed(() => layout.value.map(resolveWidget).filter(Boolean))

// 进页面与服务端对齐一次:账号里有布局就以它为准(换设备即恢复);
// 账号里还没有而本机有,则由 syncPref 把本机这份交上去。正在编辑时不覆盖。
const applyRemoteLayout = async () => {
  const raw = await syncPref(STORAGE_KEY, PREF_KEY)
  if (!raw || layoutTouched.value || editMode.value) return
  const parsed = parseStoredLayout(raw)
  if (parsed) layout.value = parsed
}
// 托盘按「功能 × 样式」列出:同一功能的两种样式各自是一条,可同时加进首页;侧栏入口卡片经拖入
const availableWidgets = computed(() =>
  WIDGET_DEFS.filter((w) => !layout.value.some((e) => e.id === w.id)).map((w) => ({
    ...w,
    label: widgetLabel(w),
    // 同一功能有多种样式时才在托盘里标样式名(单样式功能不挂冗余后缀)
    suffix: siblingsOf(w.id).length ? t(w.styleKey) : '',
  })),
)

// ========== 已添加模块集合(供侧栏在编辑模式标记「已拖入冲突」) ==========
const computeAddedCodes = () => {
  const codes = new Set()
  for (const e of layout.value) {
    const code = e.id.startsWith('link:') ? e.id.slice(5) : WIDGET_MODULE[e.id]
    if (code) codes.add(code)
  }
  return codes
}
watch(
  layout,
  () => {
    addedCodes.value = computeAddedCodes()
  },
  { deep: true, immediate: true },
)

// ========== 布局动画(FLIP:先量后放,统一驱动「变大缩小 / 邻居移动 / 回弹」) ==========
const gridEl = ref(null)
let entranceMedia
const flipAnims = new WeakMap()
const captureRects = () => {
  const m = new Map()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return m
  gridEl.value?.querySelectorAll('.gc-card').forEach((c) => {
    if (c.dataset.wid) m.set(c.dataset.wid, c.getBoundingClientRect())
  })
  return m
}
// 连续换位时，下一段动画从屏幕上正在显示的位置接续。
const FLIP_EASE = 'cubic-bezier(.2,.8,.2,1)'
const flipLayout = (prev) => {
  const grid = gridEl.value
  if (!grid || !prev.size) return
  const moves = []
  const cards = grid.querySelectorAll('.gc-card')
  cards.forEach((card) => flipAnims.get(card)?.cancel())
  cards.forEach((c) => {
    const id = c.dataset.wid
    if (!id || id === reorderPreview.value?.id || id === '__slot__') return
    const first = prev.get(id)
    if (!first) return
    const last = c.getBoundingClientRect()
    const dx = first.left - last.left
    const dy = first.top - last.top
    const dw = last.width ? first.width / last.width : 1
    const dh = last.height ? first.height / last.height : 1
    if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5 && Math.abs(dw - 1) < 0.01 && Math.abs(dh - 1) < 0.01) return
    moves.push({ el: c, dx, dy, dw, dh })
  })
  if (!moves.length) return
  moves.forEach(({ el, dx, dy, dw, dh }) => {
    flipAnims.set(
      el,
      el.animate(
        [
          { transform: `translate(${dx}px, ${dy}px) scale(${dw}, ${dh})`, transformOrigin: 'top left' },
          { transform: 'translate(0, 0) scale(1, 1)', transformOrigin: 'top left' },
        ],
        { duration: 220, easing: FLIP_EASE },
      ),
    )
  })
}

const applyLayout = (entries) => {
  const prev = captureRects()
  layout.value = entries.filter((e) => resolveWidget(e))
  nextTick(() => flipLayout(prev))
}
const addWidget = (id) => {
  if (layout.value.some((e) => e.id === id)) {
    ElMessage.info(t('warm.alreadyAdded'))
    return
  }
  applyLayout([...layout.value, { id, span: defaultSpan(id), row: defaultRow(id) }])
  ElMessage.success(t('warm.addedToast', { label: resolveWidget({ id, span: defaultSpan(id), row: defaultRow(id) })?.label || '' }))
}
const removeWidget = (w) => {
  applyLayout(layout.value.filter((e) => e.id !== w.id))
}
// ========== 样式切换(编辑态卡上「样式」菜单) ==========
// 同一功能注册了多种样式才出现;切换 = 原位替换布局项的 id(保留 span/row/featured,经 applyLayout 走 FLIP)
const styleMenuId = ref(null)
const styleOptions = (w) => siblingsOf(w.id)
const toggleStyleMenu = (id) => {
  styleMenuId.value = styleMenuId.value === id ? null : id
}
const switchStyle = (w, targetId) => {
  styleMenuId.value = null
  const i = layout.value.findIndex((e) => e.id === w.id)
  if (i < 0 || !widgetById(targetId)) return
  const next = [...layout.value]
  next[i] = { ...layout.value[i], id: targetId }
  applyLayout(next)
}
const resetLayout = () => {
  layout.value = DEFAULT_LAYOUT.map((e) => ({ ...e }))
  ElMessage.success(t('warm.layoutReset'))
}

// ========== 编辑模式(复用 appStore.homeEditMode,与光尘同一开关) ==========
const editMode = computed(() => appStore.homeEditMode)
const canEdit = computed(() => userStore.isOwner)
const startEdit = () => appStore.toggleHomeEditMode()
const finishEdit = () => appStore.toggleHomeEditMode()
// 排序和侧栏拖入在松手时保存，其余编辑在退出时保存。
watch(editMode, (on) => {
  if (on) {
    entranceMedia?.revert()
  } else {
    cancelReorder()
    styleMenuId.value = null
    saveLayout()
  }
})

// 卡片点击:仅整卡可跳转的组件(weather/anni/finance);photos 点击翻牌、feed/task/wish/reminder/item 由内部行/按钮跳转
// 判定按 base(功能的默认组件 id),这样同功能的另一种样式也走同一套点击/图标档行为
const LINK_IDS = ['weather', 'anni', 'finance']
const isLinkCard = (w) => LINK_IDS.includes(baseOf(w))
// 紧凑图标档:缩到 1 列(一行一列)/2 列(一行两列)时只渲染图标;2 列再带上名称(最简信息)。
// span<=2 一律走图标档,避免窄列里塞进完整内容而挤爆
const compact = (w) => w.span <= 2
// 图标档点击直达:组件按其所属模块路径,快捷入口用自身 path(daily 无独立页,不跳转)
const TILE_PATH = {
  weather: '/weather',
  feed: '/blog',
  photos: '/album',
  anni: '/plan?tab=anniversary',
  finance: '/book',
  item: '/item',
  task: '/task',
  wish: '/wish',
  reminder: '/plan?tab=today',
}
const onCardClick = (w) => {
  if (editMode.value) return
  if (compact(w)) {
    const path = w.kind === 'link' ? w.path : TILE_PATH[baseOf(w)]
    if (path) router.push(path)
    return
  }
  const dest = { weather: '/weather', anni: '/plan?tab=anniversary', finance: '/book' }[baseOf(w)]
  if (dest) router.push(dest)
}

// ========== 卡片排序与尺寸吸附 ==========
const resizing = ref(false)
const resizeState = ref(null)
const ROW_H = 80
const GAP = 16
// 可吸附的列宽档(1/2/4/6/8/12)与行高档(1/2/3/4/6),与 .gc-c*/.gc-r* 样式类一一对应;
// 1 列 = 一行一列、2 列 = 一行两列的紧凑图标档(见 compact())
const ALLOWED_SPANS = [1, 2, 4, 6, 8, 12]
const ALLOWED_ROWS = [1, 2, 3, 4, 6]

// ========== 侧栏拖入预览(按目标组件的默认尺寸占位) ==========
const { code: dragCode, label: dragLabel, onMove, onDrop: onWidgetDrop, setOverGrid } = useWarmWidgetDrag()
const dropPreview = ref(null) // { code, label, icon, index }
// 需登录态的组件:数据全来自需要登录的接口(记账/寻物/悬赏/提醒),未登录访客拉不到,
// 渲染出来只会是「本月还没有记账」「去登记户型图与物品」这类空态,反而显得演示内容残缺,故对游客隐藏。
// 愿望单不在此列——它已对游客开放(见 onMounted 的 wishApi.list 与后端 /wish/list 游客分支)。
// 先把落点应用到完整布局，再过滤当前成员不可见的卡片。
const GUEST_HIDDEN_WIDGETS = ['finance', 'item', 'task', 'reminder']
// 功能开关:所属功能被关掉(或「仅成年人」而当前未成年)的卡片不再渲染。
// 布局照旧留在缓存里,把开关改回「启用」就原样回来。
const gatedOut = (w) => {
  const code = w.kind === 'link' ? w.code : w.module
  return !!code && !isFeatureVisibleNow(code)
}
const displayWidgets = computed(() => {
  const rp = reorderPreview.value
  const dp = dropPreview.value
  const list = [...widgets.value]
  if (rp) {
    // 真卡悬浮在网格上方，原组件保持挂载。同尺寸占位独立参与 CSS Grid 排版。
    const from = list.findIndex((w) => w.id === rp.id)
    if (from >= 0) {
      const [source] = list.splice(from, 1)
      list.splice(rp.index, 0, { id: '__slot__', kind: 'preview', span: source.span, row: source.row })
      list.push(source)
    }
  } else if (dp) {
    const id = MODULE_DEFAULT_WIDGET[dp.code] || `link:${dp.code}`
    list.splice(dp.index, 0, {
      id: '__preview__',
      kind: 'preview',
      label: dp.label,
      icon: dp.icon,
      span: defaultSpan(id),
      row: defaultRow(id),
      hint: t('warm.dropHere'),
    })
  }
  const guest = !userStore.isLoggedIn
  return list.filter(
    (w) => (userStore.canReadBook || baseOf(w) !== 'finance') && !(guest && GUEST_HIDDEN_WIDGETS.includes(baseOf(w))) && !gatedOut(w),
  )
})
const previewMeta = (code) => {
  const def = widgetById(MODULE_DEFAULT_WIDGET[code])
  if (def) return { label: widgetLabel(def), icon: def.icon }
  const meta = MODULE_META[code]
  return meta ? { label: t(meta.titleKey), icon: meta.icon } : null
}
// 从网格测量卡片与占位的最终位置，交给 pickInsertIndex 判断落点。
// 卡片矩形用 offsetLeft/offsetTop 换算(布局位置),不能用 getBoundingClientRect——
// 邻居推挤的 FLIP 动画在跑,rect 会带 transform 的中间态,下标会连着算错、预览来回跳
const insertionIndexAt = (x, y, current, sourceId) => {
  const grid = gridEl.value
  if (!grid) return current ?? 0
  const g = grid.getBoundingClientRect()
  const boxes = Array.from(grid.querySelectorAll('.gc-card'))
    .filter((c) => c.dataset.wid && !c.classList.contains('gc-card-dragging'))
    .map((c) => {
      const left = g.left + c.offsetLeft
      const top = g.top + c.offsetTop
      const id = c.dataset.wid === '__slot__' ? sourceId : c.dataset.wid
      return { id, left, top, right: left + c.offsetWidth, bottom: top + c.offsetHeight }
    })
  return pickInsertIndex(
    boxes,
    layout.value.filter((e) => e.id !== sourceId),
    x,
    y,
    current,
    sourceId,
    g,
  )
}
const insertWidgetAt = (code, idx) => {
  const id = MODULE_DEFAULT_WIDGET[code] || `link:${code}`
  const resolved = resolveWidget({ id, span: defaultSpan(id), row: defaultRow(id) })
  if (!resolved) {
    dropPreview.value = null
    return
  }
  if (layout.value.some((e) => e.id === id)) {
    ElMessage.info(t('warm.alreadyAdded'))
    dropPreview.value = null
    return
  }
  dropPreview.value = null
  const list = [...layout.value]
  list.splice(Math.max(0, Math.min(idx, list.length)), 0, { id, span: defaultSpan(id), row: defaultRow(id) })
  applyLayout(list)
  saveLayout()
  ElMessage.success(t('warm.addedToast', { label: resolved.label }))
}
// 拖拽移动(鼠标事件驱动,同步更新):进入网格 → 幽灵变形为卡片 + 占位推挤;离开 → 还原
const updateDragPreview = (nx, ny) => {
  if (!dragCode.value) return
  const r = gridEl.value?.getBoundingClientRect()
  const inside = !!(r && nx >= r.left && nx <= r.right && ny >= r.top && ny <= r.bottom)
  if (inside) {
    setOverGrid(true)
    const idx = insertionIndexAt(nx, ny, dropPreview.value?.index, '__preview__')
    const cur = dropPreview.value
    if (!cur || cur.code !== dragCode.value || cur.index !== idx) {
      const prev = captureRects()
      const meta = previewMeta(dragCode.value)
      dropPreview.value = {
        code: dragCode.value,
        label: meta?.label || dragLabel.value || t('warm.newWidget'),
        icon: meta?.icon || '✨',
        index: idx,
      }
      nextTick(() => flipLayout(prev))
    }
  } else {
    setOverGrid(false)
    if (dropPreview.value) {
      const prev = captureRects()
      dropPreview.value = null
      nextTick(() => flipLayout(prev))
    }
  }
}
onMove((nx, ny) => updateDragPreview(nx, ny))
onWidgetDrop((c) => {
  if (dropPreview.value) insertWidgetAt(c, dropPreview.value.index)
})

watch(dragCode, (code) => {
  if (!code && dropPreview.value) {
    const prev = captureRects()
    dropPreview.value = null
    nextTick(() => flipLayout(prev))
  }
})

const {
  preview: reorderPreview,
  start: startReorder,
  cancel: cancelReorder,
} = useWarmCardReorder({
  gridEl,
  indexOf: (id) => layout.value.findIndex((w) => w.id === id),
  indexAt: insertionIndexAt,
  captureRects,
  flipLayout,
  commit: (id, index) => {
    const next = [...layout.value]
    const from = next.findIndex((w) => w.id === id)
    if (from < 0) return
    const [source] = next.splice(from, 1)
    next.splice(index, 0, source)
    layout.value = next
    saveLayout()
  },
})
const reorderStyle = (w) => {
  const rp = reorderPreview.value
  if (!rp) return
  // 暖居纸面样式会把真卡设为相对定位，提起时连同实测尺寸一起覆盖。
  if (w.id === rp.id) return { position: 'absolute', width: `${rp.width}px`, height: `${rp.height}px` }
  if (w.id === '__slot__') return { height: `${rp.height}px` }
}
const onReorderStart = (e, w) => {
  if (!editMode.value || resizing.value || dragCode.value || w.kind === 'preview') return
  if (e.target.closest('button, input, select, textarea, a, .gc-resize, .gc-style-menu')) return
  if (e.pointerType === 'touch' && !e.target.closest('.gc-grip')) return
  startReorder(e, w.id)
}

// 调整大小:按下右下角手柄拖拽,把卡片列宽吸附 4/6/8/12、行高吸附 2/3/4/6;
// 拖动中实时改 span/row,邻居经 CSS 栅格自动重排(实时预览),松手才 saveLayout 提交,拖回原位等于还原
// 缩放同样改用 Pointer Events:触屏/触控笔与鼠标共用,手柄加 touch-action:none 防止被页面滚动抢走
const onResizeStart = (e, w) => {
  if (e.button !== 0 || e.isPrimary === false || reorderPreview.value || resizing.value) return
  const card = e.currentTarget.closest('.gc-card')
  if (!card) return
  const grid = card.parentElement
  const gridW = grid.getBoundingClientRect().width
  const colW = (gridW - 11 * GAP) / 12
  resizeState.value = {
    id: w.id,
    pointerId: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    startWidth: card.getBoundingClientRect().width,
    startHeight: card.getBoundingClientRect().height,
    colW,
    currentSpan: w.span,
    currentRow: w.row,
  }
  resizing.value = true
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeUp)
  window.addEventListener('pointercancel', onResizeUp)
}
const onResizeMove = (e) => {
  const s = resizeState.value
  if (!s || e.pointerId !== s.pointerId) return
  const targetW = s.startWidth + (e.clientX - s.startX)
  const targetH = s.startHeight + (e.clientY - s.startY)
  let bestSpan = s.currentSpan
  let bestRow = s.currentRow
  let bestDist = Infinity
  for (const span of ALLOWED_SPANS) {
    const wpx = span * s.colW + (span - 1) * GAP
    const d = Math.abs(wpx - targetW)
    if (d < bestDist) {
      bestDist = d
      bestSpan = span
    }
  }
  bestDist = Infinity
  for (const row of ALLOWED_ROWS) {
    const hpx = row * ROW_H + (row - 1) * GAP
    const d = Math.abs(hpx - targetH)
    if (d < bestDist) {
      bestDist = d
      bestRow = row
    }
  }
  if (bestSpan !== s.currentSpan || bestRow !== s.currentRow) {
    const prev = captureRects()
    s.currentSpan = bestSpan
    s.currentRow = bestRow
    const i = layout.value.findIndex((x) => x.id === s.id)
    if (i >= 0) {
      layout.value[i] = { ...layout.value[i], span: bestSpan, row: bestRow }
    } // 拖动中仅实时预览,不落库
    nextTick(() => flipLayout(prev)) // 吸附档变化时动画过渡(放大缩小 + 邻居让位 + 回弹)
  }
}
const onResizeUp = (e) => {
  if (resizeState.value && e.pointerId !== undefined && e.pointerId !== resizeState.value.pointerId) return
  resizing.value = false
  resizeState.value = null
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeUp)
  window.removeEventListener('pointercancel', onResizeUp)
  // 松手仅结束拖拽,不落库;退出编辑(点「完成」或再次点编辑)时统一提交
}
onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeUp)
  window.removeEventListener('pointercancel', onResizeUp)
})

// ========== 内容丰富度(分两维,面积相关) ==========
// 纵向条数看「行数 row」:行越多数据条数越多(窄而高 → 纵向数据多)
// 横向丰富度看「列宽 span」:越宽横向信息越丰富(宽而矮 → 宽幅数据)
// 两维同时放大 → 横纵都丰富;同时缩小 → 横纵都缩紧
const vTier = (w) => ({ 2: 'S', 3: 'M', 4: 'L', 6: 'XL' })[w.row] || 'M'

const nFeed = (w) => ({ S: 2, M: 4, L: 6, XL: 9 })[vTier(w)] ?? 3
const nTask = (w) => ({ S: 2, M: 3, L: 5, XL: 8 })[vTier(w)] ?? 3
const nWish = (w) => ({ S: 2, M: 4, L: 6, XL: 10 })[vTier(w)] ?? 3
const nReminder = (w) => ({ S: 2, M: 4, L: 6, XL: 10 })[vTier(w)] ?? 3
const nItem = (w) => ({ S: 1, M: 2, L: 3, XL: 5 })[vTier(w)] ?? 2
const nAnni = (w) => ({ S: 1, M: 3, L: 4, XL: 6 })[vTier(w)] ?? 1

// ========== 数据 ==========
const feeds = ref([])
const bookSummary = ref({ income: 0, expense: 0, balance: 0 })
const items = ref([])
const tasks = ref([])
const wishes = ref([])
const reminders = ref([])
const photos = computed(() => appStore.photos || [])
const balance = computed(() => bookSummary.value?.balance ?? 0)
// 收支比例条
const financeTotal = computed(() => (Number(bookSummary.value?.income) || 0) + (Number(bookSummary.value?.expense) || 0))
const incomePct = computed(() => (financeTotal.value > 0 ? ((Number(bookSummary.value?.income) || 0) / financeTotal.value) * 100 : 0))
// 理财持仓(本金 / 持有笔数):本金不进收支,故单独一条展示
const financeList = ref([])
const financeHolding = computed(() => {
  const s = summarizePortfolio(financeList.value)
  return { principal: s.holdingPrincipal, count: s.holdingCount }
})

// ========== 寻物(搜索 + 语音 + 户型图预览) ==========
const itemHouse = ref(null)
const itemFloorPlan = ref({ rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 })
const itemImgTransform = ref(null) // 底图变换 {x,y,k}
const itemImgSize = ref({ w: 0, h: 0 })
const itemKeyword = ref('')
const itemResults = ref([])
const itemSearched = ref(false)
const itemSearching = ref(false)
const { recording: itemVoiceRecording, start: itemVoiceStart, stop: itemVoiceStop } = useVoiceRecorder()
const itemVoiceProcessing = ref(false)
let itemVoiceTimer = null

const defaultFloorOfHouse = (house) => pickDefaultFloor(house?.floorPlans)
const applyItemFloorPlan = ({ house, floor, data }) => {
  itemHouse.value = house
  itemImgTransform.value = null
  itemImgSize.value = { w: 0, h: 0 }
  if (!house) {
    itemFloorPlan.value = { rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 }
    return
  }
  itemFloorPlan.value = data || { rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 }
  if (house.floorPlans) {
    try {
      const cfg = JSON.parse(house.floorPlans)[floor]
      if (cfg?.img) itemImgTransform.value = { ...cfg.img }
    } catch {}
  }
  if (data?.imageUrl) {
    const img = new Image()
    img.onload = () => {
      if (itemHouse.value?.id !== house.id || itemFloorPlan.value?.imageUrl !== data.imageUrl) return
      itemImgSize.value = { w: img.naturalWidth, h: img.naturalHeight }
    }
    img.src = data.imageUrl
  }
}
const itemShowFloorPlan = (w) => w.row >= 3
const itemPathOf = (it) => [it.house_name, it.room_name, it.furniture_name || it.position].filter(Boolean).join(' / ')
const itemHitIds = computed(() => itemResults.value.map((r) => r.id))
const itemHitRoomIds = computed(() => {
  const hit = new Set(itemHitIds.value)
  const ids = new Set()
  ;(itemFloorPlan.value.items || []).forEach((it) => {
    if (hit.has(it.id) && it.roomId != null) ids.add(Number(it.roomId))
  })
  return [...ids]
})
const onItemSearch = async () => {
  const kw = (itemKeyword.value || '').trim()
  if (!kw) {
    clearItemSearch()
    return
  }
  itemSearched.value = true
  itemResults.value = await itemApi.list({ keyword: kw }).catch(() => [])
  if (itemResults.value.length) return
  itemSearching.value = true
  try {
    const r = await itemApi.aiFind({ query: kw }).catch(() => null)
    if (r?.matches?.length) itemResults.value = r.matches
  } finally {
    itemSearching.value = false
  }
}
const clearItemSearch = () => {
  itemKeyword.value = ''
  itemResults.value = []
  itemSearched.value = false
}
const toggleItemVoice = async () => {
  if (itemVoiceRecording.value) {
    await finishItemVoice()
    return
  }
  if (itemVoiceProcessing.value) return
  try {
    await itemVoiceStart()
    clearTimeout(itemVoiceTimer)
    itemVoiceTimer = setTimeout(() => {
      if (itemVoiceRecording.value) finishItemVoice()
    }, 10000)
  } catch (e) {
    ElMessage.warning(t('warm.micUnavailable'))
  }
}
const finishItemVoice = async () => {
  clearTimeout(itemVoiceTimer)
  if (!itemVoiceRecording.value) return
  itemVoiceProcessing.value = true
  try {
    const blob = await itemVoiceStop()
    const file = new File([blob], 'voice.wav', { type: 'audio/wav' })
    const r = await aiApi.transcribe(file, null)
    const text = (r.text || '').trim()
    if (!text) {
      ElMessage.warning(t('warm.noVoice'))
      return
    }
    itemKeyword.value = text
    await onItemSearch()
  } catch (e) {
  } finally {
    itemVoiceProcessing.value = false
  }
}
// 户型图 SVG 视图模型:解析房间多边形/家具/物品绝对坐标,自适应包围盒
const itemFloorPlanView = computed(() => {
  const fp = itemFloorPlan.value
  const rooms = (fp.rooms || [])
    .map((r) => {
      let poly = []
      try {
        poly = JSON.parse(r.geometry || '[]')
      } catch {}
      if (!Array.isArray(poly)) poly = []
      return { id: r.id, name: r.name, poly }
    })
    .filter((r) => r.poly.length >= 3)
  const hasFurn = (fp.furnitures || []).some((f) => f.x != null)
  if (!rooms.length && !hasFurn && !fp.imageUrl) return null

  const roomBox = {}
  const roomPts = []
  for (const r of rooms) {
    const xs = r.poly.map((p) => p.x),
      ys = r.poly.map((p) => p.y)
    const box = { minX: Math.min(...xs), minY: Math.min(...ys), maxX: Math.max(...xs), maxY: Math.max(...ys) }
    roomBox[r.id] = box
    roomPts.push({
      id: r.id,
      name: r.name,
      points: r.poly.map((p) => `${p.x},${p.y}`).join(' '),
      cx: (box.minX + box.maxX) / 2,
      cy: (box.minY + box.maxY) / 2,
    })
  }
  const furnitures = (fp.furnitures || [])
    .filter((f) => f.x != null)
    .map((f) => ({ id: f.id, name: f.name, x: f.x, y: f.y, w: f.w, h: f.h }))
  const furnitureById = Object.fromEntries(furnitures.map((f) => [f.id, f]))
  const items = (fp.items || [])
    .map((it) => {
      const fid = it.furnitureId,
        rid = it.roomId
      const relX = Number(it.relX ?? 0.5),
        relY = Number(it.relY ?? 0.5)
      let ax = null,
        ay = null
      if (fid != null && furnitureById[fid]) {
        const f = furnitureById[fid]
        ax = f.x + relX * f.w
        ay = f.y + relY * f.h
      } else if (rid != null && roomBox[rid]) {
        const b = roomBox[rid]
        ax = b.minX + relX * (b.maxX - b.minX)
        ay = b.minY + relY * (b.maxY - b.minY)
      }
      return { id: it.id, name: it.name, ax, ay }
    })
    .filter((it) => it.ax != null)

  let minX = Infinity,
    minY = Infinity,
    maxX = -Infinity,
    maxY = -Infinity
  const collect = (x, y) => {
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }
  rooms.forEach((r) => r.poly.forEach((p) => collect(p.x, p.y)))
  furnitures.forEach((f) => {
    collect(f.x, f.y)
    collect(f.x + f.w, f.y + f.h)
  })
  items.forEach((it) => collect(it.ax, it.ay))
  if (fp.imageUrl && itemImgTransform.value && itemImgSize.value.w) {
    const t = itemImgTransform.value
    collect(t.x, t.y)
    collect(t.x + itemImgSize.value.w * t.k, t.y + itemImgSize.value.h * t.k)
  }
  if (!isFinite(minX)) {
    minX = 0
    minY = 0
    maxX = 100
    maxY = 100
  }
  const pad = 26
  return {
    rooms: roomPts,
    furnitures,
    items,
    imageUrl: fp.imageUrl || null,
    imgTransform: itemImgTransform.value
      ? `translate(${itemImgTransform.value.x},${itemImgTransform.value.y}) scale(${itemImgTransform.value.k})`
      : '',
    viewBox: `${minX - pad} ${minY - pad} ${maxX - minX + pad * 2} ${maxY - minY + pad * 2}`,
  }
})

// 按「功能」判定可见:同一功能的多种样式共用一份数据(如列表 + 横向卡片都吃 feeds)
const visibleCard = (base) => displayWidgets.value.find((w) => baseOf(w) === base && !compact(w))
// 按「具体样式」判定可见:只有某个样式需要的数据,别让同功能的其它样式白拉一次
const visibleCardById = (id) => displayWidgets.value.find((w) => w.id === id && !compact(w))
const hasCard = (base) => () => Boolean(visibleCard(base))
const hasCardStyle = (id) => () => Boolean(visibleCardById(id))
const hasMap = () => Boolean(visibleCard('item') && itemShowFloorPlan(visibleCard('item')))
const hasItemList = () => Boolean(visibleCard('item') && !itemShowFloorPlan(visibleCard('item')))
const bindCard = (visible, request, target, initial, transform = (value) => value ?? initial) =>
  useHomeCardData(
    visible,
    scope,
    request,
    (data) => {
      target.value = transform(data)
    },
    () => {
      target.value = initial
    },
  )
const itemListData = bindCard(hasItemList, () => itemApi.list({ limit: 5 }), items, [])
const itemMapData = useHomeCardData(
  hasMap,
  scope,
  async () => {
    const [house] = await itemApi.houses()
    if (!house) return {}
    const floor = defaultFloorOfHouse(house)
    return { house, floor, data: await itemApi.floorPlan(house.id, floor) }
  },
  applyItemFloorPlan,
  () => applyItemFloorPlan({}),
)
// 本月收支概览与理财持仓只有「收支概览」样式会画 → 按样式判定,别让趋势卡多拉一次 summary
const holdingData = bindCard(hasCardStyle('finance'), () => financeApi.list(), financeList, [])
// 近半年趋势(记账·趋势样式):byMonth 补零交给 utils/bookTrend 的纯函数
const ledgerData = ref({})
const trendRows = computed(() => recentMonthRows(ledgerData.value?.byMonth))
const cardData = {
  feed: bindCard(hasCard('feed'), fetchFeed, feeds, []),
  reminder: bindCard(hasCard('reminder'), () => planApi.today(), reminders, [], buildAgenda),
  finance: bindCard(hasCardStyle('finance'), () => bookApi.summary(), bookSummary, {}),
  ledger: bindCard(hasCardStyle('finance:trend'), () => bookApi.ledger(), ledgerData, {}),
  wish: bindCard(hasCard('wish'), () => wishApi.list(), wishes, []),
  task: bindCard(
    hasCard('task'),
    () => taskApi.list(),
    tasks,
    [],
    (data) => (Array.isArray(data) ? data : data?.records || []).filter((task) => task.status !== 'CANCELLED'),
  ),
  get item() {
    return hasMap() ? itemMapData : itemListData
  },
}
// 卡片的加载/错误分支按「数据源」找请求:默认就是 base,个别样式换了数据源在注册表里写 dataKey
const cardKey = (w) => w.dataKey || baseOf(w)
watch(
  hasCard('daily'),
  (visible) => {
    if (visible) loadDaily()
  },
  { immediate: true },
)
// 注册表里带 component 的样式组件要的数据,统一在这里交给 props(ctx)
const widgetCtx = () => ({
  feeds: feeds.value,
  photos: photos.value,
  anni: anniversaries.value,
  trendRows: trendRows.value,
  editMode: editMode.value,
})
const retryCards = () => {
  for (const data of [...Object.values(cardData), holdingData]) {
    if (data.status.value === 'error') data.retry()
  }
}
onMounted(() => {
  if (!editMode.value) {
    entranceMedia = gsap.matchMedia()
    entranceMedia.add('(prefers-reduced-motion: no-preference)', () => {
      const cards = gridEl.value.querySelectorAll('.gc-card')
      if (cards.length) {
        gsap.from(cards, {
          y: 10,
          opacity: 0.4,
          duration: 0.34,
          stagger: { amount: 0.18 },
          ease: 'power2.out',
          clearProps: 'transform,opacity',
        })
      }
    })
  }
  window.addEventListener('online', retryCards)
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 60000)
  // 账号里的布局对齐一次(游客/拉取失败都直接返回,不影响首屏)
  applyRemoteLayout()
})
onBeforeUnmount(() => {
  entranceMedia?.revert()
  window.removeEventListener('online', retryCards)
  clearTimeout(itemVoiceTimer)
  clearInterval(clockTimer)
  if (itemVoiceRecording.value) itemVoiceStop()
})

const weatherTemp = computed(() => sunLight?.weather?.value?.temp ?? '—')
const weatherText = computed(() => sunLight?.weather?.value?.text || '')
const FEED_ICON = { blog: '📝', diary: '📖', photo: '📷', video: '🎬', wish: '🎁', task: '🎯', recipe: '🍳', book: '📚' }
const feedIcon = (type) => FEED_ICON[type] || '✨'
// 动态类型 → 图标芯片色相(博客陶土 / 影像暖沙 / 愿望鼠尾草 / 日记·任务暖木)
const FEED_CHIP = { blog: 1, diary: 5, photo: 2, video: 2, wish: 3, task: 5, recipe: 1, book: 3 }
const feedChipStyle = (type) => ({ '--chip': `var(--blob-${FEED_CHIP[type] || 1})` })
const feedSummary = (f) => feedSummaryOf(t, f)
const goFeed = (f) => {
  const path = feedPath(f)
  if (path) router.push(path)
}
const fmt = (n) => (Number(n) || 0).toFixed(2)

// 任务枚举映射(与 sys_dict_item 一致:OPEN/IN_PROGRESS/REVIEW/DONE/CANCELLED;奖励 NONE/POINTS/ITEM)
// 状态语义着色:进行中=主色 / 待确认=陶土 / 已完成=鼠尾草绿(设计稿 §3.4「已找到 绿标签」)
const TASK_STATUS_TAG = { OPEN: '', IN_PROGRESS: 'pri', REVIEW: 'acc', DONE: 'grn', CANCELLED: '' }
const TASK_REWARD_ICON = { NONE: '⭕', POINTS: '🎁', ITEM: '📦' }
const taskStatusLabel = (s) => dictText(t, 'taskStatus', s)
const taskIcon = (type) => TASK_REWARD_ICON[type] || '⭕'

// 问候语:走 now ref + interval,跨小时/切语言都能刷新(computed 内禁 new Date())
const now = ref(new Date())
let clockTimer = null
const greeting = computed(() => {
  const h = now.value.getHours()
  if (h < 6) return t('warm.greeting.lateNight')
  if (h < 12) return t('warm.greeting.morning')
  if (h < 18) return t('warm.greeting.afternoon')
  return t('warm.greeting.evening')
})
</script>

<style>
/* 补色强度档:陶土粉(--blob-1)与鼠尾草绿(--blob-3)原先只以 .12~.14 的雾状渐变存在,
 * 叠在米色卡面上与卡面只差个位数 RGB,读不出色相 —— 统一提到可读区间,并且立一条「标题色相锚点」 */
.gc-home {
  color: var(--color-text);
  --chip-a: 0.2;
  --bar-a: 0.8;
  --sill-a: 0.5;
  --sage-a: 0.3;
}
/* 暮色是 #33241A 深咖底,同一 alpha 在暗底上更向灰收敛,补色统一再提一档 */
html.theme-warm.dark .gc-home {
  --chip-a: 0.26;
  --bar-a: 1;
  --sill-a: 0.58;
  --sage-a: 0.36;
}

.gc-main-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}
/* 分享门脸:与下方问候标题同宽 16:9 横幅 */
.gc-door-sign {
  margin-bottom: 20px;
}
.gc-h2 {
  font-size: 20px;
  margin: 0;
  font-weight: 650;
  color: var(--color-text);
}
.gc-subline {
  font-size: 12.5px;
  color: var(--color-text-tertiary);
  margin-top: 3px;
}
.gc-weather-link {
  margin-inline-start: 10px;
  padding-inline-start: 10px;
  border-inline-start: 1px solid var(--color-line);
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.gc-head-btns {
  display: flex;
  gap: 8px;
}

.gc-grid {
  position: relative;
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  grid-auto-rows: 80px;
  gap: 16px;
}
.gc-grid:has(.gc-card-dragging) {
  overflow: clip;
  overflow-anchor: none;
  cursor: grabbing;
}
.gc-editing .gc-grid:empty {
  min-height: 120px;
}
.gc-c12 {
  grid-column: span 12;
}
.gc-c8 {
  grid-column: span 8;
}
.gc-c6 {
  grid-column: span 6;
}
.gc-c4 {
  grid-column: span 4;
}
.gc-c2 {
  grid-column: span 2;
}
.gc-c1 {
  grid-column: span 1;
}
.gc-r1 {
  grid-row: span 1;
}
.gc-r2 {
  grid-row: span 2;
}
.gc-r3 {
  grid-row: span 3;
}
.gc-r4 {
  grid-row: span 4;
}
.gc-r6 {
  grid-row: span 6;
}

/* 紧凑图标档(1/2 列):收起内边距,只留图标 + 最简名称 */
.gc-card-tile {
  padding: 8px;
}
.gc-tile {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
}
.gc-tile-icon {
  font-size: 24px;
  line-height: 1;
}
.gc-tile-label {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--color-text-secondary);
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

@media (prefers-reduced-motion: no-preference) {
  .gc-home:not(.gc-editing) :is(.gc-row, .gc-daily-know, .gc-daily-pic) {
    animation: gc-content-arrive 280ms ease-out backwards;
  }
  .gc-home:not(.gc-editing) .gc-row:nth-child(2) {
    animation-delay: 35ms;
  }
  .gc-home:not(.gc-editing) .gc-row:nth-child(n + 3) {
    animation-delay: 70ms;
  }
  .gc-home:not(.gc-editing) :is(.gc-meter > i, .gc-fin-bar) {
    transform-origin: left center;
    animation: gc-balance-reveal 440ms ease-out;
  }
  .gc-home:not(.gc-editing) .gc-item-dot.hit {
    transform-box: fill-box;
    transform-origin: center;
    animation: gc-locate-item 420ms ease-out;
  }
  .gc-home:not(.gc-editing) :is(.gc-tile-icon, .gc-link-icon, .gc-row .gc-ic) {
    transition: transform 180ms ease-out;
  }
  .gc-home:not(.gc-editing) .gc-card:focus-visible :is(.gc-tile-icon, .gc-link-icon) {
    transform: translateY(-2px);
  }
  .gc-home:not(.gc-editing) .gc-row:focus-visible .gc-ic {
    transform: translateX(2px);
  }
  @media (hover: hover) {
    .gc-home:not(.gc-editing) .gc-card:hover :is(.gc-tile-icon, .gc-link-icon) {
      transform: translateY(-2px);
    }
    .gc-home:not(.gc-editing) .gc-row[role='button']:hover .gc-ic {
      transform: translateX(2px);
    }
  }
}
@keyframes gc-content-arrive {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
}
@keyframes gc-balance-reveal {
  from {
    transform: scaleX(0);
  }
}
@keyframes gc-locate-item {
  50% {
    transform: scale(1.6);
  }
}

/* 卡片 = 家具块 */
.gc-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 18px;
  position: relative;
  overflow: hidden;
  transition: box-shadow 0.35s ease;
}
.gc-card:hover {
  box-shadow: var(--shadow-hover);
}
.gc-card-h3 {
  font-size: 13px;
  font-weight: 650;
  margin: 0 0 12px;
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 色相锚点:每张卡标题前一条 3px 竖条,取该卡的 --chip(陶土/鼠尾草/暖沙/暖木),
 * 让 6 张卡不再共用同一个「米色面 + 棕字」 */
.gc-card-h3::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  flex-shrink: 0;
  background: rgba(var(--chip, var(--color-brand-rgb)), var(--bar-a));
}
/* 右侧附属文字(收支月份/寻物户型名)推到行尾;末位是「更多」按钮时由 .gc-more 自己的 margin-left:auto 接管 */
.gc-card-h3 > .gc-muted:last-child {
  margin-left: auto;
}
.gc-muted {
  font-size: 11.5px;
  color: var(--color-text-tertiary);
}
.gc-val-lg {
  font-size: 16px;
  font-weight: 650;
  color: var(--color-brand);
  margin-bottom: 4px;
}
.gc-flex-baseline {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.gc-ctrl-row {
  display: flex;
  gap: 8px;
  margin: 4px 0 12px;
  flex-wrap: wrap;
}
.gc-empty {
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding: 20px 0;
  text-align: center;
}
.gc-empty-link {
  cursor: pointer;
}

/* 编辑模式 */
.gc-card-edit {
  border-style: dashed;
  border-color: var(--color-accent, var(--color-brand));
}
.gc-card-link {
  cursor: pointer;
}
.gc-card-edit {
  cursor: grab;
  user-select: none;
}
.gc-card.gc-card-dragging {
  inset: 0 auto auto 0;
  grid-area: auto;
  box-sizing: border-box;
  z-index: var(--z-content);
  pointer-events: none;
  cursor: grabbing;
  box-shadow: var(--shadow-hover);
  will-change: translate;
}
/* 占位参与网格排版，邻居按放置后的尺寸让位。 */
.gc-card-preview {
  border-style: dashed;
  border-color: var(--color-accent, var(--color-brand));
  background: rgba(var(--color-brand-rgb), 0.05);
  box-shadow: none;
  cursor: copy;
}
.gc-preview-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-text-secondary);
}
.gc-preview-icon {
  font-size: 30px;
  line-height: 1;
}
.gc-preview-label {
  font-size: 14px;
  font-weight: 650;
  color: var(--color-text);
}
.gc-preview-hint {
  font-size: 11.5px;
  color: var(--color-text-tertiary);
}
.gc-del {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  border: none;
  display: grid;
  place-items: center;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  background: var(--color-card-2);
  color: var(--color-text-secondary);
  box-shadow: var(--shadow);
}
.gc-del:hover {
  background: var(--color-accent, var(--color-brand));
  color: var(--color-card);
}
.gc-grip {
  position: absolute;
  top: 10px;
  right: 42px;
  z-index: 2;
  width: 22px;
  height: 24px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  cursor: grab;
  font-size: 13px;
  letter-spacing: -2px;
  color: var(--color-text-tertiary);
  user-select: none;
}
.gc-grip:active {
  cursor: grabbing;
}
.gc-resize {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 2;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
}
/* 触屏编辑:手柄吃指针、不触发页面滚动;命中区放大到手指能稳稳按住 */
.gc-grip,
.gc-resize {
  touch-action: none;
}
@media (pointer: coarse) {
  .gc-grip {
    top: 6px;
    right: 46px;
    width: 34px;
    height: 34px;
    font-size: 15px;
  }
  .gc-resize {
    width: 38px;
    height: 38px;
  }
  .gc-del {
    top: 6px;
    right: 6px;
    width: 32px;
    height: 32px;
    font-size: 14px;
  }
}
.gc-resize::after {
  content: '';
  position: absolute;
  right: 5px;
  bottom: 5px;
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--color-text-tertiary);
  border-bottom: 2px solid var(--color-text-tertiary);
  border-bottom-right-radius: 2px;
}

/* 样式切换(编辑态):卡左上角的文字键 + 内联菜单。
 * 不用 el-dropdown —— 它 teleport 到 body,脱出 .gc-card 的 overflow,也带一串 z-index 规则要挪 */
.gc-style-btn {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  height: 24px;
  padding: 0 9px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 11.5px;
  font-weight: 600;
  background: var(--color-card-2);
  color: var(--color-text-secondary);
  box-shadow: var(--shadow);
}
.gc-style-btn:hover,
.gc-style-btn.on {
  background: var(--color-accent, var(--color-brand));
  color: var(--color-card);
}
.gc-style-menu {
  position: absolute;
  top: 40px;
  left: 10px;
  z-index: 3;
  min-width: 118px;
  padding: 6px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: var(--shadow-hover);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.gc-style-tip {
  padding: 2px 8px 4px;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
}
.gc-style-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text);
  text-align: left;
}
.gc-style-item:hover {
  background: var(--color-line);
}
.gc-style-ic {
  font-size: 13px;
}
@media (pointer: coarse) {
  .gc-style-btn {
    top: 6px;
    left: 6px;
    height: 32px;
    padding: 0 11px;
    font-size: 12.5px;
  }
  .gc-style-menu {
    top: 44px;
    left: 6px;
  }
}
.gc-edit-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  background: var(--color-card-2);
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  padding: 8px 14px;
  margin: -8px 0 16px;
}

/* 照片卡牌堆:卡片须为 flex 纵向容器,stack 的 flex:1 才能撑满剩余高度(否则子项全 absolute → 高度塌缩为 0 → 照片不可见) */
.gc-photos {
  display: flex;
  flex-direction: column;
}
/* 注册表样式组件(轮播/卡片流/趋势图)同理:组件根节点的 flex:1 要落在纵向容器里才撑满卡片 */
.gc-card-styled {
  display: flex;
  flex-direction: column;
}
/* 头部「更多」小链接(照片→相册 / 寻物→管理) */
.gc-more {
  all: unset;
  margin-left: auto;
  cursor: pointer;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  transition: color 0.2s;
}
.gc-more:hover {
  color: var(--color-brand);
}

/* 收支比例条 */
.gc-fin-bar {
  display: flex;
  height: 8px;
  border-radius: 6px;
  overflow: hidden;
  margin-top: 12px;
  background: var(--color-line);
}
.gc-fin-bar i {
  display: block;
  height: 100%;
}
.gc-fin-bar i.in {
  background: var(--color-green);
}
.gc-fin-bar i.out {
  background: var(--color-accent);
}

/* 寻物搜索框 + 语音 */
.gc-item-card {
  display: flex;
  flex-direction: column;
}
.gc-item-search {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.gc-item-input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--color-border);
  background: var(--color-line);
  color: var(--color-text);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  transition:
    border-color 0.2s,
    background 0.2s;
}
.gc-item-input:focus {
  border-color: var(--color-brand);
  background: var(--color-card);
}
.gc-item-input::placeholder {
  color: var(--color-text-tertiary);
}
.gc-item-voice {
  flex-shrink: 0;
  padding: 6px 12px;
}
.gc-item-voice.recording {
  background: var(--color-accent);
  color: #fff7f0;
  border-color: var(--color-accent);
  animation: gcVoicePulse 1.2s ease-in-out infinite;
}
@keyframes gcVoicePulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(168, 72, 58, 0.4);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(168, 72, 58, 0);
  }
}

/* 户型图预览 */
.gc-item-plan {
  position: relative;
  flex: 1;
  min-height: 0;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  background: linear-gradient(160deg, var(--color-bg-2), var(--color-card-2));
  box-shadow: inset 0 0 0 1px var(--color-line);
}
.gc-item-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}
.gc-item-bg {
  opacity: 0.5;
}
/* 户型图:房间/家具原先把「光尘」主色 rgb(184,140,110) 写死,暖居下不跟随主题 —— 改走 --color-brand-rgb */
.gc-item-room {
  fill: rgba(var(--color-brand-rgb), 0.1);
  stroke: rgba(var(--color-brand-rgb), 0.5);
  stroke-width: 1.6;
  stroke-linejoin: round;
}
.gc-item-room.hit {
  fill: rgba(var(--color-brand-rgb), 0.22);
  stroke: var(--color-brand);
}
.gc-item-room-label {
  fill: var(--color-text-secondary);
  font-size: 12px;
  text-anchor: middle;
  pointer-events: none;
}
.gc-item-furn {
  fill: rgba(var(--color-brand-rgb), 0.2);
  stroke: rgba(var(--color-brand-rgb), 0.45);
  stroke-width: 1.2;
}
.gc-item-furn-label {
  fill: var(--color-text-tertiary);
  font-size: 10px;
  text-anchor: middle;
  pointer-events: none;
}
.gc-item-dot {
  fill: var(--color-brand);
  stroke: var(--color-card);
  stroke-width: 1.5;
}
.gc-item-dot.hit {
  fill: var(--color-accent);
}
.gc-item-dot-label {
  fill: var(--color-text-secondary);
  font-size: 10px;
  text-anchor: middle;
  pointer-events: none;
}
.gc-item-results {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 46%;
  max-height: 60%;
  overflow-y: auto;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 6px 10px;
  z-index: 5;
}
.gc-item-results .gc-row {
  font-size: 12px;
  padding: 7px 0;
}
.gc-item-nohit {
  position: absolute;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--color-card);
  box-shadow: var(--shadow);
  white-space: nowrap;
}

/* 添加组件托盘 */
.gc-add-tray {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--color-card);
  border: 1px dashed var(--color-border);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.gc-add-label {
  font-size: 12px;
  font-weight: 650;
  color: var(--color-text-tertiary);
  margin-right: 4px;
}
.gc-add-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 12.5px;
  border: 1px solid var(--color-border);
  background: var(--color-line);
  color: var(--color-text-secondary);
  transition: 0.2s;
}
.gc-add-item:hover {
  background: var(--color-card-2);
  color: var(--color-text);
  border-color: var(--color-brand);
}
.gc-add-icon {
  font-size: 14px;
}
/* 同一功能的多种样式在托盘里按「功能 · 样式」列出,样式名淡一档 */
.gc-add-style {
  font-size: 11.5px;
  color: var(--color-text-tertiary);
}

/* 快捷入口卡片 */
.gc-link-body {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 4px 4px;
  cursor: pointer;
}
.gc-link-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 20px;
  background: rgba(var(--blob-3), var(--chip-a));
  flex-shrink: 0;
}
.gc-link-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  flex: 1;
}
.gc-link-arrow {
  font-size: 16px;
  color: var(--color-text-tertiary);
  transition:
    transform 0.2s ease,
    color 0.2s ease;
}
.gc-link-body:hover .gc-link-arrow {
  transform: translateX(3px);
  color: var(--color-brand);
}

/* 每日一图 / 每日知识 */
.gc-daily-card {
  display: flex;
  flex-direction: column;
}
.gc-daily {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.gc-daily-pic {
  position: relative;
  flex: 1;
  min-height: 72px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-line);
}
.gc-daily-pic img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.gc-daily-cap {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 6px 9px;
  font-size: 10.5px;
  color: #fff;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gc-daily-know {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--color-text-secondary);
  max-height: 4.5em;
  overflow: hidden;
}

.gc-weather {
  container-type: inline-size;
  min-width: 0;
}
.gc-weather:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

/* 按钮(扩展 primary/accent) */
.gc-btn.primary {
  background: var(--color-brand);
  border-color: var(--color-brand);
  color: var(--color-card);
  box-shadow: var(--shadow);
}
.gc-btn.primary:hover {
  background: var(--color-brand-hover);
}
.gc-btn.accent {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: #fff7f0;
  box-shadow: var(--shadow);
}

/* 标签 */
.gc-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 11px;
  border-radius: 9px;
  font-size: 12px;
  background: var(--color-line);
  color: var(--color-text-secondary);
}
.gc-tag.acc {
  background: var(--color-accent);
  color: #fff7f0;
}
.gc-tag.grn {
  background: var(--color-green);
  color: var(--color-card);
}
.gc-tag.pri {
  background: var(--color-brand);
  color: var(--color-card);
}

/* 进度条 */
/* 进度条:填充改纯陶土实色 —— 原先 brand→accent 渐变在窄幅里大半还是棕色,是页面唯一能读出陶土粉的地方却读不出来 */
.gc-meter {
  height: 10px;
  border-radius: 6px;
  background: var(--color-line);
  overflow: hidden;
  margin-top: 8px;
}
.gc-meter > i {
  display: block;
  height: 100%;
  background: var(--color-accent);
  border-radius: 6px;
}
.gc-days {
  color: var(--color-accent);
  font-weight: 650;
}
.gc-anni-more {
  margin-top: 8px;
}

/* 收支 */
.gc-fin-row {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 12.5px;
}
.gc-fin {
  font-variant-numeric: tabular-nums;
}
.gc-fin.in {
  color: var(--color-green);
}
.gc-fin.out {
  color: var(--color-accent);
}
/* 理财持仓(本月收支卡底部一条,可点进理财页签) */
.gc-fin-holding {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
  padding: 5px 10px;
  border: 0;
  border-radius: 8px;
  background: rgba(160, 140, 110, 0.1);
  color: inherit;
  font: inherit;
  font-size: 12px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s;
}
.gc-fin-holding:hover {
  background: rgba(160, 140, 110, 0.18);
}
.gc-fin-holding b {
  font-variant-numeric: tabular-nums;
}
.gc-fin-holding-label {
  opacity: 0.55;
}

/* 列表 */
.gc-list .gc-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px solid var(--color-line);
  font-size: 13px;
  cursor: pointer;
}
.gc-list .gc-row:last-child {
  border-bottom: none;
}
.gc-list .gc-row .gc-muted {
  margin-left: auto;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 45%;
}
.gc-list .gc-row > .gc-tag {
  margin-left: auto;
}
.gc-featured[data-wid='feed'],
.gc-featured[data-wid='reminder'] {
  display: flex;
  flex-direction: column;
}
.gc-featured .gc-list {
  min-height: 0;
  overflow-y: auto;
}
.gc-featured .gc-list .gc-row {
  padding-block: 7px;
}
.gc-feed-entry {
  display: grid;
  min-width: 0;
  gap: 2px;
  line-height: 1.4;
}
.gc-feed-title {
  color: var(--color-text);
  font-weight: 600;
  overflow-wrap: anywhere;
}
.gc-feed-entry small {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.gc-ic {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 13px;
  background: rgba(var(--chip, var(--blob-1)), var(--chip-a));
  flex-shrink: 0;
}
.gc-val {
  font-weight: 650;
  color: var(--color-brand);
}
.gc-val.done {
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}

/* 编辑态下卡片内部禁止点击/文本选择 */
.gc-editing .gc-card {
  user-select: none;
}

/* 过渡 */
.gc-fade-enter-active,
.gc-fade-leave-active {
  transition: opacity 0.25s ease;
}
.gc-fade-enter-from,
.gc-fade-leave-to {
  opacity: 0;
}

@media (max-width: 880px) {
  .gc-grid {
    grid-auto-rows: auto;
  }
  .gc-c1,
  .gc-c2,
  .gc-c4,
  .gc-c6,
  .gc-c8,
  .gc-c12 {
    grid-column: span 12;
  }
  .gc-r1,
  .gc-r2,
  .gc-r3,
  .gc-r4,
  .gc-r6 {
    grid-row: auto;
  }
  .gc-card-tile {
    min-height: 72px;
  }
}
</style>
