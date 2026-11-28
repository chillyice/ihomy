// A fresh sketchbook per art-review session. No account, wallet or network calls.
export function createGardenBookReview(t) {
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
