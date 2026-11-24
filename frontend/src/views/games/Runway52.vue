<template>
  <Ludo v-if="roomId" />
  <GameShell
    v-else
    ref="shellEl"
    class="rw-shell"
    :title="$t('games.ludo.title')"
    body-align="stretch"
    new-game
    @back="goBack"
    @new-game="onShellNewGame"
  >
    <!-- 规则进外壳左栏的规则弹窗:文案与 runway-52.html 的规则弹窗是同一份(i18n games.runway52.rule*),两处要同步 -->
    <template #rules>
      <ol>
        <li v-for="n in 6" :key="n">{{ $t(`games.runway52.rule${n}`) }}</li>
      </ol>
    </template>
    <div class="runway-stage">
      <iframe ref="frameEl" class="runway-table" :src="tableUrl" title="Runway 52"></iframe>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'

const Ludo = defineAsyncComponent(() => import('./Ludo.vue'))
const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.query.room) || 0)
const tableUrl = `${import.meta.env.BASE_URL}runway-52.html?v=${__APP_VERSION__}`

const shellEl = ref(null)
const frameEl = ref(null)

// 外壳「新游戏」透传给 iframe 里的「新对局」:静态页自己的弹窗带「开始后会替换当前棋局」确认,外壳不再二次确认
const onShellNewGame = () => {
  frameEl.value?.contentDocument?.getElementById('new')?.click()
}

// 返回固定回上一级(小游戏列表):从首页卡片 / 分享链接直达时也回列表,不按浏览历史回到上一页
const goBack = () => {
  router.push('/games')
}
</script>

<style scoped>
.runway-stage {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.runway-table {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 320px;
  border: 0;
  border-radius: 14px;
  background: #172522;
}

/* 手机横屏/矮窗:外壳游戏区给多高就占多高(静态页自己按容器高铺满),
   不再用 320px 的下限把矮游戏区撑破、顶出外壳 */
@media (max-height: 520px) {
  .runway-table {
    min-height: 0;
  }
}
</style>

<!-- 以下规则以 html.game-mobile-immersive 为条件、目标在子组件(GameShell)内部,
     scoped 里 `:global(sel) 后续` 编译会把后续整段丢掉,所以这些必须放非 scoped 块 -->
<style>
/* 手机沉浸态:iframe 圆角抹平,游戏底色直通页面边缘 */
html.game-mobile-immersive .runway-table {
  min-height: 0;
  border-radius: 0;
}

/* 手机横屏沉浸态:航线 52 的墨绿底铺满整页——外壳底色同化成页面底色,左栏与四周留白不再露应用底色;
   头部标题换浅色、次级按钮改白玻璃(与 main.css 的 .gs-on-dark 同一套口径)。
   这里不用 gs-on-dark 类:深底只在「横屏矮屏」这一档出现,类名加不了媒体查询条件。
   token 一档给头部里其它小件(复选框、标签、禁用档)取浅色值;按钮的底色与字色再显式钉一档——
   光尘那条次级按钮规则是硬编码浅米底、暖居那条走 token 且带 !important,不钉就会被任意一边抢走。 */
@media (orientation: landscape) and (max-height: 520px) {
  html.game-mobile-immersive .rw-shell {
    --gs-bg: #172522;
  }
  html.game-mobile-immersive .rw-shell .gs-head {
    --color-line: rgba(255, 255, 255, 0.14);
    --color-card-2: rgba(255, 255, 255, 0.24);
    --color-border: rgba(255, 255, 255, 0.24);
    --color-text: #fff;
    --color-text-secondary: rgba(255, 255, 255, 0.82);
    --color-text-tertiary: rgba(255, 255, 255, 0.45);
  }
  html.game-mobile-immersive .rw-shell .gs-title {
    color: #f4f4e7;
  }
  html.game-mobile-immersive
    .rw-shell
    :is(.gs-head, .gs-controls)
    .el-button:not(.el-button--primary):not(.el-button--danger):not(.is-link):not(.is-text) {
    background: rgba(255, 255, 255, 0.14) !important;
    color: #fff !important;
  }
  html.game-mobile-immersive
    .rw-shell
    :is(.gs-head, .gs-controls)
    .el-button:not(.el-button--primary):not(.el-button--danger):not(.is-link):not(.is-text):hover {
    background: rgba(255, 255, 255, 0.24) !important;
  }
}
</style>
