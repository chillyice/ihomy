<template>
  <div class="bg-playlist-item" :class="{ active: p.isBackground }">
    <img v-if="p.coverUrl" :src="p.coverUrl" class="bg-pl-cover" alt="" />
    <div v-else class="bg-pl-cover placeholder">🎼</div>
    <div class="bg-pl-info">
      <div class="bg-pl-name">{{ p.name }}</div>
      <div class="bg-pl-count">{{ $t('settings.bgmTrackCount', { n: p.trackCount || 0 }) }}</div>
    </div>
    <div v-if="p.isBackground" class="bg-pl-tag">{{ $t('settings.bgmCurrent') }}</div>
    <el-button v-else size="small" type="primary" @click="$emit('set', p)">{{ $t('settings.bgmSet') }}</el-button>
  </div>
</template>

<script setup>
defineProps({ p: { type: Object, required: true } })
defineEmits(['set'])
</script>

<style scoped>
.bg-playlist-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--color-card-2);
  border-radius: 10px;
  transition: background 0.15s;
}
.bg-playlist-item.active {
  background: rgba(168, 72, 58, 0.08);
  border: 1px solid rgba(168, 72, 58, 0.2);
}
.bg-pl-cover {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.bg-pl-cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(58, 46, 34, 0.06);
  font-size: 18px;
}
.bg-pl-info {
  flex: 1;
  min-width: 0;
}
.bg-pl-name {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bg-pl-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.bg-pl-tag {
  font-size: 12px;
  color: var(--color-accent);
  font-weight: 500;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .bg-playlist-item {
    flex-wrap: wrap;
  }
  .bg-pl-tag {
    margin-left: 52px;
  }
}
</style>
