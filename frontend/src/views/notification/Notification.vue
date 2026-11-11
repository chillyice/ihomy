<!-- 通知中心:后台未读通知点「打开通知」后落在这里(壳内深链 /notification)。
     桌面端侧栏气泡仍是主入口,这一页让手机/深链也能看全量并标记已读。 -->
<template>
  <div ref="root" class="page">
    <Breadcrumb :items="[{ label: t('nav.notifications') }]" />

    <PageToolbar>
      <div class="tb-left"></div>
      <div class="tb-right">
        <el-button v-if="hasUnread" @click="markAllRead">{{ t('nav.allRead') }}</el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading" class="card notify-page">
      <div v-if="list.length" class="notify-list">
        <SwipeRow
          v-for="n in list"
          :key="n.id"
          :left-actions="n.isRead ? [] : readAction"
          :menu-items="n.isRead ? [] : readAction"
          :disabled="!!n.isRead"
          @action="onRowAction(n)"
        >
          <template #default="{ openMenu }">
            <div v-a11y-click class="notify-item" :class="{ unread: !n.isRead }" @click="onClick(n)">
              <div class="notify-main">
                <div class="notify-type">{{ typeText(n.type) }}</div>
                <div class="notify-content">{{ n.content }}</div>
                <div class="notify-time">{{ formatRelativeTime(t, n.createdAt) }}</div>
              </div>
              <!-- 长按菜单的等价按钮(D4):已读通知没有可执行项,不显示 -->
              <span v-if="!n.isRead" class="notify-actions" @click.stop>
                <el-tooltip :content="t('common.more')" placement="top" :show-after="300">
                  <el-button size="small" text :aria-label="t('common.more')" @click.stop="openMenu($event)"
                    ><el-icon><MoreFilled /></el-icon
                  ></el-button>
                </el-tooltip>
              </span>
            </div>
          </template>
        </SwipeRow>
      </div>
      <el-empty v-else :description="t('nav.noNotification')" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import SwipeRow from '@/components/ui/SwipeRow.vue'
import { notificationApi } from '@/api'
import { formatRelativeTime } from '@/utils/datetime'
import { todayString } from '@/utils/scheduleCalendar'
import { usePullRefresh } from '@/composables/usePullRefresh'

const { t } = useI18n()
const router = useRouter()
const root = ref(null)
const loading = ref(false)
const list = ref([])

const hasUnread = computed(() => list.value.some((n) => !n.isRead))
const readAction = computed(() => [{ key: 'read', label: t('common.markRead') }])

async function load() {
  loading.value = true
  try {
    list.value = await notificationApi.list()
  } catch (e) {
    list.value = []
    throw e
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  try {
    await notificationApi.markAllRead()
    list.value = list.value.map((n) => ({ ...n, isRead: 1 }))
  } catch (e) {
    /* 失败保持原样,用户可重试 */
  }
}

function typeText(type) {
  if (type === 'reply') return t('notify.typeReply')
  if (type === 'system') return t('notify.typeSystem')
  if (type === 'item_expiry') return t('notify.typeExpiry')
  if (type === 'reminder') return t('notify.typeReminder')
  if (type === 'anniversary') return t('notify.typeAnniversary')
  return t('notify.typeComment')
}

// 标为已读:手势/菜单用,失败给提示但不打断后续跳转;行点击复用同一入口
async function markRead(n) {
  if (!n || n.isRead) return
  try {
    await notificationApi.markRead(n.id)
    n.isRead = 1
  } catch (e) {
    ElMessage.error(t('common.failed'))
  }
}

function onRowAction(n) {
  markRead(n)
}

async function onClick(n) {
  if (!n.isRead) {
    try {
      await notificationApi.markRead(n.id)
      n.isRead = 1
    } catch (e) {
      /* 已读失败不挡跳转 */
    }
  }
  if (n.contentType === 'blog') router.push(`/blog/${n.contentId}`)
  else if (n.contentType === 'diary') router.push('/diary')
  else if (n.contentType === 'photo' && n.contentId) router.push('/album')
  else if (n.contentType === 'item') router.push('/kitchen/ingredients')
  else if (n.contentType === 'reminder' || n.contentType === 'anniversary') {
    const day = n.createdAt ? String(n.createdAt).slice(0, 10) : todayString()
    router.push({ path: '/plan', query: { date: day } })
  }
}

usePullRefresh({ target: root, onRefresh: load })

onMounted(() => load().catch(() => {}))
</script>

<style scoped>
.notify-page {
  padding: 8px 16px;
}
.notify-list {
  display: flex;
  flex-direction: column;
}
.notify-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}
.notify-main {
  flex: 1;
  min-width: 0;
}
.notify-actions {
  flex-shrink: 0;
}
.notify-actions :deep(.el-button) {
  padding: 4px 5px;
}
.notify-item:last-child {
  border-bottom: none;
}
.notify-item.unread .notify-content {
  font-weight: 600;
}
.notify-type {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
.notify-content {
  margin: 4px 0;
  line-height: 1.5;
}
.notify-time {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
</style>
