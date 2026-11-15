<template>
  <!-- 房间邀请:成员走私聊、家庭走对方聊天流;本家/亲友圈可多选 -->
  <el-dialog v-model="visible" :title="$t('games.room.inviteTitle')" width="520px" append-to-body class="dialog-lg">
    <div v-loading="loading" class="invite-picker">
      <p class="picker-hint">{{ $t('games.room.inviteHint') }}</p>

      <section class="picker-group">
        <div class="group-label">{{ $t('games.room.inviteFamilies') }}</div>
        <el-checkbox-group v-model="pickedFamilies" class="family-checks">
          <el-checkbox :value="myFamilyId">{{ $t('games.room.inviteOwnFamily') }}</el-checkbox>
          <template v-for="c in circles" :key="c.id">
            <el-checkbox v-for="f in circleFamilies(c)" :key="f.familyId" :value="f.familyId">
              {{ f.alias || f.familyName }}
              <span class="family-circle-name">{{ c.name }}</span>
            </el-checkbox>
          </template>
        </el-checkbox-group>
        <div v-if="!hasCircleFamilies" class="group-empty">{{ $t('games.room.inviteNoCircle') }}</div>
      </section>

      <section class="picker-group">
        <div class="group-label">{{ $t('games.room.inviteMembers') }}</div>
        <el-checkbox-group v-model="pickedMembers" class="member-checks">
          <el-checkbox v-for="m in ownMembers" :key="m.id" :value="m.id">
            <span class="member-name">{{ m.nickname || m.username }}</span>
            <span v-if="m.id === myId" class="member-me">{{ $t('games.room.me') }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="!ownMembers.length" class="group-empty">{{ $t('games.room.inviteNoMembers') }}</div>

        <el-collapse v-if="hasCircleFamilies" v-model="expandedCircles" class="circle-collapse">
          <el-collapse-item v-for="c in circles" :key="c.id" :name="c.id">
            <template #title>
              <span class="circle-collapse-title">{{ c.name }}</span>
            </template>
            <div v-for="f in circleFamilies(c)" :key="f.familyId" class="circle-family">
              <div class="circle-family-name">{{ f.alias || f.familyName }}</div>
              <div v-if="familyMembersMap[f.familyId]" class="member-checks">
                <el-checkbox-group v-model="pickedMembers">
                  <el-checkbox v-for="m in familyMembersMap[f.familyId]" :key="m.id" :value="m.id">
                    {{ m.name }}
                  </el-checkbox>
                </el-checkbox-group>
                <div v-if="!familyMembersMap[f.familyId].length" class="group-empty">{{ $t('games.room.inviteNoMembers') }}</div>
              </div>
              <div v-else v-loading="true" class="circle-family-loading" />
            </div>
          </el-collapse-item>
        </el-collapse>
      </section>
    </div>
    <template #footer>
      <span class="picker-count">{{ $t('games.room.inviteCount', { n: pickedMembers.length + pickedFamilies.length }) }}</span>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="sending" :disabled="!pickedMembers.length && !pickedFamilies.length" @click="send">
        {{ $t('games.room.inviteSend') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 邀请对象目录:本家成员(memberApi)+ 亲友圈家庭及其成员(circleApi,同圈可见名单);
// 圈内家庭成员按折叠面板懒加载(一个家庭一次请求,不预拉)。
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { circleApi, memberApi, roomApi } from '@/api'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  roomId: { type: [Number, String], default: null },
})
const emit = defineEmits(['sent'])

const { t } = useI18n()
const userStore = useUserStore()

const visible = defineModel('visible', { type: Boolean, default: false })
const loading = ref(false)
const sending = ref(false)
const ownMembers = ref([])
const circles = ref([])
const familyMembersMap = reactive({})
const expandedCircles = ref([])
const pickedMembers = ref([])
const pickedFamilies = ref([])

const myId = computed(() => userStore.userInfo?.id)
const myFamilyId = computed(() => userStore.userInfo?.familyId)
const circleFamilies = (c) => (c.families || []).filter((f) => !f.self)
const hasCircleFamilies = computed(() => circles.value.some((c) => circleFamilies(c).length))

const load = async () => {
  loading.value = true
  pickedMembers.value = []
  pickedFamilies.value = []
  try {
    const [members, circleList] = await Promise.all([memberApi.list(), circleApi.list()])
    ownMembers.value = (members || []).filter((m) => m.id !== myId.value)
    circles.value = circleList || []
  } catch {
    ownMembers.value = []
    circles.value = []
  } finally {
    loading.value = false
  }
}

// 展开某个圈时补拉圈内各家庭的成员名单(只拉一次)
watch(expandedCircles, async (names) => {
  for (const name of names || []) {
    const circle = circles.value.find((c) => c.id === name)
    for (const f of circle ? circleFamilies(circle) : []) {
      if (familyMembersMap[f.familyId]) continue
      try {
        familyMembersMap[f.familyId] = (await circleApi.familyMembers(f.familyId)) || []
      } catch {
        familyMembersMap[f.familyId] = []
      }
    }
  }
})

const send = async () => {
  if (!props.roomId || sending.value) return
  sending.value = true
  try {
    const res = await roomApi.invite(props.roomId, pickedMembers.value, pickedFamilies.value)
    ElMessage.success(t('games.room.inviteSentCount', { n: (res?.dmSent || 0) + (res?.familySent || 0) }))
    visible.value = false
    emit('sent', res)
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.inviteFailed'))
  } finally {
    sending.value = false
  }
}

watch(visible, (v) => {
  if (v) load()
})
</script>

<style scoped>
.invite-picker {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.picker-hint {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.picker-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.group-label {
  font-size: 13px;
  font-weight: 600;
}
.group-empty {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.family-checks,
.member-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}
.family-circle-name {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.member-name {
  margin-right: 4px;
}
.member-me {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.circle-collapse {
  border-top: 1px dashed var(--color-border);
}
.circle-collapse-title {
  font-size: 13px;
}
.circle-family {
  padding: 4px 0 8px;
}
.circle-family + .circle-family {
  border-top: 1px dashed var(--color-border);
}
.circle-family-name {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.circle-family-loading {
  height: 32px;
}
.picker-count {
  margin-right: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
