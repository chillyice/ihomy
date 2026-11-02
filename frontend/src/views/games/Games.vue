<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('games.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('games.title') }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" round @click="wishOpen = true">{{ $t('wish.gameWish') }}</el-button>
        <el-button type="primary" size="small" round @click="openImport">
          <el-icon><Plus /></el-icon>
          {{ $t('games.import') }}
        </el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading" class="tool-grid">
      <!-- 花园固定入口(植物养殖改名而来) -->
      <div v-a11y-click class="tool-card card" @click="$router.push('/tools/plant')">
        <div class="tool-icon">
          <el-icon :size="30"><Sunny /></el-icon>
        </div>
        <div class="tool-name">{{ $t('games.garden.title') }}</div>
        <div class="tool-desc">{{ $t('games.garden.desc') }}</div>
        <span class="tool-enter"
          >{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon
        ></span>
      </div>

      <!-- 宠物连连看固定入口(H5 原生小游戏,通关发积分) -->
      <div v-a11y-click class="tool-card card" @click="$router.push('/games/petlink')">
        <div class="tool-icon">
          <el-icon :size="30"><Grid /></el-icon>
        </div>
        <div class="tool-name">{{ $t('games.petlink.title') }}</div>
        <div class="tool-desc">{{ $t('games.petlink.desc') }}</div>
        <span class="tool-enter"
          >{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon
        ></span>
      </div>

      <!-- 数独固定入口(H5 原生小游戏,通关发积分) -->
      <div v-a11y-click class="tool-card card" @click="$router.push('/games/sudoku')">
        <div class="tool-icon">
          <el-icon :size="30"><Cpu /></el-icon>
        </div>
        <div class="tool-name">{{ $t('games.sudoku.title') }}</div>
        <div class="tool-desc">{{ $t('games.sudoku.desc') }}</div>
        <span class="tool-enter"
          >{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon
        ></span>
      </div>

      <!-- 2048 固定入口(H5 原生小游戏,达成 2048 发积分) -->
      <div v-a11y-click class="tool-card card" @click="$router.push('/games/2048')">
        <div class="tool-icon">
          <el-icon :size="30"><Histogram /></el-icon>
        </div>
        <div class="tool-name">{{ $t('games.g2048.title') }}</div>
        <div class="tool-desc">{{ $t('games.g2048.desc') }}</div>
        <span class="tool-enter"
          >{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon
        ></span>
      </div>

      <!-- 导入的小游戏(LINK 为外部网页游戏,点卡片新标签页打开) -->
      <div v-a11y-click v-for="g in games" :key="g.id" class="tool-card card" @click="play(g)">
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

      <div v-if="!loading && games.length === 0" class="tool-empty">{{ $t('games.empty') }}</div>
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
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Sunny, VideoPlay, ArrowRight, MoreFilled, Grid, Cpu, Histogram, Link, Key } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import AddToWishDialog from '@/components/wish/AddToWishDialog.vue'
import { gameApi } from '@/api'

const router = useRouter()
const { t } = useI18n()

const games = ref([])
const loading = ref(false)

const wishOpen = ref(false)
const wishSource = computed(() => ({ type: 'GAME', title: t('wish.gameSourceTitle') }))

const importVisible = ref(false)
const importing = ref(false)
const importMode = ref('file')
const importForm = reactive({ file: null, url: '', name: '', description: '' })

const editVisible = ref(false)
const saving = ref(false)
const editGameType = ref('')
const editForm = reactive({ id: null, name: '', description: '', linkUrl: '' })

const accountVisible = ref(false)
const accountSaving = ref(false)
const accountExists = ref(false)
const accountGame = ref(null)
const accountForm = reactive({ siteUsername: '', sitePassword: '', remark: '' })

const load = async () => {
  loading.value = true
  try {
    games.value = (await gameApi.list()) || []
  } finally {
    loading.value = false
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
      await gameApi.addLink({ name: importForm.name.trim(), url, description: importForm.description.trim() })
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
  editVisible.value = true
}

const submitEdit = async () => {
  if (!editForm.name.trim()) {
    ElMessage.warning(t('games.needName'))
    return
  }
  saving.value = true
  try {
    const data = { name: editForm.name.trim(), description: editForm.description.trim() }
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
