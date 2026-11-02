<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('games.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('games.title') }}</span>
        <el-select
          v-if="seriesOptions.length"
          v-model="activeSeries"
          size="small"
          clearable
          class="game-series-filter"
          :placeholder="$t('games.allSeries')"
        >
          <el-option v-if="unsortedCount" :label="$t('games.unsortedSeries')" :value="UNSERIES" />
          <el-option v-for="s in seriesOptions" :key="s" :label="s" :value="s" />
        </el-select>
      </div>
      <div class="tb-right">
        <el-button v-if="unsortedCount" size="small" round :loading="autoSeriesLoading" @click="autoClassify">
          <el-icon><MagicStick /></el-icon>
          {{ $t('games.autoSeries') }}
        </el-button>
        <el-button size="small" round @click="wishOpen = true">{{ $t('wish.gameWish') }}</el-button>
        <el-button type="primary" size="small" round @click="openImport">
          <el-icon><Plus /></el-icon>
          {{ $t('games.import') }}
        </el-button>
      </div>
    </PageToolbar>

    <!-- 分类筛选:只列出当前有游戏的分类,带数量;「全部」恒在第一 -->
    <div v-if="categoryChips.length > 1" class="game-cats">
      <button
        v-for="c in categoryChips"
        :key="c.value"
        type="button"
        class="game-cat"
        :class="{ active: activeCategory === c.value }"
        :aria-pressed="activeCategory === c.value"
        @click="activeCategory = c.value"
      >
        {{ c.label }}
        <span class="game-cat-count">{{ c.count }}</span>
      </button>
    </div>

    <div v-loading="loading" class="tool-grid">
      <!-- 固定入口(花园/连连看/数独/2048)同样参与分类筛选 -->
      <div v-a11y-click v-for="b in visibleBuiltins" :key="b.key" class="tool-card card" @click="$router.push(b.path)">
        <div class="tool-icon">
          <el-icon :size="30"><component :is="b.icon" /></el-icon>
        </div>
        <div class="tool-name">{{ $t(b.titleKey) }}</div>
        <div class="tool-desc">{{ $t(b.descKey) }}</div>
        <div class="tool-meta">
          <div class="tool-meta-left">
            <div class="tool-type-tag cat-tag">{{ categoryText(b.category) }}</div>
          </div>
          <span class="tool-enter"
            >{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon
          ></span>
        </div>
      </div>

      <!-- 导入的小游戏(LINK 为外部网页游戏,点卡片新标签页打开) -->
      <div v-a11y-click v-for="g in visibleGames" :key="g.id" class="tool-card card" @click="play(g)">
        <div class="tool-icon">
          <el-icon :size="30">
            <Link v-if="g.type === 'LINK'" />
            <VideoPlay v-else />
          </el-icon>
        </div>
        <div class="tool-name">{{ g.name }}</div>
        <div class="tool-desc">{{ g.description || $t('games.noDesc') }}</div>
        <div class="tool-meta">
          <div class="tool-meta-left">
            <div class="tool-type-tag cat-tag">{{ categoryText(g.category) }}</div>
            <div v-if="g.series" class="tool-type-tag series-tag" :title="$t('games.seriesLabel')">
              <el-icon><Collection /></el-icon>{{ g.series }}
            </div>
            <div class="tool-type-tag">{{ typeText(g) }}</div>
            <el-tooltip v-if="g.hasAccount" :content="$t('games.account.savedFlag')" placement="top">
              <el-icon class="tool-account-flag"><Key /></el-icon>
            </el-tooltip>
          </div>
          <span class="tool-enter">
            {{ g.type === 'LINK' ? $t('games.open') : $t('games.play') }}<el-icon><ArrowRight /></el-icon
          ></span>
          <el-dropdown trigger="click" @command="(cmd) => onGameAction(cmd, g)">
            <span class="game-more" @click.stop
              ><el-icon><MoreFilled /></el-icon
            ></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">{{ $t('games.edit') }}</el-dropdown-item>
                <el-dropdown-item v-if="g.type === 'LINK'" command="account">{{ $t('games.account.title') }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided>{{ $t('games.delete') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div v-if="!loading && games.length === 0 && !activeSeries && activeCategory === 'ALL'" class="tool-empty">
        {{ $t('games.empty') }}
      </div>
      <div v-else-if="!loading && visibleTotal === 0" class="tool-empty">{{ $t('games.emptyFiltered') }}</div>
    </div>

    <!-- 导入对话框(游戏文件 / 网页游戏两种方式) -->
    <el-dialog v-model="importVisible" append-to-body :title="$t('games.import')" width="440px" destroy-on-close>
      <el-radio-group v-model="importMode" size="small" class="import-mode">
        <el-radio-button value="file">{{ $t('games.importModeFile') }}</el-radio-button>
        <el-radio-button value="link">{{ $t('games.importModeLink') }}</el-radio-button>
      </el-radio-group>
      <el-form label-position="top">
        <el-form-item v-if="importMode === 'file'" :label="$t('games.file')">
          <input ref="fileInput" type="file" accept=".swf,.gba" class="game-file-input" @change="onFilePicked" />
        </el-form-item>
        <el-form-item v-else :label="$t('games.url')">
          <el-input v-model="importForm.url" maxlength="500" :placeholder="$t('games.urlPlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="$t('games.name')">
          <el-input v-model="importForm.name" maxlength="100" :placeholder="$t('games.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('games.categoryLabel')">
          <el-select v-model="importForm.category" style="width: 100%">
            <el-option v-for="c in GAME_CATEGORIES" :key="c" :label="categoryText(c)" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('games.seriesLabel')">
          <el-select
            v-model="importForm.series"
            filterable
            allow-create
            clearable
            default-first-option
            style="width: 100%"
            :placeholder="$t('games.seriesPlaceholder')"
          >
            <el-option v-for="s in seriesOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('games.desc')">
          <el-input v-model="importForm.description" type="textarea" :rows="2" maxlength="500" :placeholder="$t('games.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="importVisible = false">{{ $t('games.cancel') }}</el-button>
        <el-button type="primary" size="small" :loading="importing" @click="submitImport">{{ $t('games.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" append-to-body :title="$t('games.edit')" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item v-if="editGameType === 'LINK'" :label="$t('games.url')">
          <el-input v-model="editForm.linkUrl" maxlength="500" :placeholder="$t('games.urlPlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="$t('games.name')">
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$t('games.categoryLabel')">
          <el-select v-model="editForm.category" style="width: 100%">
            <el-option v-for="c in GAME_CATEGORIES" :key="c" :label="categoryText(c)" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('games.seriesLabel')">
          <el-select
            v-model="editForm.series"
            filterable
            allow-create
            clearable
            default-first-option
            style="width: 100%"
            :placeholder="$t('games.seriesPlaceholder')"
          >
            <el-option v-for="s in seriesOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('games.desc')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="500" :placeholder="$t('games.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editVisible = false">{{ $t('games.cancel') }}</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="submitEdit">{{ $t('games.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 站点账号对话框:记住外部游戏网站的登录信息,只存自己名下 -->
    <el-dialog v-model="accountVisible" append-to-body :title="$t('games.account.title')" width="440px" destroy-on-close>
      <p class="account-hint">{{ $t('games.account.hint') }}</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="$t('games.account.username')">
          <el-input v-model="accountForm.siteUsername" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('games.account.password')">
          <el-input v-model="accountForm.sitePassword" type="password" show-password maxlength="200" autocomplete="new-password" />
        </el-form-item>
        <el-form-item :label="$t('games.account.remark')">
          <el-input v-model="accountForm.remark" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="accountExists" size="small" type="danger" plain @click="removeAccount">
          {{ $t('games.account.deleteBtn') }}
        </el-button>
        <el-button size="small" @click="accountVisible = false">{{ $t('games.cancel') }}</el-button>
        <el-button type="primary" size="small" :loading="accountSaving" @click="submitAccount">{{ $t('games.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 许愿想加的游戏:只有「想加什么游戏」这一个语义,不带来源记录ID -->
    <AddToWishDialog v-model="wishOpen" :source="wishSource" :title-placeholder="$t('wish.gameTitlePlaceholder')" />
  </div>
</template>

<script setup>
import { computed, ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Sunny,
  VideoPlay,
  ArrowRight,
  MoreFilled,
  Grid,
  Cpu,
  Histogram,
  Link,
  Key,
  MagicStick,
  Collection,
  Aim,
  Tickets,
  Box,
  Cherry,
} from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import AddToWishDialog from '@/components/wish/AddToWishDialog.vue'
import { gameApi } from '@/api'
import { dictText } from '@/utils/dict'
import { GAME_CATEGORIES, countGameCategories, inGameCategory, normalizeGameCategory } from '@/utils/games/gameCategory'
import { UNSERIES, inGameSeries, normalizeGameSeries, seriesOptionsOf } from '@/utils/games/gameSeries'

const router = useRouter()
const { t } = useI18n()

const games = ref([])
const loading = ref(false)
const activeCategory = ref('ALL')
const activeSeries = ref('')
const autoSeriesLoading = ref(false)

/** 站内固定入口也按同一套分类参与筛选;图标直接引用组件 */
const builtinGames = [
  {
    key: 'garden',
    titleKey: 'games.garden.title',
    descKey: 'games.garden.desc',
    icon: Sunny,
    path: '/tools/plant',
    category: 'SIMULATION',
  },
  {
    key: 'petlink',
    titleKey: 'games.petlink.title',
    descKey: 'games.petlink.desc',
    icon: Grid,
    path: '/games/petlink',
    category: 'PUZZLE',
  },
  { key: 'sudoku', titleKey: 'games.sudoku.title', descKey: 'games.sudoku.desc', icon: Cpu, path: '/games/sudoku', category: 'PUZZLE' },
  { key: 'g2048', titleKey: 'games.g2048.title', descKey: 'games.g2048.desc', icon: Histogram, path: '/games/2048', category: 'PUZZLE' },
  {
    key: 'minesweeper',
    titleKey: 'games.minesweeper.title',
    descKey: 'games.minesweeper.desc',
    icon: Aim,
    path: '/games/minesweeper',
    category: 'PUZZLE',
  },
  {
    key: 'solitaire',
    titleKey: 'games.solitaire.title',
    descKey: 'games.solitaire.desc',
    icon: Tickets,
    path: '/games/solitaire',
    category: 'CASUAL',
  },
  { key: 'tetris', titleKey: 'games.tetris.title', descKey: 'games.tetris.desc', icon: Box, path: '/games/tetris', category: 'ARCADE' },
  { key: 'snake', titleKey: 'games.snake.title', descKey: 'games.snake.desc', icon: Cherry, path: '/games/snake', category: 'ARCADE' },
]

const categoryText = (category) => dictText(t, 'game_category', normalizeGameCategory(category))

const seriesOptions = computed(() => seriesOptionsOf(games.value))
const unsortedCount = computed(() => games.value.filter((g) => !normalizeGameSeries(g.series)).length)

/** 分类计数跟随当前系列筛选(系列为空时含固定入口),避免出现「计数有、点进去空白」的组合 */
const categoryPool = computed(() =>
  activeSeries.value ? games.value.filter((g) => inGameSeries(g, activeSeries.value)) : [...builtinGames, ...games.value],
)
const categoryCounts = computed(() => countGameCategories(categoryPool.value))

/** 筛选条只显示有游戏的分类(含固定入口),避免一排空分类 */
const categoryChips = computed(() => {
  const chips = [{ value: 'ALL', label: t('games.allGames'), count: categoryPool.value.length }]
  for (const c of GAME_CATEGORIES) {
    if (categoryCounts.value[c] > 0) chips.push({ value: c, label: categoryText(c), count: categoryCounts.value[c] })
  }
  return chips
})

/** 选了系列时只看系列内的导入游戏(固定入口不属于任何系列) */
const visibleBuiltins = computed(() => (activeSeries.value ? [] : builtinGames.filter((b) => inGameCategory(b, activeCategory.value))))
const visibleGames = computed(() =>
  games.value.filter((g) => inGameCategory(g, activeCategory.value) && inGameSeries(g, activeSeries.value)),
)
const visibleTotal = computed(() => visibleBuiltins.value.length + visibleGames.value.length)

// 切换系列后,原分类在新范围内可能没有游戏,回落到「全部」分类
watch(activeSeries, () => {
  if (activeCategory.value !== 'ALL' && !categoryCounts.value[activeCategory.value]) {
    activeCategory.value = 'ALL'
  }
})

const wishOpen = ref(false)
const wishSource = computed(() => ({ type: 'GAME', title: t('wish.gameSourceTitle') }))

const importVisible = ref(false)
const importing = ref(false)
const importMode = ref('file')
const importForm = reactive({ file: null, url: '', name: '', description: '', category: 'OTHER', series: '' })

const editVisible = ref(false)
const saving = ref(false)
const editGameType = ref('')
const editForm = reactive({ id: null, name: '', description: '', linkUrl: '', category: 'OTHER', series: '' })

const accountVisible = ref(false)
const accountSaving = ref(false)
const accountExists = ref(false)
const accountGame = ref(null)
const accountForm = reactive({ siteUsername: '', sitePassword: '', remark: '' })

const load = async () => {
  loading.value = true
  try {
    const list = (await gameApi.list()) || []
    games.value = list.map((g) => ({ ...g, category: normalizeGameCategory(g.category) }))
    // 选中的分类/系列被删空时回到「全部」,避免停留在空白的筛选结果
    if (activeCategory.value !== 'ALL' && !categoryCounts.value[activeCategory.value]) {
      activeCategory.value = 'ALL'
    }
    if (activeSeries.value && activeSeries.value !== UNSERIES && !seriesOptions.value.includes(activeSeries.value)) {
      activeSeries.value = ''
    }
    if (activeSeries.value === UNSERIES && !unsortedCount.value) {
      activeSeries.value = ''
    }
  } finally {
    loading.value = false
  }
}

/** 自动归类:只处理还没有系列的游戏,已有(手动或先前自动)的系列不会被覆盖 */
const autoClassify = async () => {
  autoSeriesLoading.value = true
  try {
    const res = await gameApi.autoSeries()
    const n = res?.updated || 0
    ElMessage.success(n ? t('games.autoSeriesDone', { n }) : t('games.autoSeriesNone'))
    await load()
  } finally {
    autoSeriesLoading.value = false
  }
}

const typeText = (g) => (g.type === 'LINK' ? t('games.typeLink') : g.type)

const play = (g) => {
  if (g.type === 'LINK') {
    window.open(g.linkUrl, '_blank', 'noopener,noreferrer')
    return
  }
  router.push(`/games/play/${g.id}`)
}

const openImport = () => {
  importMode.value = 'file'
  importForm.file = null
  importForm.url = ''
  importForm.name = ''
  importForm.description = ''
  importForm.category = 'OTHER'
  importForm.series = ''
  importVisible.value = true
}

/** 网址前置粗校验:必须像 http(s) 绝对地址(带 scheme 或至少有域名点分),严格校验交给后端 */
const looksLikeUrl = (url) => /^(https?:\/\/)/i.test(url) || (/^[\w-]+(\.[\w-]+)+/.test(url) && !url.includes(' '))

const onFilePicked = (e) => {
  const f = e.target.files?.[0]
  if (!f) return
  importForm.file = f
  if (!importForm.name.trim()) {
    importForm.name = f.name.replace(/\.(swf|gba)$/i, '')
  }
  e.target.value = ''
}

const submitImport = async () => {
  if (importMode.value === 'link') {
    const url = importForm.url.trim()
    if (!url) {
      ElMessage.warning(t('games.needUrl'))
      return
    }
    if (!looksLikeUrl(url)) {
      ElMessage.warning(t('games.urlInvalid'))
      return
    }
    if (!importForm.name.trim()) {
      ElMessage.warning(t('games.needName'))
      return
    }
    importing.value = true
    try {
      await gameApi.addLink({
        name: importForm.name.trim(),
        url,
        description: importForm.description.trim(),
        category: importForm.category,
        series: normalizeGameSeries(importForm.series),
      })
      ElMessage.success(t('games.linkImported'))
      importVisible.value = false
      load()
    } finally {
      importing.value = false
    }
    return
  }
  if (!importForm.file) {
    ElMessage.warning(t('games.needFile'))
    return
  }
  if (!importForm.name.trim()) {
    ElMessage.warning(t('games.needName'))
    return
  }
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importForm.file)
    fd.append('name', importForm.name.trim())
    fd.append('description', importForm.description.trim())
    fd.append('category', importForm.category)
    fd.append('series', normalizeGameSeries(importForm.series))
    await gameApi.import(fd)
    ElMessage.success(t('games.imported'))
    importVisible.value = false
    load()
  } finally {
    importing.value = false
  }
}

const openEdit = (g) => {
  editForm.id = g.id
  editGameType.value = g.type
  editForm.name = g.name
  editForm.description = g.description || ''
  editForm.linkUrl = g.linkUrl || ''
  editForm.category = normalizeGameCategory(g.category)
  editForm.series = normalizeGameSeries(g.series)
  editVisible.value = true
}

const submitEdit = async () => {
  if (!editForm.name.trim()) {
    ElMessage.warning(t('games.needName'))
    return
  }
  saving.value = true
  try {
    const data = {
      name: editForm.name.trim(),
      description: editForm.description.trim(),
      category: editForm.category,
      series: normalizeGameSeries(editForm.series),
    }
    if (editGameType.value === 'LINK') data.linkUrl = editForm.linkUrl.trim()
    await gameApi.update(editForm.id, data)
    ElMessage.success(t('games.saved'))
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const openAccount = async (g) => {
  accountGame.value = g
  accountForm.siteUsername = ''
  accountForm.sitePassword = ''
  accountForm.remark = ''
  accountExists.value = false
  accountVisible.value = true
  try {
    const acc = await gameApi.getAccount(g.id)
    if (acc && acc.updatedAt) {
      accountExists.value = true
      accountForm.siteUsername = acc.siteUsername || ''
      accountForm.sitePassword = acc.sitePassword || ''
      accountForm.remark = acc.remark || ''
    }
  } catch (e) {
    // 取不到已有账号(首次使用/读取失败)按空表单处理,保存时后端会给出具体错误
  }
}

const submitAccount = async () => {
  if (!accountForm.siteUsername.trim() && !accountForm.sitePassword.trim()) {
    ElMessage.warning(t('games.account.needAny'))
    return
  }
  accountSaving.value = true
  try {
    await gameApi.saveAccount(accountGame.value.id, {
      siteUsername: accountForm.siteUsername.trim(),
      sitePassword: accountForm.sitePassword.trim(),
      remark: accountForm.remark.trim(),
    })
    ElMessage.success(t('games.account.saved'))
    accountVisible.value = false
    load()
  } finally {
    accountSaving.value = false
  }
}

const removeAccount = async () => {
  try {
    await ElMessageBox.confirm(t('games.account.deleteConfirm'), t('games.account.deleteBtn'), { type: 'warning' })
  } catch (e) {
    return
  }
  await gameApi.deleteAccount(accountGame.value.id)
  ElMessage.success(t('games.account.deleted'))
  accountVisible.value = false
  load()
}

const removeGame = async (g) => {
  try {
    await ElMessageBox.confirm(t('games.deleteConfirm', { name: g.name }), t('games.delete'), { type: 'warning' })
  } catch (e) {
    return
  }
  await gameApi.remove(g.id)
  ElMessage.success(t('games.deleted'))
  load()
}

const onGameAction = (cmd, g) => {
  if (cmd === 'edit') openEdit(g)
  else if (cmd === 'account') openAccount(g)
  else if (cmd === 'delete') removeGame(g)
}

onMounted(load)
</script>

<style scoped>
.game-cats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.game-cat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  transition:
    color 0.16s ease,
    border-color 0.16s ease,
    background-color 0.16s ease;
}
.game-cat:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}
.game-cat.active {
  color: var(--color-brand-text);
  background: var(--color-brand);
  border-color: var(--color-brand);
}
.game-cat-count {
  font-size: 11px;
  opacity: 0.75;
}
.game-series-filter {
  width: 150px;
}
.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  min-height: 120px;
}
.tool-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.tool-card:hover {
  transform: translateY(-3px);
}
.tool-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-weak, rgba(184, 140, 110, 0.14));
  color: var(--color-accent, var(--color-brand));
  margin-bottom: 14px;
}
.tool-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}
.tool-desc {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.5;
  min-height: 20px;
}
.tool-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.tool-meta-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
  row-gap: 4px;
}
.tool-account-flag {
  color: var(--color-accent, var(--color-brand));
  font-size: 14px;
}
.tool-type-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-text-secondary, #999);
  font-weight: 500;
}
.tool-type-tag.cat-tag {
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent);
}
.tool-type-tag.series-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  max-width: 100%;
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-enter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-accent, var(--color-brand));
}
.game-more {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 6px;
  color: var(--color-text-secondary, #999);
  cursor: pointer;
}
.game-more:hover {
  color: var(--color-accent, var(--color-brand));
  background: rgba(0, 0, 0, 0.04);
}
.tool-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-secondary, #999);
  font-size: 13px;
}
.game-file-input {
  width: 100%;
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
.import-mode {
  margin-bottom: 16px;
}
.account-hint {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
  margin: 0 0 14px;
}
</style>
