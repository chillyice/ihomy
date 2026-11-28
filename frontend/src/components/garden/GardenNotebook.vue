<template>
  <div class="book-shade">
    <section class="book" :aria-label="t('garden.book.title')">
      <header>
        <h2 id="garden-book-title">{{ t('garden.book.title') }}</h2>
        <button ref="close" class="fold" @click="emit('back')">{{ t('garden.book.close') }}</button>
      </header>
      <nav :aria-label="t('garden.book.locations')">
        <button v-for="tab in tabs" :key="tab" :aria-pressed="tab === page" @click="page = tab">{{ t('garden.book.' + tab) }}</button>
      </nav>
      <div class="entries">
        <section v-for="group in groups" :key="group.key">
          <h3 v-if="page === 'family'">{{ t('garden.book.ownerSill', { name: group.name }) }}</h3>
          <button v-for="p in group.plots" :key="p.id" :data-plot="p.id" class="entry" :disabled="busy" @click="emit('visit', p.id)">
            <span
              class="sketch"
              :class="{ field: page === 'yard', growing: p.cropCode && p.stage > 0, full: p.stage >= 3, dry: p.thirsty }"
              aria-hidden="true"
            >
              <i></i><i></i><i></i><b></b>
            </span>
            <span class="caption"
              ><strong>{{ p.cropName || (p.cropCode ? p.cropCode : t('garden.book.empty')) }}</strong>
              <small>{{ slot(p) }} · {{ status(p) }}</small></span
            >
            <span class="arrow" aria-hidden="true">›</span>
          </button>
        </section>
        <p v-if="!groups.length" class="empty">{{ t(page === 'own' ? 'garden.noPot' : 'garden.book.noFamily') }}</p>
      </div>
      <details v-if="Number.isFinite(world.inventoryCap)" class="stores">
        <summary>
          {{ t('garden.book.store') }} <small>{{ world.inventoryUsed }} / {{ world.inventoryCap }}</small>
        </summary>
        <p v-if="!items.length">{{ t('garden.book.storeEmpty') }}</p>
        <p v-for="item in items" :key="item.itemCode">{{ item.name }} ×{{ item.qty }}</p>
      </details>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
const props = defineProps({ world: { type: Object, required: true }, busy: Boolean })
const emit = defineEmits(['visit', 'back'])
const { t } = useI18n()
const page = ref('own')
const close = ref(null)
const tabs = computed(() =>
  props.world.plots.some((p) => p.venue === 'BACKYARD' && p.ownerUid === '') ? ['own', 'family', 'yard'] : ['own', 'family'],
)
watch(tabs, (available) => {
  if (!available.includes(page.value)) page.value = 'own'
})
const groups = computed(() => {
  const byOwner = new Map()
  for (const p of props.world.plots) {
    const tab =
      p.venue === 'BACKYARD' && p.ownerUid === '' ? 'yard' : p.venue === 'WINDOWSILL' ? (p.mine === true ? 'own' : 'family') : null
    if (tab !== page.value) continue
    const key = p.ownerUid || tab
    if (!byOwner.has(key)) byOwner.set(key, { key, name: p.ownerName || t('garden.book.member'), plots: [] })
    byOwner.get(key).plots.push(p)
  }
  return [...byOwner.values()].map((group) => ({ ...group, plots: group.plots.sort((a, b) => a.slotNo - b.slotNo) }))
})
const items = computed(() =>
  [...(props.world.seeds || []), ...(props.world.fruits || [])]
    .filter((s) => s.qty > 0)
    .map((s) => {
      const crop = props.world.unlockedCrops?.find((c) => c.code === s.cropCode)?.name || s.cropCode || s.itemCode
      return { ...s, name: s.itemCode.startsWith('SEED_') ? t('garden.seedPacket', { crop }) : crop }
    }),
)
function slot(p) {
  return t(p.venue === 'BACKYARD' ? 'garden.book.bedNo' : 'garden.book.potNo', { n: p.slotNo + 1 })
}
function status(p) {
  if (p.harvestable) return t('garden.book.ripe')
  if (p.thirsty) return t('garden.book.dry')
  if (p.cropCode) return t('garden.book.stageNames.' + (p.stage ?? 0))
  return t(p.prepared || p.venue === 'BACKYARD' ? 'garden.book.soil' : 'garden.book.unprepared')
}
async function focus(id) {
  await nextTick()
  const buttons = close.value?.closest('section.book')?.querySelectorAll('.entry')
  const previous = Array.from(buttons || []).find((b) => b.dataset.plot === String(id) && !b.disabled)
  ;(previous || close.value)?.focus({ preventScroll: true })
}
defineExpose({ focus })
</script>

