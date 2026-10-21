<!-- chillyice 个人主页 —— 独立页(https://ihomy.top/chillyice),全公开、不套 ihomy 外壳。
     内容尚未定稿,先给出可访问的占位页;个人首页设计落地时只替换本组件,
     路由与 meta.public / meta.standalone 无需再动。 -->
<template>
  <main class="cy">
    <div class="cy-glow" aria-hidden="true"></div>

    <section class="cy-stage">
      <p class="cy-kicker">
        <span class="cy-dot" aria-hidden="true"></span>
        {{ $t('chillyice.kicker') }}
      </p>

      <h1 class="cy-name">chillyice</h1>

      <p class="cy-role">{{ $t('chillyice.role') }}</p>
      <p class="cy-note">{{ $t('chillyice.note') }}</p>

      <nav class="cy-links" :aria-label="$t('chillyice.linksAria')">
        <router-link class="cy-link cy-link--primary" to="/home">{{ $t('chillyice.backHome') }}</router-link>
        <a class="cy-link" :href="githubUrl" target="_blank" rel="noopener noreferrer">{{ $t('chillyice.github') }}</a>
      </nav>
    </section>

    <footer class="cy-foot">© {{ year }} chillyice</footer>
  </main>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'

const githubUrl = 'https://github.com/chillyice'
const year = new Date().getFullYear()
const { t } = useI18n()

onMounted(() => {
  document.title = t('chillyice.docTitle')
  // 独立页整屏渲染,根元素一起接管配色(滚动条/橡皮筋区);卸载时移除,规则不外漏到 ihomy 其他页面
  document.documentElement.classList.add('chillyice-page')
})

onBeforeUnmount(() => {
  document.title = 'ihomy'
  document.documentElement.classList.remove('chillyice-page')
})
</script>

<style scoped>
/* 自带一套浅/深配色,跟随浏览器偏好,不随 ihomy 的暖居/光尘主题变化 */
:global(html.chillyice-page) {
  --cy-bg: #f6f2ea;
  --cy-ink: #201d19;
  --cy-muted: rgba(32, 29, 25, 0.62);
  --cy-line: rgba(32, 29, 25, 0.14);
  --cy-accent: #b96a3d;
  --cy-spot-a: rgba(233, 190, 132, 0.55);
  --cy-spot-b: rgba(142, 174, 204, 0.42);
  background: var(--cy-bg);
  color-scheme: light;
}
:global(html.chillyice-page body) {
  background: var(--cy-bg);
}
@media (prefers-color-scheme: dark) {
  :global(html.chillyice-page) {
    --cy-bg: #121315;
    --cy-ink: #f1ece4;
    --cy-muted: rgba(241, 236, 228, 0.58);
    --cy-line: rgba(241, 236, 228, 0.18);
    --cy-accent: #e0a06a;
    --cy-spot-a: rgba(120, 86, 52, 0.42);
    --cy-spot-b: rgba(58, 84, 112, 0.4);
    color-scheme: dark;
  }
}

.cy {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px 72px;
  overflow: hidden;
  background: var(--cy-bg);
  color: var(--cy-ink);
}

/* 静态氛围光:两层径向渐变,不参与交互 */
.cy-glow {
  position: absolute;
  inset: -20%;
  background:
    radial-gradient(closest-side at 22% 24%, var(--cy-spot-a), transparent 72%),
    radial-gradient(closest-side at 82% 72%, var(--cy-spot-b), transparent 74%);
  filter: blur(18px);
  pointer-events: none;
}

.cy-stage {
  position: relative;
  z-index: 1;
  max-width: 640px;
  text-align: center;
}

.cy-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 28px;
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--cy-muted);
}
.cy-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cy-accent);
}

.cy-name {
  margin: 0;
  font-size: clamp(48px, 11vw, 104px);
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.045em;
}

.cy-role {
  margin: 22px 0 0;
  font-size: clamp(15px, 2vw, 18px);
  letter-spacing: 0.02em;
}

.cy-note {
  max-width: 34em;
  margin: 14px auto 0;
  font-size: 14px;
  line-height: 1.85;
  color: var(--cy-muted);
}

.cy-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 36px;
}

.cy-link {
  display: inline-flex;
  align-items: center;
  height: 40px;
  padding: 0 20px;
  border: 1px solid var(--cy-line);
  border-radius: 999px;
  font-size: 13px;
  color: inherit;
  text-decoration: none;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}
.cy-link:hover {
  border-color: var(--cy-ink);
}
.cy-link:focus-visible {
  outline: 2px solid var(--cy-accent);
  outline-offset: 3px;
}
.cy-link--primary {
  background: var(--cy-ink);
  border-color: var(--cy-ink);
  color: var(--cy-bg);
}
.cy-link--primary:hover {
  background: var(--cy-accent);
  border-color: var(--cy-accent);
  color: #fff;
}

.cy-foot {
  position: relative;
  z-index: 1;
  margin-top: 56px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--cy-muted);
}
</style>
