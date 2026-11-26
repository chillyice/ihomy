<template>
  <nav class="directory" :aria-label="$t('settings.allSettings')">
    <section v-for="group in orderedGroups" :key="group.key" :aria-label="$t(group.labelKey)">
      <div class="directory-label" aria-hidden="true">{{ $t(group.labelKey) }}</div>
      <div class="entries" :class="'entries-' + group.key">
        <button
          v-for="panel in group.items"
          :key="panel.id"
          type="button"
          class="entry"
          :class="{ 'family-cover': panel.id === 'family' }"
          @click="$emit('open', panel.id)"
        >
          <template v-if="panel.id === 'family'">
            <span class="cover-copy">
              <span v-if="appStore.familyName" class="cover-name">{{ appStore.familyName }}</span>
              <span class="cover-link" :class="{ 'cover-name': !appStore.familyName }">
                {{ $t(panel.labelKey) }}
                <el-icon aria-hidden="true"><ArrowRight /></el-icon>
              </span>
            </span>
            <span class="cover-art"><SettingsIcon name="family" /></span>
          </template>
          <template v-else>
            <SettingsIcon :name="panel.id" class="entry-icon" />
            <span class="entry-copy">
              <span class="entry-title">{{ $t(panel.labelKey) }}</span>
              <span v-if="summaries[panel.id]" class="entry-summary">{{ summaries[panel.id] }}</span>
            </span>
            <el-icon v-if="group.key !== 'family'" aria-hidden="true" class="entry-arrow"><ArrowRight /></el-icon>
          </template>
        </button>
      </div>
    </section>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import SettingsIcon from './SettingsIcon.vue'

const props = defineProps({ groups: { type: Array, required: true } })
defineEmits(['open'])
const { t } = useI18n()
const appStore = useAppStore()
const themeStore = useThemeStore()
const userStore = useUserStore()
const orderedGroups = computed(() => [...props.groups.filter((g) => g.key === 'family'), ...props.groups.filter((g) => g.key !== 'family')])
const summaries = computed(() => ({
  profile: userStore.userInfo?.nickname,
  health: t('settings.health.privacyTitle'),
  content: t('settings.dailyContent'),
  media: t('settings.mediaMine.title'),
  storage: t('storage.devices'),
  appearance: t('theme.' + themeStore.theme),
}))
</script>

<style scoped>
.directory {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 24px;
}
.directory section {
  min-width: 0;
}
.directory-label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 2px 8px;
  color: var(--color-text);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.045em;
}
.directory-label::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
}
.entries {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background-color: var(--color-card);
}
html.theme-warm .entries {
  background-image: var(--caption-paper);
  background-size: auto, var(--home-grain-size);
}
.entry {
  appearance: none;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 9px 14px;
  text-align: start;
  background: transparent;
  color: var(--color-text);
  border: 0;
  font: inherit;
  cursor: pointer;
}
.entry:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -3px;
}
.entry:active {
  background-color: var(--color-card-2);
}
.entry-copy,
.cover-copy {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 3px;
  overflow-wrap: anywhere;
}
.entry-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}
.entry-summary,
.cover-link {
  color: color-mix(in srgb, var(--color-text-secondary) 76%, var(--color-text));
  font-size: 12px;
  line-height: 1.4;
}
.entry-arrow {
  flex: none;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.entries-family {
  display: flex;
  flex-wrap: wrap;
  box-shadow: var(--shadow);
}
.family-cover {
  position: relative;
  isolation: isolate;
  min-height: 106px;
  padding: 12px clamp(12px, 4.5vw, 22px) 12px clamp(20px, 7vw, 30px);
  background-color: color-mix(in srgb, var(--color-green) 26%, var(--color-card));
}
html.theme-warm .family-cover {
  background-image:
    linear-gradient(90deg, transparent, color-mix(in srgb, var(--color-card) 28%, transparent)),
    url('@/assets/materials/album-bookcloth.webp?no-inline');
  background-size:
    auto,
    110px 110px;
  background-blend-mode: normal, soft-light;
}
.family-cover::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 9px;
  border-inline-end: 1px solid var(--color-border);
  background-color: color-mix(in srgb, var(--color-green) 50%, transparent);
  box-shadow: 2px 0 3px var(--color-border);
}
.cover-copy {
  gap: 6px;
}
.cover-name {
  color: var(--color-text);
  font-size: clamp(20px, 5.9vw, 23px);
  font-weight: 700;
  line-height: 1.25;
}
.cover-link {
  display: flex;
  align-items: center;
  gap: 4px;
}
.cover-art {
  flex: none;
  display: grid;
  place-items: center;
  width: clamp(58px, 20vw, 82px);
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, var(--color-card) 0%, transparent 72%);
}
.cover-art > svg {
  width: 88%;
  height: 88%;
}
.entries-family .entry:not(.family-cover) {
  flex: 1 1 0;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 8px 4px;
  text-align: center;
}
.entries-family .entry-icon {
  width: 28px;
  height: 28px;
}
.entries-family .entry-title {
  font-size: 13px;
}
.entries-account {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
}
.entries-account .entry {
  position: relative;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  padding: 12px 14px;
}
.entries-account .entry-arrow {
  position: absolute;
  inset: 15px 12px auto auto;
}
.entries-account .entry:active,
.family-cover:active {
  background-color: var(--color-card-2);
}
</style>
