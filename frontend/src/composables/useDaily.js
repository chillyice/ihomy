import { ref } from 'vue'
import { dailyApi } from '@/api'
import { readDailyPrefs } from '@/utils/dailyPrefs'

// 每日一图 / 每日知识卡片数据(暖居 + 光尘首页共用);后端按开启分类随机返回,reload 即「换一条」
export function useDaily() {
  const prefs = readDailyPrefs()
  const image = ref(null) // { url, copyright }
  const knowledge = ref('')
  const loadImage = async () => {
    if (!prefs.imageOn) return
    try {
      image.value = await dailyApi.image()
    } catch {}
  }
  const loadKnowledge = async () => {
    if (!prefs.knowledgeOn) return
    try {
      knowledge.value = (await dailyApi.knowledge(prefs.types.join(',')))?.content || ''
    } catch {}
  }
  let loaded = false
  // 幂等:组件在卡片出现时调用即可,重复调用无副作用
  const load = () => {
    if (loaded) return Promise.resolve()
    loaded = true
    return Promise.all([loadImage(), loadKnowledge()])
  }
  return {
    image,
    knowledge,
    imageOn: prefs.imageOn,
    knowledgeOn: prefs.knowledgeOn,
    load,
    reload: loadKnowledge,
  }
}
