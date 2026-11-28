import { reactive } from 'vue'

// The cuttings and shared beds are sketches. The task slip follows the local first-pot save.
export function createGardenBookReview(t, saveKey) {
  const key = saveKey + ':task-slip'
  function readSlip() {
    try {
      return JSON.parse(localStorage.getItem(key)) || {}
    } catch {
      return {}
    }
  }
  const blank = { sunned: false, inLight: false, harvested: false, sunClaimed: false, harvestClaimed: false }
  const slip = reactive({ ...blank, ...readSlip() })
  function mark(values) {
    Object.assign(slip, values)
    try {
      localStorage.setItem(key, JSON.stringify(slip))
    } catch {
      /* Keep this visit playable. */
    }
  }
  const crops = [
    { code: 'CATGRASS', name: t('garden.book.catgrass'), firstPot: true, packCount: 1 },
    { code: 'MINT', name: t('garden.book.mint') },
  ]
  const planted = { prepared: true, plantedAt: 'review-first', waterCooldownSec: 0, lastWateredAt: 'review-first' }
  const bed = { ...planted, ownerUid: '', venue: 'BACKYARD', mine: true, cropCode: 'CATGRASS', cropName: crops[0].name }
  const plots = [
    { id: 'review-home', mine: true, ownerUid: 'me', venue: 'WINDOWSILL', slotNo: 0 },
    {
      ...planted,
      id: 'review-guest',
      mine: false,
      ownerUid: 'guest',
      ownerName: t('garden.book.guest'),
      venue: 'WINDOWSILL',
      slotNo: 0,
      cropCode: 'MINT',
      cropName: crops[1].name,
      stage: 3,
    },
    { ...bed, id: 'review-dry', slotNo: 0, stage: 2, thirsty: true },
    { ...bed, id: 'review-ripe', slotNo: 1, stage: 5, harvestable: true },
  ]
  let cycle = 0
  function change(p, values) {
    Object.assign(p, values)
    return { plot: { ...p } }
  }
  return {
    slip,
    mark,
    restoreSlip: () => Object.assign(slip, blank, readSlip()),
    resetSlip: () => mark(blank),
    tasks: () => ({
      starter: [
        {
          code: 'review-sun',
          title: t('garden.todo.reviewSun'),
          condition: { type: 'CARE_N', value: 1 },
          visible: true,
          completed: slip.sunned,
          progress: Number(slip.sunned),
          claimed: slip.sunClaimed,
        },
        {
          code: 'review-harvest',
          title: t('garden.todo.reviewHarvest'),
          condition: { type: 'HARVEST_N', value: 1 },
          visible: slip.sunClaimed,
          completed: slip.harvested,
          progress: Number(slip.harvested),
          claimed: slip.harvestClaimed,
        },
      ],
    }),
    claimTask(code) {
      if (code === 'review-sun' && slip.sunned) mark({ sunClaimed: true })
      if (code === 'review-harvest' && slip.sunClaimed && slip.harvested) mark({ harvestClaimed: true })
      return {}
    },
    panorama: () => ({ plots: plots.map((p) => ({ ...p })), unlockedCrops: crops, letters: [] }),
    water: (id) =>
      change(
        plots.find((p) => p.id === id),
        { thirsty: false, lastWateredAt: 'review-water', waterCooldownSec: 43200 },
      ),
    harvest: (id) =>
      change(
        plots.find((p) => p.id === id),
        { cropCode: null, cropName: null, plantedAt: null, stage: null, harvestable: false, thirsty: false, lastWateredAt: null },
      ),
    sow: (slotNo, code, _key, venue) =>
      change(
        plots.find((p) => p.venue === venue && p.slotNo === slotNo && p.mine),
        {
          cropCode: code,
          cropName: crops.find((c) => c.code === code)?.name,
          plantedAt: 'review-' + ++cycle,
          stage: 0,
          waterCooldownSec: 0,
          lastWateredAt: null,
        },
      ),
  }
}
