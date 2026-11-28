<template>
  <div class="guide-shade">
    <section class="guide" aria-labelledby="garden-guide-title">
      <header>
        <h2 id="garden-guide-title">{{ t('garden.guide.title') }}</h2>
        <button ref="back" class="return" @click="$emit('back')">{{ t(fromPot ? 'garden.guide.backPot' : 'garden.book.back') }}</button>
      </header>
      <div class="spread">
        <div class="index">
          <div class="groups" role="group" :aria-label="t('garden.guide.title')">
            <button v-for="(_, key) in groups" :key="key" :aria-pressed="group === key" @click="group = key">
              {{ t('garden.guide.' + key) }}
            </button>
          </div>
          <div class="samples" role="group" :aria-label="t('garden.guide.' + group)">
            <button v-for="s in groups[group]" :key="s.id" :aria-pressed="sample.id === s.id" @click="picked[group] = s.id">
              <i class="swatch" :style="tile(s)" aria-hidden="true"></i>
              <span>{{ t('garden.guide.entries.' + s.id + '.title') }}</span>
            </button>
          </div>
        </div>
        <div class="page-leaf">
          <figure>
            <div class="surface">
              <div class="mount" :style="point">
                <i class="plate" :class="{ reference, enlarged }" :style="tile(sample)" aria-hidden="true"></i>
                <button v-if="!enlarged" class="lens" :aria-label="t('garden.guide.detail', { part })" @click="setZoom(true, true)">
                  <span aria-hidden="true">+</span>
                </button>
              </div>
            </div>
            <figcaption aria-live="polite" aria-atomic="true">
              <h3>{{ t(reference ? 'garden.guide.ordinary' : 'garden.guide.entries.' + sample.id + '.title') }}</h3>
              <p>{{ t(reference ? 'garden.guide.ordinaryNote' : 'garden.guide.entries.' + sample.id + '.note') }}</p>
            </figcaption>
          </figure>
          <div class="view-tools">
            <button ref="zoomButton" :aria-pressed="enlarged" @click="setZoom(!enlarged)">
              {{ enlarged ? t('garden.guide.whole') : t('garden.guide.detail', { part }) }}
            </button>
            <button :aria-pressed="reference" @click="reference = !reference">
              {{ t(reference ? 'garden.guide.backSample' : 'garden.guide.compare') }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({ fromPot: Boolean, dry: Boolean })
defineEmits(['back'])
const { t } = useI18n()
// Coordinates mark the visible feature on each registered 500 × 500 specimen.
const groups = {
  conditions: [
    { id: 'aphids', cell: 4, x: 61, y: 52, part: 'blade' },
    { id: 'dry', cell: 0, x: 77, y: 68, part: 'tip' },
    { id: 'long', cell: 1, x: 64, y: 32, part: 'tip' },
    { id: 'scorch', cell: 2, x: 66, y: 34, part: 'tip' },
    { id: 'wet', cell: 3, x: 51, y: 68, part: 'base' },
    { id: 'perfect', cell: 5, x: 51, y: 38, part: 'tip' },
  ],
  variants: [
    { id: 'whiteEdge', cell: 6, x: 41, y: 29, part: 'edge' },
    { id: 'mottled', cell: 7, x: 56, y: 28, part: 'blade' },
    { id: 'silver', cell: 8, x: 52, y: 37, part: 'edge' },
  ],
}
const group = ref('conditions')
const picked = reactive({ conditions: props.dry ? 'dry' : 'aphids', variants: 'whiteEdge' })
const sample = computed(() => groups[group.value].find((s) => s.id === picked[group.value]))
const part = computed(() => t('garden.guide.parts.' + sample.value.part))
const point = computed(() => ({ '--point-x': sample.value.x + '%', '--point-y': sample.value.y + '%' }))
function tile(s) {
  return { '--tile-x': (s.cell % 3) * 50 + '%', '--tile-y': Math.floor(s.cell / 3) * 50 + '%' }
}
const reference = ref(false)
const enlarged = ref(false)
const back = ref(null)
const zoomButton = ref(null)
function setZoom(value, fromLens = false) {
  enlarged.value = value
  if (fromLens) nextTick(() => zoomButton.value?.focus({ preventScroll: true }))
}
watch(sample, () => {
  reference.value = false
  enlarged.value = false
})
onMounted(() => back.value?.focus({ preventScroll: true }))
</script>

<style scoped>
.guide-shade {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: max(12px, env(safe-area-inset-top)) 12px max(12px, env(safe-area-inset-bottom));
  background: #39352664;
}
.guide {
  --point-x: 50%;
  --point-y: 50%;
  --tile-x: 0%;
  --tile-y: 0%;
  width: min(740px, 100%);
  max-height: 100%;
  overflow: auto;
  box-sizing: border-box;
  background: #faf4e3;
  color: #423d2e;
  border: 1px solid #aa9f80;
  border-inline-start: 6px solid #7a856d;
  border-radius: 4px 12px 12px 4px;
  box-shadow:
    2px 3px #d4c8aa,
    3px 5px #889077,
    0 10px 28px #42341d40;
}
header,
.view-tools,
.groups {
  display: flex;
  align-items: center;
  gap: 8px;
}
header {
  justify-content: space-between;
  padding: 8px 18px;
  border-bottom: 1px solid #c8bfa3;
}
h2,
h3,
p,
figure {
  margin: 0;
}
h2 {
  font-size: 21px;
  color: inherit;
}
button {
  font: inherit;
  min-height: 44px;
  cursor: pointer;
  color: inherit;
}
button:focus-visible {
  outline: 3px solid #4d664d;
  outline-offset: -3px;
}
.return,
.groups button,
.samples button,
.view-tools button {
  border: 0;
  background: transparent;
}
.return {
  color: #526044;
  text-decoration: underline;
  text-underline-offset: 4px;
}
.spread {
  display: grid;
  grid-template-columns: minmax(170px, 0.7fr) minmax(0, 1.65fr);
}
.index {
  padding: 12px;
  background: #eee9d7;
  border-inline-end: 1px solid #c8bfa3;
}
.groups {
  gap: 0;
  border-bottom: 1px solid #bcb59a;
}
.groups button {
  flex: 1;
  padding: 2px 4px;
  font-size: 14px;
}
.groups [aria-pressed='true'] {
  color: #3b563b;
  box-shadow: inset 0 -3px #61764f;
  font-weight: 650;
}
.samples {
  display: grid;
  gap: 3px;
  margin-top: 8px;
}
.samples button {
  display: flex;
  align-items: center;
  text-align: start;
  gap: 7px;
  padding: 3px 6px;
  border-inline-start: 3px solid transparent;
  border-radius: 2px;
  line-height: 1.25;
  font-size: 14px;
}
.samples [aria-pressed='true'] {
  background: #fdf8e9;
  border-color: #647b55;
  font-weight: 650;
}
.swatch,
.plate {
  display: block;
  background: url('@/assets/garden/catgrass-conditions.webp') var(--tile-x) var(--tile-y) / 300% 300% no-repeat;
}
.swatch {
  width: 38px;
  aspect-ratio: 1;
  flex: none;
}
.page-leaf {
  min-width: 0;
}
.surface {
  display: grid;
  place-items: center;
  overflow: hidden;
  background: radial-gradient(ellipse at 55% 38%, #f5f1df, #e4e4cd);
}
.mount {
  position: relative;
  width: min(100%, clamp(170px, 38dvh, 330px));
  aspect-ratio: 1;
}
.plate {
  position: absolute;
  inset: 0;
  transform-origin: var(--point-x) var(--point-y);
}
.plate.reference {
  background-image: url('@/assets/garden/catgrass-stages.webp');
  background-size: 400% 200%;
  background-position: 66.6667% 100%;
}
.plate.enlarged {
  transform: translate(calc(50% - var(--point-x)), calc(50% - var(--point-y))) scale(2.35);
}
.lens {
  position: absolute;
  top: var(--point-y);
  left: var(--point-x);
  translate: -50% -50%;
  width: 44px;
  height: 44px;
  padding: 0;
  border-radius: 50%;
  border: 2px solid #fff5d9;
  background: transparent;
  box-shadow: 0 0 0 1px #514e34;
}
.lens span {
  position: absolute;
  top: -5px;
  right: -6px;
  width: 18px;
  line-height: 18px;
  border-radius: 50%;
  background: #496047;
  color: #fffbed;
  font-weight: bold;
}
figcaption {
  padding: 12px 18px 4px;
}
h3 {
  font-size: 18px;
  color: #3c5038;
}
p {
  margin-top: 4px;
  line-height: 1.5;
  font-size: 14px;
}
.view-tools {
  padding: 4px 14px 12px;
  flex-wrap: wrap;
  gap: 4px;
}
.view-tools button {
  padding: 4px 8px;
  text-decoration: underline;
  text-underline-offset: 4px;
  color: #506442;
  font-size: 14px;
}
.view-tools [aria-pressed='true'] {
  background: #e5e8d5;
  border-radius: 4px;
}
@media (max-width: 590px) {
  .spread {
    display: block;
  }
  .index {
    padding: 2px 10px 8px;
    border: 0;
  }
  .samples {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 2px;
    margin-top: 4px;
  }
  .samples button {
    gap: 3px;
    padding: 2px 3px;
    font-size: 13px;
  }
  .swatch {
    width: 26px;
  }
  .mount {
    width: min(100%, clamp(165px, 30dvh, 260px));
  }
}
@media (max-height: 460px) and (min-width: 591px) {
  .samples {
    grid-template-columns: 1fr 1fr;
  }
  .swatch {
    display: none;
  }
}
</style>