<style scoped>
.book-shade {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 16px;
  background: #39352664;
}
.book {
  width: min(100%, 465px);
  max-height: min(84dvh, 660px);
  display: flex;
  flex-direction: column;
  padding: 20px 22px 16px 29px;
  box-sizing: border-box;
  background: linear-gradient(90deg, #b4ac87 0 5px, #d5cbab 5px 8px, #f9f3df 9px);
  color: #453c2b;
  border: 1px solid #a49b7b;
  border-radius: 4px 12px 12px 4px;
  box-shadow:
    2px 3px #d1c6a7,
    4px 6px #79836b,
    0 12px 30px #352b1d44;
}
header,
nav,
.entry,
summary {
  display: flex;
  align-items: center;
  gap: 10px;
}
header {
  justify-content: space-between;
}
h2 {
  margin: 0;
  font-size: 23px;
  line-height: 1.3;
}
button {
  font: inherit;
  color: inherit;
  cursor: pointer;
  min-height: 44px;
}
.fold {
  border: 0;
  color: #5c6247;
  background: transparent;
  text-decoration: underline;
  text-underline-offset: 4px;
}
nav {
  gap: 3px;
  margin-top: 10px;
  border-bottom: 2px solid #758264;
}
nav button {
  flex: 1;
  padding: 6px;
  border: 0;
  border-radius: 6px 6px 0 0;
  background: #e8e1c9;
  font-size: 14px;
}
nav button[aria-pressed='true'] {
  background: #536c54;
  color: #fffae9;
}
.entries {
  min-height: 55px;
  overflow: auto;
  overscroll-behavior: contain;
  padding: 4px 0;
}
h3 {
  margin: 15px 0 2px;
  font-size: 14px;
  color: #626346;
}
.entry {
  width: 100%;
  text-align: start;
  padding: 9px 2px;
  border: 0;
  border-bottom: 1px solid #cfc7ad;
  background: transparent;
}
.entry:hover,
.entry:focus-visible {
  background: #ecebd6;
}
.caption {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.caption strong,
.caption small {
  display: block;
}
.caption small,
.stores small {
  color: #676350;
  font-size: 13px;
}
.arrow {
  color: #697456;
  font-size: 24px;
}
.sketch {
  position: relative;
  flex: 0 0 45px;
  height: 39px;
}
.sketch b {
  position: absolute;
  left: 9px;
  bottom: 2px;
  width: 27px;
  height: 13px;
  background: #bd8569;
  border-top: 4px solid #805d44;
  border-radius: 3px 3px 10px 10px;
  transform: rotate(-3deg);
}
.sketch.field b {
  left: 3px;
  width: 38px;
  height: 8px;
  border-radius: 55%;
  background: #986c49;
}
.sketch.growing i {
  position: absolute;
  left: 20px;
  bottom: 15px;
  width: 7px;
  height: 17px;
  background: #869761;
  border-radius: 100% 0 90% 0;
  transform: rotate(-23deg);
  transform-origin: bottom;
}
.sketch i:nth-child(2) {
  height: 12px;
  transform: rotate(-56deg);
}
.sketch i:nth-child(3) {
  height: 14px;
  transform: rotate(32deg);
}
.sketch.full i {
  width: 9px;
  height: 22px;
}
.sketch.field i {
  bottom: 9px;
}
.sketch.dry i {
  scale: 1 0.73;
  background: #9d9c62;
}
.stores {
  overflow: auto;
  flex-shrink: 0;
  max-height: 30dvh;
  border-top: 1px solid #afa98f;
  font-size: 14px;
}
summary {
  min-height: 44px;
  cursor: pointer;
  justify-content: space-between;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.empty,
.stores p {
  font-size: 14px;
  margin: 8px 0;
}
@media (max-height: 490px) {
  .book {
    max-height: 96dvh;
    padding-block: 6px;
  }
  nav {
    margin-top: 0;
  }
}
</style>
