<!-- 咔哒(Kada)发布页 —— 独立产品页,托管在 ihomy 域名 https://ihomy.top/kada。
     配色与控件走咔哒自己的浅色主题令牌(ui/src/style.css 的 [data-theme="light"]:
     --bg/--panel/--line/--text/--muted/--accent/--accent-dim/--key-bg/--key-line),
     页内自包含、不随 ihomy 的暖居/光尘主题变化;路由 meta.standalone 让 App.vue 不套
     ihomy 外壳、直接全屏渲染,也不初始化家庭数据。 -->
<template>
  <div class="kada">
    <div class="kwrap">
      <!-- 顶栏:品牌 + GitHub 入口 -->
      <header class="ktop">
        <div class="kbrand">
          <div class="kmark" aria-hidden="true"><span>K</span></div>
          <span class="kbrand-cn">{{ $t('kada.brand') }}</span>
          <span class="kbrand-en">Kada</span>
        </div>
        <a class="kgh" :href="repoUrl" target="_blank" rel="noopener noreferrer" :aria-label="$t('kada.ghAria')">
          <span class="kgh-ic" aria-hidden="true" v-html="ghIcon"></span>
          GitHub
        </a>
      </header>

      <!-- 主视觉 -->
      <section class="khero">
        <div class="kkey" aria-hidden="true">
          <span class="kkey-top">K</span>
          <span class="kkey-base"></span>
        </div>
        <h1 class="ktitle">{{ $t('kada.brand') }}<span class="ktitle-en">Kada</span></h1>
        <p class="ktag">{{ $t('kada.tag') }}</p>
        <p class="ksub">{{ $t('kada.sub') }}</p>

        <!-- 一次触发跑一串动作:用咔哒自己的动作链视觉(节点圆点 + 类型色)示意 -->
        <div class="kflow">
          <span class="kflow-cap">{{ $t('kada.flowCaption') }}</span>
          <span class="kflow-row">
            <span class="kflow-bolt" aria-hidden="true">⚡</span>
            <span class="kflow-keys" aria-hidden="true"> <kbd>Ctrl</kbd><kbd>Alt</kbd><kbd>K</kbd> </span>
            <span class="kflow-arrow" aria-hidden="true">→</span>
            <span v-for="(s, i) in flowSteps" :key="s.key" class="kflow-step">
              <i class="kflow-dot" :class="s.cls" aria-hidden="true"></i>{{ $t(s.key) }}
              <span v-if="i < flowSteps.length - 1" class="kflow-arrow" aria-hidden="true">→</span>
            </span>
            <span class="kflow-done" aria-hidden="true">✓</span>
          </span>
        </div>

        <div class="kcta">
          <a class="kbtn kbtn-primary" :href="exeUrl" download>
            <span class="kbtn-ic" aria-hidden="true" v-html="dlIcon"></span>
            {{ $t('kada.ctaDownload') }}
          </a>
          <button class="kbtn kbtn-ghost" type="button" @click="scrollToPlatforms">
            {{ $t('kada.ctaPlatforms') }}
          </button>
        </div>

        <div class="kmeta">
          <span class="kchip">v{{ version }}</span>
          <span class="kchip">{{ $t('kada.chipWindows') }}</span>
          <span class="kchip">≈ {{ fileSize }}</span>
          <span class="kchip">MIT</span>
        </div>
      </section>

      <!-- 能力速览 -->
      <section class="kstats">
        <div v-for="s in stats" :key="s.labelKey" class="kstat">
          <div class="kstat-n">{{ s.num }}</div>
          <div class="kstat-l">{{ $t(s.labelKey) }}</div>
        </div>
      </section>

      <!-- 下载 -->
      <section id="kada-download" class="ksec">
        <div class="klabel">{{ $t('kada.secDownload') }}</div>
        <p class="ksec-hint">{{ $t('kada.dlHint') }}</p>

        <div class="kplat">
          <article v-for="p in platforms" :key="p.key" class="kplat-card">
            <div class="kplat-head">
              <span class="kplat-ic" aria-hidden="true" v-html="p.icon"></span>
              <div class="kplat-titles">
                <h3 class="kplat-name">{{ $t(p.nameKey) }}</h3>
                <p class="kplat-arch">{{ $t(p.archKey) }}</p>
              </div>
            </div>
            <ul class="kplat-dls">
              <li v-for="d in p.downloads" :key="d.labelKey">
                <a
                  class="kdl"
                  :class="{ 'kdl-primary': d.primary }"
                  :href="d.href"
                  :download="d.local ? '' : undefined"
                  :target="d.local ? undefined : '_blank'"
                  :rel="d.local ? undefined : 'noopener noreferrer'"
                >
                  <span class="kdl-ic" aria-hidden="true" v-html="dlIcon"></span>
                  <span class="kdl-label">{{ $t(d.labelKey) }}</span>
                  <span class="kdl-meta">{{ d.meta }}</span>
                </a>
              </li>
            </ul>
          </article>
        </div>

        <p class="krelease">
          {{ $t('kada.releaseHint') }}
          <a :href="releasesUrl" target="_blank" rel="noopener noreferrer"> GitHub Releases </a>
        </p>
      </section>

      <!-- 功能 -->
      <section class="ksec">
        <div class="klabel">{{ $t('kada.secFeatures') }}</div>
        <p class="ksec-hint">{{ $t('kada.featHint') }}</p>

        <div v-for="g in featureGroups" :key="g.labelKey" class="kgroup">
          <div class="kgroup-label">{{ $t(g.labelKey) }}</div>
          <div class="kgrid">
            <div v-for="f in g.items" :key="f.titleKey" class="kcard">
              <div class="kcard-ic" aria-hidden="true" v-html="f.icon"></div>
              <h3 class="kcard-t">{{ $t(f.titleKey) }}</h3>
              <p class="kcard-d">{{ $t(f.descKey) }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 首次运行与权限 -->
      <section class="ksec">
        <div class="klabel">{{ $t('kada.notesLabel') }}</div>
        <div class="knotes">
          <div v-for="n in notes" :key="n.titleKey" class="knote">
            <div class="knote-t">{{ $t(n.titleKey) }}</div>
            <p class="knote-b">{{ $t(n.bodyKey) }}</p>
          </div>
        </div>
      </section>

      <!-- 页脚 -->
      <footer class="kfoot">
        <span>{{ $t('kada.footer', { version }) }}</span>
        <span class="ksep" aria-hidden="true">·</span>
        <span>MIT License</span>
        <span class="ksep" aria-hidden="true">·</span>
        <a :href="repoUrl" target="_blank" rel="noopener noreferrer">
          {{ $t('kada.sourceCode') }}
        </a>
        <span class="ksep" aria-hidden="true">·</span>
        <span>{{ $t('kada.hostedBy') }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'

// ---- 分发渠道 ----
// Windows 绿色版 + 安装版(NSIS) 与 Linux(deb / AppImage) 都发 ihomy 直链
// (放服务器 /opt/ihomy/uploads/kada/,nginx /files/ 反代托管),免登录、免过 GitHub;
// macOS(universal dmg) 本机无 Mac 打不出来,仍指向 GitHub Releases 同 tag 产物
// (由 Kada 仓 .github/workflows/release.yml 的 macos job 出,同时也供应用内自动更新)。
// ⚠ 换版本时同步三处:version 常量、Windows 裸包体积 fileSize、Kada 仓该 tag 的 Release 已发布。
const version = '1.1.0'
const fileSize = '14.0 MB'
const repoUrl = 'https://github.com/chillyice/Kada'
const releasesUrl = `${repoUrl}/releases`
const assetUrl = (name) => `${repoUrl}/releases/download/v${version}/${name}`
const exeUrl = '/files/kada/kada.exe'
const fileUrl = (name) => `/files/kada/${name}`

const ghIcon =
  '<svg viewBox="0 0 16 16"><path fill="currentColor" stroke="none" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.4 7.4 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>'
const dlIcon = '<svg viewBox="0 0 24 24"><path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>'

const flowSteps = [
  { key: 'kada.flowText', cls: 'st-text' },
  { key: 'kada.flowKeys', cls: 'st-keys' },
  { key: 'kada.flowCmd', cls: 'st-command' },
  { key: 'kada.flowUrl', cls: 'st-url' },
]

const stats = [
  { num: '3', labelKey: 'kada.statTriggers' },
  { num: '11', labelKey: 'kada.statActions' },
  { num: '3', labelKey: 'kada.statPlatforms' },
  { num: '0', labelKey: 'kada.statCloud' },
]

const platformIcons = {
  win: '<svg viewBox="0 0 24 24"><path d="M4 5.6 10.6 4.7v6.8H4zM13.4 4.3 20 3.4v8.1h-6.6zM4 12.5h6.6v6.8L4 18.4zM13.4 12.5H20v8.1l-6.6-.9z"/></svg>',
  linux: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9.5 10 12l-3 2.5M13.5 15h3.5"/></svg>',
  mac: '<svg viewBox="0 0 24 24"><path d="M9 9h6v6H9z"/><circle cx="7" cy="7" r="2"/><circle cx="17" cy="7" r="2"/><circle cx="7" cy="17" r="2"/><circle cx="17" cy="17" r="2"/></svg>',
}

const platforms = [
  {
    key: 'win',
    icon: platformIcons.win,
    nameKey: 'kada.platWin',
    archKey: 'kada.archWin',
    downloads: [
      {
        labelKey: 'kada.dlWinPortable',
        meta: fileSize,
        href: exeUrl,
        primary: true,
        local: true,
      },
      {
        labelKey: 'kada.dlWinSetup',
        meta: 'x64',
        href: fileUrl(`Kada_${version}_x64-setup.exe`),
        local: true,
      },
    ],
  },
  {
    key: 'linux',
    icon: platformIcons.linux,
    nameKey: 'kada.platLinux',
    archKey: 'kada.archLinux',
    downloads: [
      {
        labelKey: 'kada.dlLinuxAppImage',
        meta: 'amd64',
        href: fileUrl(`Kada_${version}_amd64.AppImage`),
        primary: true,
        local: true,
      },
      {
        labelKey: 'kada.dlLinuxDeb',
        meta: 'amd64',
        href: fileUrl(`Kada_${version}_amd64.deb`),
        local: true,
      },
    ],
  },
  {
    key: 'mac',
    icon: platformIcons.mac,
    nameKey: 'kada.platMac',
    archKey: 'kada.archMac',
    downloads: [
      {
        labelKey: 'kada.dlMacDmg',
        meta: 'universal',
        href: assetUrl(`Kada_${version}_universal.dmg`),
        primary: true,
        local: false,
      },
    ],
  },
]

const featureGroups = [
  {
    labelKey: 'kada.grpTrigger',
    items: [
      {
        titleKey: 'kada.f1Title',
        descKey: 'kada.f1Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="7" width="18" height="12" rx="2"/><path d="M7 11h.01M11 11h.01M15 11h.01M19 11h.01M8 15h8"/></svg>',
      },
      {
        titleKey: 'kada.f2Title',
        descKey: 'kada.f2Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 8h8a4 4 0 0 1 4 4M4 16h5"/><path d="M17 9.5 20.5 12 17 14.5"/></svg>',
      },
      {
        titleKey: 'kada.f3Title',
        descKey: 'kada.f3Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3.5 21 8l-9 4.5L3 8z"/><path d="M3 12.5 12 17l9-4.5M3 16.5 12 21l9-4.5"/></svg>',
      },
      {
        titleKey: 'kada.f4Title',
        descKey: 'kada.f4Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 6h16v12H4z"/><path d="M7 10h5M7 14h9"/></svg>',
      },
      {
        titleKey: 'kada.f5Title',
        descKey: 'kada.f5Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M6 4v6a3 3 0 0 0 3 3h9"/><path d="M15 10l3 3-3 3"/><circle cx="5" cy="20" r="1.6"/></svg>',
      },
    ],
  },
  {
    labelKey: 'kada.grpAction',
    items: [
      {
        titleKey: 'kada.f6Title',
        descKey: 'kada.f6Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M6 4h12M6 12h12M6 20h12"/><circle cx="3.5" cy="4" r="1.2"/><circle cx="3.5" cy="12" r="1.2"/><circle cx="3.5" cy="20" r="1.2"/></svg>',
      },
      {
        titleKey: 'kada.f7Title',
        descKey: 'kada.f7Desc',
        icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/><path d="M5 5l2 2M19 5l-2 2"/></svg>',
      },
      {
        titleKey: 'kada.f8Title',
        descKey: 'kada.f8Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 7h6l3 5-3 5H4"/><path d="M14 12h6M17 9l3 3-3 3"/></svg>',
      },
      {
        titleKey: 'kada.f9Title',
        descKey: 'kada.f9Desc',
        icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.2"/></svg>',
      },
    ],
  },
  {
    labelKey: 'kada.grpConfig',
    items: [
      {
        titleKey: 'kada.f10Title',
        descKey: 'kada.f10Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h10"/><circle cx="17" cy="18" r="2.4"/></svg>',
      },
      {
        titleKey: 'kada.f11Title',
        descKey: 'kada.f11Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z"/><path d="M9 12l2 2 4-4"/></svg>',
      },
      {
        titleKey: 'kada.f12Title',
        descKey: 'kada.f12Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 6h7v12H4zM13 6h7v12h-7z"/><path d="M11 12h2"/></svg>',
      },
      {
        titleKey: 'kada.f13Title',
        descKey: 'kada.f13Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M9 20h6M12 16v4"/></svg>',
      },
      {
        titleKey: 'kada.f14Title',
        descKey: 'kada.f14Desc',
        icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4"/></svg>',
      },
    ],
  },
]

const notes = [
  { titleKey: 'kada.noteWinTitle', bodyKey: 'kada.noteWinBody' },
  { titleKey: 'kada.noteLinuxTitle', bodyKey: 'kada.noteLinuxBody' },
  { titleKey: 'kada.noteMacTitle', bodyKey: 'kada.noteMacBody' },
  { titleKey: 'kada.noteUpdateTitle', bodyKey: 'kada.noteUpdateBody' },
]

const { t } = useI18n()

function scrollToPlatforms() {
  document.getElementById('kada-download')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  document.title = t('kada.docTitle')
  // 独立页整屏渲染,根元素也得跟着换色(滚动条/橡皮筋区,见样式里的 :global 段);
  // 类在挂载/卸载时增删,保证这份 chunk 的规则不会漏给 ihomy 其他页面。
  document.documentElement.classList.add('kada-page')
})
onBeforeUnmount(() => {
  document.title = 'ihomy'
  document.documentElement.classList.remove('kada-page')
})
</script>

<style scoped>
/* 页面自包含:令牌取咔哒自己的深浅两套主题(与 Kada 仓 ui/src/style.css 的
   [data-theme="light"] / :root 同值同义),不读 ihomy 主题变量、不随暖居/光尘与晨暮变。
   浅色为默认,深色由浏览器/系统的 prefers-color-scheme 触发——两套只换令牌,
   版式与组件规则共用下面这一份(故别在别处写死颜色,新增颜色一律走变量)。 */
.kada {
  --bg: #f2f3f5;
  --panel: #ffffff;
  --line: #d9dce2;
  --line-soft: rgba(0, 0, 0, 0.06);
  --text: #1c1f26;
  --muted: #626b7a;
  --accent: #d1553a;
  --accent-dim: #f6ddd6;
  --key-bg: #e6e9ee;
  --key-line: #c3c9d3;
  /* 以下为页面自用的衍生量:图标底/提示卡底是 accent 的淡色,键帽与投影在深浅主题下
     材质不同(浅色白面键帽、深色 accent 面键帽),所以整套跟着主题走。 */
  --plate: rgba(209, 85, 58, 0.12);
  --accent-soft: rgba(209, 85, 58, 0.055);
  --accent-soft-line: rgba(209, 85, 58, 0.2);
  --hover-line: rgba(209, 85, 58, 0.42);
  --shadow-card: 0 2px 10px rgba(28, 31, 38, 0.04);
  --shadow-lift: 0 12px 26px rgba(28, 31, 38, 0.09);
  --cta-shadow: 0 8px 20px rgba(209, 85, 58, 0.28);
  --cta-shadow-hi: 0 12px 26px rgba(209, 85, 58, 0.34);
  --keycap-face: linear-gradient(180deg, #ffffff 0%, #f4f5f8 100%);
  --keycap-ink: var(--accent);
  --keycap-edge: var(--line);
  --keycap-lift: 0 12px 24px rgba(28, 31, 38, 0.1);
  --keycap-inset: inset 0 -3px 0 rgba(28, 31, 38, 0.05);
  --keycap-base: linear-gradient(180deg, #dfe2e8, #c9ced7);
  --keycap-base-lift: 0 8px 18px rgba(28, 31, 38, 0.14);
  --halo:
    radial-gradient(880px 420px at 50% -8%, rgba(209, 85, 58, 0.1), transparent 62%),
    radial-gradient(680px 380px at 94% 3%, rgba(79, 156, 249, 0.07), transparent 60%), var(--bg);
  /* 动作类型色(同 ui/src/style.css 的 .flow-step.type-*),只用于动作链示意里的圆点 */
  --t-text: #4ec97a;
  --t-keys: #f97a50;
  --t-command: #4f9cf9;
  --t-url: #e879b9;
  --mono: Consolas, 'Cascadia Mono', monospace;

  color-scheme: light;
  min-height: 100vh;
  color: var(--text);
  font:
    14px/1.6 'Microsoft YaHei UI',
    'Segoe UI',
    system-ui,
    sans-serif;
  background: var(--halo);
}

/* 深色主题:跟随浏览器/系统偏好。键帽改 accent 面 + 深色字(浅色那套是白面 + accent 字),
   投影整体加深(深底上浅投影看不见)。 */
@media (prefers-color-scheme: dark) {
  .kada {
    --bg: #111318;
    --panel: #191d24;
    --line: #2a2f3a;
    --line-soft: rgba(255, 255, 255, 0.07);
    --text: #e8eaf0;
    --muted: #8b93a3;
    --accent: #e06040;
    --accent-dim: #7a3a2a;
    --key-bg: #262b35;
    --key-line: #3a4150;
    --plate: rgba(224, 96, 64, 0.18);
    --accent-soft: rgba(224, 96, 64, 0.09);
    --accent-soft-line: rgba(224, 96, 64, 0.28);
    --hover-line: rgba(224, 96, 64, 0.5);
    --shadow-card: 0 2px 10px rgba(0, 0, 0, 0.3);
    --shadow-lift: 0 14px 30px rgba(0, 0, 0, 0.45);
    --cta-shadow: 0 8px 22px rgba(224, 96, 64, 0.3);
    --cta-shadow-hi: 0 12px 28px rgba(224, 96, 64, 0.38);
    --keycap-face: linear-gradient(160deg, #f08a6a 0%, #e06040 55%, #b8432a 100%);
    --keycap-ink: #20130a;
    --keycap-edge: transparent;
    --keycap-lift: 0 10px 28px rgba(224, 96, 64, 0.34);
    --keycap-inset: inset 0 1px 0 rgba(255, 255, 255, 0.28);
    --keycap-base: linear-gradient(180deg, #8f3a26, #6d2b1c);
    --keycap-base-lift: 0 8px 20px rgba(0, 0, 0, 0.45);
    --halo:
      radial-gradient(900px 440px at 50% -8%, rgba(224, 96, 64, 0.16), transparent 62%),
      radial-gradient(700px 380px at 92% 4%, rgba(120, 170, 255, 0.08), transparent 60%), var(--bg);

    color-scheme: dark;
  }
}
/* 独立页整屏渲染时,根元素也是页面的一部分:ihomy 外壳默认暖居·暮,html 上带着深色底与
   深色滚动条,浅色浏览器下会在浅色页面上顶出一条深色边。底色取上面两套 --bg 的同值
   (令牌声明在 .kada 上,html 取不到)。 */
:global(html.kada-page:root) {
  color-scheme: light;
  background: #f2f3f5;
}
@media (prefers-color-scheme: dark) {
  :global(html.kada-page:root) {
    color-scheme: dark;
    background: #111318;
  }
}
.kwrap {
  max-width: 1060px;
  margin: 0 auto;
  padding: 0 24px;
}

/* ---------- 顶栏 ---------- */
.ktop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 24px 0 4px;
}
.kbrand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.kmark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--keycap-face);
  border: 1px solid var(--keycap-edge);
  color: var(--keycap-ink);
  font-weight: 800;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 6px 14px var(--keycap-lift),
    var(--keycap-inset);
}
.kbrand-cn {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.kbrand-en {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.kgh {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: none;
  transition:
    color 0.16s ease,
    border-color 0.16s ease,
    transform 0.16s ease;
}
.kgh:hover {
  color: var(--text);
  border-color: var(--muted);
  transform: translateY(-1px);
}
.kgh-ic {
  width: 15px;
  height: 15px;
  display: block;
}
.kgh-ic :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

/* ---------- 主视觉 ---------- */
.khero {
  text-align: center;
  padding: 56px 0 44px;
}
.kkey {
  position: relative;
  width: 82px;
  height: 80px;
  margin: 0 auto 26px;
}
.kkey-top {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 82px;
  height: 70px;
  border-radius: 16px;
  background: var(--keycap-face);
  border: 1px solid var(--keycap-edge);
  color: var(--keycap-ink);
  font-size: 34px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--keycap-lift), var(--keycap-inset);
}
.kkey-base {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 96px;
  height: 18px;
  border-radius: 6px;
  background: var(--keycap-base);
  box-shadow: var(--keycap-base-lift);
}
.ktitle {
  font-size: 50px;
  font-weight: 800;
  letter-spacing: 0.01em;
  line-height: 1.1;
  margin: 0 0 10px;
}
.ktitle-en {
  font-size: 24px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.06em;
  margin-left: 10px;
  vertical-align: 4px;
}
.ktag {
  font-size: 19px;
  font-weight: 600;
  color: var(--accent);
  margin: 0 0 8px;
}
.ksub {
  font-size: 15px;
  color: var(--muted);
  margin: 0 auto;
  max-width: 640px;
}

/* 动作链示意 */
.kflow {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  margin-top: 26px;
  padding: 12px 20px 13px;
  border-radius: 14px;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-card);
}
.kflow-cap {
  font-size: 11.5px;
  color: var(--muted);
  letter-spacing: 0.04em;
}
.kflow-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
.kflow-bolt {
  color: var(--accent);
  font-size: 13px;
}
.kflow-keys {
  display: inline-flex;
  gap: 3px;
}
.kflow-keys kbd {
  font-family: var(--mono);
  font-size: 11.5px;
  line-height: 1.45;
  padding: 1px 6px;
  color: var(--text);
  background: var(--key-bg);
  border: 1px solid var(--key-line);
  border-bottom-width: 2px;
  border-radius: 5px;
}
.kflow-arrow {
  color: var(--muted);
  opacity: 0.65;
  font-size: 12px;
}
.kflow-step {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: var(--muted);
}
.kflow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid var(--muted);
  box-sizing: content-box;
}
.kflow-dot.st-text {
  border-color: var(--t-text);
}
.kflow-dot.st-keys {
  border-color: var(--t-keys);
}
.kflow-dot.st-command {
  border-color: var(--t-command);
}
.kflow-dot.st-url {
  border-color: var(--t-url);
}
.kflow-done {
  color: #4ec97a;
  font-size: 12.5px;
}

/* CTA */
.kcta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 30px;
  flex-wrap: wrap;
}
.kbtn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 24px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  font-family: inherit;
  text-decoration: none;
  cursor: pointer;
  transition:
    transform 0.16s ease,
    box-shadow 0.16s ease,
    filter 0.16s ease,
    border-color 0.16s ease;
}
.kbtn-ic,
.kdl-ic {
  width: 17px;
  height: 17px;
  display: block;
}
.kbtn-ic :deep(svg),
.kdl-ic :deep(svg) {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.kbtn-primary {
  background: var(--accent);
  border: 1px solid var(--accent);
  color: #fff;
  box-shadow: var(--cta-shadow);
}
.kbtn-primary:hover {
  transform: translateY(-2px);
  filter: brightness(1.05);
  box-shadow: var(--cta-shadow-hi);
}
.kbtn-ghost {
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text);
}
.kbtn-ghost:hover {
  transform: translateY(-2px);
  border-color: var(--muted);
  box-shadow: var(--shadow-lift);
}

