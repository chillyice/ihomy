<template>
  <!-- 聊天对象列:家庭聊天室 + 本家成员 + 亲友圈(圈内家庭→成员);点成员开私聊 -->
  <aside class="chat-rail">
    <button type="button" class="rail-item rail-family" :class="{ active: chatStore.activePeer == null }" @click="chatStore.closeDm()">
      <el-icon :size="16"><HomeFilled /></el-icon>
      <span class="rail-name">{{ $t('chat.rail.family') }}</span>
      <span v-if="chatStore.unread" class="rail-badge">{{ chatStore.unread > 99 ? '99+' : chatStore.unread }}</span>
    </button>

    <div class="rail-group-label">{{ $t('chat.rail.members') }}</div>
    <div v-loading="loading" class="rail-scroll">
      <button
        v-for="m in ownMembers"
        :key="'own' + m.id"
        type="button"
        class="rail-item"
        :class="{ active: isActivePeer(m.id) }"
        @click="openDm(m.id, memberName(m))"
      >
        <img v-if="m.avatar" class="rail-avatar" :src="m.avatar" :alt="memberName(m)" />
        <span v-else class="rail-avatar rail-avatar-text">{{ memberName(m).charAt(0) }}</span>
        <span class="rail-name">{{ memberName(m) }}</span>
        <span v-if="peerUnread(m.id)" class="rail-badge">{{ peerUnread(m.id) > 99 ? '99+' : peerUnread(m.id) }}</span>
      </button>
      <div v-if="!loading && !ownMembers.length" class="rail-empty">{{ $t('chat.rail.noMembers') }}</div>

      <template v-for="c in circles" :key="c.id">
        <div class="rail-group-label">{{ c.name }}</div>
        <template v-for="f in circleFamilies(c)" :key="f.familyId">
          <button
            v-for="m in familyMembers(f.familyId)"
            :key="'c' + f.familyId + '-' + m.id"
            type="button"
            class="rail-item"
            :class="{ active: isActivePeer(m.id) }"
            :title="f.alias || f.familyName"
            @click="openDm(m.id, m.name)"
          >
            <img v-if="m.avatar" class="rail-avatar" :src="m.avatar" :alt="m.name" />
            <span v-else class="rail-avatar rail-avatar-text">{{ (m.name || '?').charAt(0) }}</span>
            <span class="rail-name">{{ m.name }}</span>
            <span class="rail-family-tag">{{ f.alias || f.familyName }}</span>
            <span v-if="peerUnread(m.id)" class="rail-badge">{{ peerUnread(m.id) > 99 ? '99+' : peerUnread(m.id) }}</span>
          </button>
          <div v-if="familyLoading(f.familyId)" v-loading="true" class="rail-family-loading" />
          <div v-else-if="familyLoaded(f.familyId) && !familyMembers(f.familyId).length" class="rail-empty">
            {{ $t('chat.rail.noMembers') }}
          </div>
        </template>
      </template>
    </div>
  </aside>
</template>

<script setup>
// 联系人数据:本家成员一次拉;圈内家庭挂出后懒加载各自成员(同圈可见名单口径)。
// 未读数读 store(dmUnread),WS 实时增量已由 store 维护。
import { computed, onMounted, ref } from 'vue'
import { HomeFilled } from '@element-plus/icons-vue'
import { circleApi, memberApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'

const chatStore = useChatStore()
const userStore = useUserStore()

const loading = ref(false)
const ownMembers = ref([])
const circles = ref([])
const familyMembersMap = ref({})

const myId = computed(() => userStore.userInfo?.id)
const circleFamilies = (c) => (c.families || []).filter((f) => !f.self)
const memberName = (m) => m.nickname || m.username || '?'
const familyMembers = (familyId) => familyMembersMap.value[familyId] || []
const familyLoaded = (familyId) => familyMembersMap.value[familyId] != null
const familyLoading = (familyId) => familyMembersMap.value[familyId] === 'loading'
const isActivePeer = (id) => chatStore.activePeer != null && Number(chatStore.activePeer) === Number(id)
const peerUnread = (id) => chatStore.dmUnread[String(id)] || 0

const openDm = async (id, name) => {
  await chatStore.openDm(id, name)
}

// 圈内家庭成员:渲染前按需补拉(一次一个家庭,拉过就不再拉)
const ensureCircleMembers = async () => {
  for (const c of circles.value) {
    for (const f of circleFamilies(c)) {
      if (familyLoaded(f.familyId) || familyLoading(f.familyId)) continue
      familyMembersMap.value[f.familyId] = 'loading'
      try {
        familyMembersMap.value[f.familyId] = (await circleApi.familyMembers(f.familyId)) || []
      } catch {
        familyMembersMap.value[f.familyId] = []
      }
    }
  }
}

const load = async () => {
  loading.value = true
  try {
    const [members, circleList] = await Promise.allSettled([memberApi.list(), circleApi.list()])
    ownMembers.value = (members.status === 'fulfilled' ? members.value : []).filter((m) => m.id !== myId.value) || []
    circles.value = circleList.status === 'fulfilled' ? circleList.value || [] : []
    ensureCircleMembers()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.chat-rail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 168px;
  flex: none;
  min-height: 0;
  border-right: 1px solid var(--color-border);
  padding-right: 8px;
  margin-right: 2px;
}
.rail-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 4px;
}
.rail-group-label {
  margin: 8px 2px 2px;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.rail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-soft);
}
.rail-item:hover {
  background: rgba(var(--color-brand-rgb), 0.08);
}
.rail-item.active {
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent);
  font-weight: 600;
}
.rail-family {
  font-weight: 600;
}
.rail-avatar {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.rail-avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-brand-text);
  background: rgba(var(--color-brand-rgb), 0.16);
}
.rail-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rail-family-tag {
  flex: none;
  max-width: 52px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.rail-badge {
  flex: none;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: var(--el-color-danger, #f56c6c);
}
.rail-empty {
  padding: 6px 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.rail-family-loading {
  height: 28px;
}
@media (max-width: 768px) {
  .chat-rail {
    width: 132px;
  }
  .rail-family-tag {
    display: none;
  }
}
</style>