.kmeta {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
  flex-wrap: wrap;
}
.kchip {
  font-size: 12.5px;
  color: var(--muted);
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--panel);
  border: 1px solid var(--line);
}

/* ---------- 能力速览 ---------- */
.kstats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding-bottom: 8px;
}
.kstat {
  padding: 16px 14px;
  border-radius: 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  text-align: center;
}
.kstat-n {
  font-family: var(--mono);
  font-size: 26px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1.2;
}
.kstat-l {
  font-size: 12.5px;
  color: var(--muted);
}

/* ---------- 分区 ---------- */
.ksec {
  padding: 38px 0 0;
}
.klabel {
  font-size: 12px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.ksec-hint {
  font-size: 14px;
  color: var(--muted);
  margin: 4px 0 16px;
}

/* 下载 */
.kplat {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.kplat-card {
  display: flex;
  flex-direction: column;
  padding: 20px 18px 18px;
  border-radius: 16px;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-card);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}
.kplat-card:hover {
  border-color: var(--hover-line);
  box-shadow: var(--shadow-lift);
  transform: translateY(-2px);
}
.kplat-head {
  display: flex;
  align-items: center;
  gap: 11px;
}
.kplat-ic {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 10px;
  background: var(--plate);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kplat-ic :deep(svg) {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.kplat-titles {
  min-width: 0;
}
.kplat-name {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
.kplat-arch {
  font-size: 12px;
  color: var(--muted);
  margin: 1px 0 0;
}
.kplat-dls {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.kdl {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 13px;
  border-radius: 10px;
  background: var(--bg);
  border: 1px solid var(--line);
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
  text-decoration: none;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    color 0.16s ease;
}
.kdl:hover {
  border-color: var(--muted);
}
.kdl-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kdl-meta {
  margin-left: auto;
  flex: none;
  font-family: var(--mono);
  font-size: 11.5px;
  font-weight: 400;
  color: var(--muted);
}
.kdl-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.kdl-primary .kdl-meta {
  color: rgba(255, 255, 255, 0.82);
}
.kdl-primary:hover {
  filter: brightness(1.06);
  border-color: var(--accent);
}
.krelease {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--muted);
}
.krelease a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid var(--accent-dim);
}
.krelease a:hover {
  border-bottom-color: var(--accent);
}

/* 功能 */
.kgroup + .kgroup {
  margin-top: 22px;
}
.kgroup-label {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 10px;
}
.kgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(258px, 1fr));
  gap: 12px;
}
.kcard {
  padding: 18px 18px 16px;
  border-radius: 14px;
  background: var(--panel);
  border: 1px solid var(--line);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}
.kcard:hover {
  border-color: var(--hover-line);
  box-shadow: var(--shadow-lift);
  transform: translateY(-2px);
}
.kcard-ic {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--plate);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.kcard-ic :deep(svg) {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.kcard-t {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 6px;
}
.kcard-d {
  font-size: 12.8px;
  line-height: 1.7;
  color: var(--muted);
  margin: 0;
}

/* 首次运行与权限 */
.knotes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.knote {
  padding: 16px 18px;
  border-radius: 14px;
  background: var(--accent-soft);
  border: 1px solid var(--accent-soft-line);
}
.knote-t {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 6px;
}
.knote-b {
  margin: 0;
  font-size: 12.8px;
  line-height: 1.7;
  color: var(--muted);
}

/* 页脚 */
.kfoot {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 40px;
  padding: 22px 0 40px;
  border-top: 1px solid var(--line-soft);
  font-size: 12.5px;
  color: var(--muted);
}
.kfoot a {
  color: var(--muted);
  text-decoration: none;
}
.kfoot a:hover {
  color: var(--accent);
}
.ksep {
  opacity: 0.6;
}

@media (max-width: 860px) {
  .kplat {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .khero {
    padding: 40px 0 34px;
  }
  .ktitle {
    font-size: 38px;
  }
  .ktitle-en {
    font-size: 18px;
    vertical-align: 3px;
  }
  .kstats {
    grid-template-columns: 1fr 1fr;
  }
  .knotes {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 460px) {
  .kgrid {
    grid-template-columns: 1fr;
  }
  .kflow-row {
    gap: 6px;
  }
}
</style>
