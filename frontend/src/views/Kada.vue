<!-- 咔哒(Kada)发布页 —— 独立产品页,托管在 ihomy 域名 https://ihomy.top/kada。
     视觉与软件本体同源:令牌、像素字体、方角、扫描线、荧光与暗角都取自
     Kada 仓 ui/src/style.css 的像素 CRT 主题(data-theme="crt"),默认深色,
     右上角可切到 CRT 浅色(同一套结构,只换调色板与混合模式)。
     路由 meta.standalone 让 App.vue 不套 ihomy 外壳、直接全屏渲染。 -->
<template>
  <div ref="rootEl" class="kada" :data-crt="light ? 'light' : 'dark'">
    <!-- CRT 光学层:扫描线 / 暗缝 / 场同步亮带 / 暗角。全部 pointer-events:none。 -->
    <div class="kfx kfx-scan" aria-hidden="true"></div>
    <div class="kfx kfx-darken" aria-hidden="true"></div>
    <div class="kfx kfx-sweep" aria-hidden="true"></div>
    <div class="kfx kfx-vignette" aria-hidden="true"></div>

    <div class="kwrap" id="top">
      <!-- 顶栏:动画字标 + 分区导航 + 主题 / GitHub -->
      <header class="ktop">
        <a class="kbrand" href="#top" :aria-label="$t('kada.brandFull')">
          <span class="klogo klogo-sm" aria-hidden="true">
            <span class="kword">
              <span class="bw bw-cyan">K</span><span class="bw bw-yellow">a</span><span class="bw bw-red">d</span
              ><span class="bw bw-indigo">a</span>
            </span>
            <span class="kburst">
              <i class="bz bz-ray bz-r1"></i>
              <i class="bz bz-ray bz-r2"></i>
              <i class="bz bz-ray bz-r3"></i>
              <i class="bz bz-star bz-s1"></i>
              <i class="bz bz-star bz-s2"></i>
              <i class="bz bz-star bz-s3"></i>
            </span>
          </span>
          <span class="kbrand-cn">{{ $t('kada.brand') }}</span>
        </a>

        <nav class="knav" :aria-label="$t('kada.navAria')">
          <a href="#kada-features">{{ $t('kada.navFeatures') }}</a>
          <a href="#kada-shots">{{ $t('kada.navShots') }}</a>
          <a href="#kada-download">{{ $t('kada.navDownload') }}</a>
        </nav>

        <div class="kact">
          <button
            class="kicon-btn"
            type="button"
            :title="light ? $t('kada.themeToDark') : $t('kada.themeToLight')"
            :aria-label="light ? $t('kada.themeToDark') : $t('kada.themeToLight')"
            :aria-pressed="light"
            @click="toggleTheme"
          >
            <span v-html="light ? moonIcon : sunIcon" aria-hidden="true"></span>
          </button>
          <a class="kgh" :href="repoUrl" target="_blank" rel="noopener noreferrer" :aria-label="$t('kada.ghAria')">
            <span class="kgh-ic" aria-hidden="true" v-html="ghIcon"></span>
            GitHub
          </a>
        </div>
      </header>

      <!-- 主视觉 -->
      <section class="khero">
        <p class="kterm" aria-hidden="true">
          <span class="kterm-prompt">C:\Users\you&gt;</span>
          <span class="kterm-cmd">kada --theme crt</span>
          <span class="kcaret"></span>
        </p>

        <h1 class="khero-title" :aria-label="$t('kada.brandFull')">
          <span class="klogo klogo-lg" aria-hidden="true">
            <span class="kword">
              <span class="bw bw-cyan">K</span><span class="bw bw-yellow">a</span><span class="bw bw-red">d</span
              ><span class="bw bw-indigo">a</span>
            </span>
            <span class="kburst">
              <i class="bz bz-ray bz-r1"></i>
              <i class="bz bz-ray bz-r2"></i>
              <i class="bz bz-ray bz-r3"></i>
              <i class="bz bz-star bz-s1"></i>
              <i class="bz bz-star bz-s2"></i>
              <i class="bz bz-star bz-s3"></i>
            </span>
          </span>
        </h1>

        <p class="khero-cn">{{ $t('kada.heroCn') }}</p>
        <p class="ksub">{{ $t('kada.sub') }}</p>

        <!-- 一次触发跑一串动作:用软件里的动作链视觉示意 -->
        <div class="kflow">
          <div class="kflow-bar">
            <span class="kflow-cap">{{ $t('kada.flowCaption') }}</span>
            <span class="kflow-id">CHAIN&nbsp;01</span>
          </div>
          <div class="kflow-row">
            <span class="kflow-keys" aria-hidden="true"><kbd>Ctrl</kbd><kbd>Alt</kbd><kbd>B</kbd></span>
            <span class="kflow-arrow" aria-hidden="true">-&gt;</span>
            <span v-for="(s, i) in flowSteps" :key="s.key" class="kflow-step">
              <i class="kflow-dot" :class="s.cls" aria-hidden="true"></i>{{ $t(s.key) }}
              <span v-if="i < flowSteps.length - 1" class="kflow-arrow" aria-hidden="true">-&gt;</span>
            </span>
            <span class="kflow-done" aria-hidden="true">OK</span>
          </div>
        </div>

        <div class="kcta">
          <a class="kbtn kbtn-primary" :href="exeUrl" download>
            <span class="kbtn-ic" aria-hidden="true" v-html="dlIcon"></span>
            {{ $t('kada.ctaDownload') }}
          </a>
          <a class="kbtn kbtn-ghost" href="#kada-download">{{ $t('kada.ctaPlatforms') }}</a>
        </div>

        <div class="kmeta">
          <span class="kchip">v{{ version }}</span>
          <span class="kchip">{{ $t('kada.chipPlatforms') }}</span>
          <span class="kchip">{{ fileSize }}</span>
          <span class="kchip">MIT</span>
        </div>
      </section>

      <!-- 能力速览 -->
      <section class="kstats" :aria-label="$t('kada.statsAria')">
        <div v-for="s in stats" :key="s.labelKey" class="kstat">
          <div class="kstat-n">{{ s.num }}</div>
          <div class="kstat-l">{{ $t(s.labelKey) }}</div>
        </div>
      </section>

      <!-- 功能 -->
      <section id="kada-features" class="ksec">
        <div class="klabel"><span class="klabel-no">01</span>{{ $t('kada.secFeatures') }}<i class="klabel-line"></i></div>
        <p class="ksec-hint">{{ $t('kada.featHint') }}</p>

        <div v-for="g in featureGroups" :key="g.labelKey" class="kgroup">
          <div class="kgroup-label">{{ $t(g.labelKey) }}</div>
          <div class="kgrid">
            <article v-for="f in g.items" :key="f.titleKey" class="kcard" :class="{ 'kcard-exp': f.tag }">
              <div class="kcard-head">
                <div class="kcard-ic" aria-hidden="true" v-html="f.icon"></div>
                <span v-if="f.tag" class="ktag-exp">{{ $t(f.tag) }}</span>
              </div>
              <h3 class="kcard-t">{{ $t(f.titleKey) }}</h3>
              <p class="kcard-d">{{ $t(f.descKey) }}</p>
            </article>
          </div>
        </div>
      </section>

      <!-- 软件截图 -->
      <section id="kada-shots" class="ksec">
        <div class="klabel"><span class="klabel-no">02</span>{{ $t('kada.secShots') }}<i class="klabel-line"></i></div>
        <p class="ksec-hint">{{ $t('kada.shotsHint') }}</p>

        <div class="kshots">
          <figure v-for="(s, i) in shots" :key="s.src" class="kshot">
            <div class="kshot-screen">
              <img :src="s.src" :alt="$t(s.titleKey)" width="1440" height="900" loading="lazy" decoding="async" />
              <span class="kshot-glass" aria-hidden="true"></span>
            </div>
            <figcaption class="kshot-cap">
              <span class="kshot-no">0{{ i + 1 }}</span>
              <b>{{ $t(s.titleKey) }}</b>
              <span class="kshot-d">{{ $t(s.descKey) }}</span>
            </figcaption>
          </figure>
        </div>
      </section>

      <!-- 下载 -->
      <section id="kada-download" class="ksec">
        <div class="klabel"><span class="klabel-no">03</span>{{ $t('kada.secDownload') }}<i class="klabel-line"></i></div>
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

      <!-- 首次运行与权限 -->
      <section class="ksec">
        <div class="klabel"><span class="klabel-no">04</span>{{ $t('kada.notesLabel') }}<i class="klabel-line"></i></div>
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
        <span class="ksep" aria-hidden="true">/</span>
        <span>MIT License</span>
        <span class="ksep" aria-hidden="true">/</span>
        <a :href="repoUrl" target="_blank" rel="noopener noreferrer">{{ $t('kada.sourceCode') }}</a>
        <span class="ksep" aria-hidden="true">/</span>
        <span>{{ $t('kada.hostedBy') }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

// ---- 分发渠道 ----
// Windows 绿色版 + 安装版(NSIS) 与 Linux(deb / AppImage) 都发 ihomy 直链
// (放服务器 /opt/ihomy/uploads/kada/,nginx /files/ 反代托管),免登录、免过 GitHub;
// macOS(universal dmg) 本机无 Mac 打不出来,仍指向 GitHub Releases 同 tag 产物。
// ⚠ 换版本时同步:version 常量、下面各包的 meta 体积、服务器上的同名文件。
const version = '1.1.0'
const fileSize = '14.0 MB'
const repoUrl = 'https://github.com/chillyice/Kada'
const releasesUrl = `${repoUrl}/releases`
const assetUrl = (name) => `${repoUrl}/releases/download/v${version}/${name}`
const exeUrl = '/files/kada/kada.exe'
const fileUrl = (name) => `/files/kada/${name}`

const { t } = useI18n()
const rootEl = ref(null)
const THEME_KEY = 'kada.page.crt'

// 初值在 setup 里读（不是 onMounted）：组件首次渲染前就把浅色定下来，
// 否则存过浅色的人会先看到一帧深色再闪成浅色。html 上的类也在这里补，
// 让根元素底色与页面首帧同色。
const light = ref(false)
try {
  light.value = localStorage.getItem(THEME_KEY) === 'light'
} catch {}
document.documentElement.classList.add('kada-page')
document.documentElement.classList.toggle('kada-page-light', light.value)

// 移动端浏览器 / PWA 的地址栏主题色也跟着页面走（ihomy 默认是蓝色），
// 离开页面时还原，别把整站的 meta 留在 CRT 深色上。
const themeColorMeta = document.querySelector('meta[name="theme-color"]')
const prevThemeColor = themeColorMeta?.getAttribute('content') ?? null

function syncThemeColor() {
  if (!themeColorMeta) return
  themeColorMeta.setAttribute('content', light.value ? '#f2f3f5' : '#111318')
}
syncThemeColor()

const ghIcon =
  '<svg viewBox="0 0 16 16"><path fill="currentColor" stroke="none" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.4 7.4 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>'
const dlIcon = '<svg viewBox="0 0 24 24"><path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>'
const sunIcon =
  '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4"/></svg>'
const moonIcon = '<svg viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 0 1 9.5 4a8.2 8.2 0 1 0 10.5 10.5z"/></svg>'

const flowSteps = [
  { key: 'kada.flowGrab', cls: 'st-command' },
  { key: 'kada.flowIf', cls: 'st-if' },
  { key: 'kada.flowCopy', cls: 'st-os' },
  { key: 'kada.flowKeys', cls: 'st-keys' },
]

const stats = [
  { num: '16', labelKey: 'kada.statActions' },
  { num: '3', labelKey: 'kada.statTriggers' },
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
      { labelKey: 'kada.dlWinPortable', meta: fileSize, href: exeUrl, primary: true, local: true },
      { labelKey: 'kada.dlWinSetup', meta: '3.6 MB', href: fileUrl(`Kada_${version}_x64-setup.exe`), local: true },
    ],
  },
  {
    key: 'linux',
    icon: platformIcons.linux,
    nameKey: 'kada.platLinux',
    archKey: 'kada.archLinux',
    downloads: [
      { labelKey: 'kada.dlLinuxAppImage', meta: '83.1 MB', href: fileUrl(`Kada_${version}_amd64.AppImage`), primary: true, local: true },
      { labelKey: 'kada.dlLinuxDeb', meta: '4.6 MB', href: fileUrl(`Kada_${version}_amd64.deb`), local: true },
    ],
  },
  {
    key: 'mac',
    icon: platformIcons.mac,
    nameKey: 'kada.platMac',
    archKey: 'kada.archMac',
    downloads: [
      { labelKey: 'kada.dlMacDmg', meta: 'universal', href: assetUrl(`Kada_${version}_universal.dmg`), primary: true, local: false },
    ],
  },
]

// 截图取自 Kada 仓 ui 的纯浏览器演示模式（1440×900，像素 CRT 深色）；
// 软件界面改了之后重截，覆盖 public/kada-media/ 下的同名文件即可。
const shots = [
  { src: '/kada-media/kada-list.webp', titleKey: 'kada.shot1Title', descKey: 'kada.shot1Desc' },
  { src: '/kada-media/kada-flow.webp', titleKey: 'kada.shot2Title', descKey: 'kada.shot2Desc' },
  { src: '/kada-media/kada-settings.webp', titleKey: 'kada.shot3Title', descKey: 'kada.shot3Desc' },
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
      {
        titleKey: 'kada.f6Title',
        descKey: 'kada.f6Desc',
        tag: 'kada.tagExp',
        icon: '<svg viewBox="0 0 24 24"><path d="M6.5 7v6a4 4 0 0 0 4 4h9.5"/><path d="M17 14l3 3-3 3"/><rect x="4" y="2" width="5" height="5"/><circle cx="20" cy="6" r="1.6"/></svg>',
      },
    ],
  },
  {
    labelKey: 'kada.grpAction',
    items: [
      {
        titleKey: 'kada.f7Title',
        descKey: 'kada.f7Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M6 4h12M6 12h12M6 20h12"/><circle cx="3.5" cy="4" r="1.2"/><circle cx="3.5" cy="12" r="1.2"/><circle cx="3.5" cy="20" r="1.2"/></svg>',
      },
      {
        titleKey: 'kada.f8Title',
        descKey: 'kada.f8Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="12" height="10" rx="1"/><rect x="9" y="11" width="12" height="9" rx="1"/><path d="M6 20h4"/></svg>',
      },
      {
        titleKey: 'kada.f9Title',
        descKey: 'kada.f9Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V2.5h6V4M8.5 10h7M8.5 14h5"/></svg>',
      },
      {
        titleKey: 'kada.f10Title',
        descKey: 'kada.f10Desc',
        icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/><path d="M5 5l2 2M19 5l-2 2"/></svg>',
      },
      {
        titleKey: 'kada.f11Title',
        descKey: 'kada.f11Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M8 4H5v16h3M16 4h3v16h-3"/><path d="M10 12h4"/><circle cx="12" cy="12" r="2.2"/></svg>',
      },
    ],
  },
  {
    labelKey: 'kada.grpConfig',
    items: [
      {
        titleKey: 'kada.f12Title',
        descKey: 'kada.f12Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M9 20h6M12 16v4M6 8h12M6 11h12M6 14h12"/></svg>',
      },
      {
        titleKey: 'kada.f13Title',
        descKey: 'kada.f13Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="12" height="8" rx="1"/><rect x="9" y="10" width="12" height="8" rx="1"/><rect x="5" y="16" width="8" height="5" rx="1"/></svg>',
      },
      {
        titleKey: 'kada.f14Title',
        descKey: 'kada.f14Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z"/><path d="M9 12l2 2 4-4"/></svg>',
      },
      {
        titleKey: 'kada.f15Title',
        descKey: 'kada.f15Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 6h7v12H4zM13 6h7v12h-7z"/><path d="M11 12h2M14 9l3 3-3 3"/></svg>',
      },
      {
        titleKey: 'kada.f16Title',
        descKey: 'kada.f16Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 16V4M8 8l4-4 4 4"/><path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/></svg>',
      },
    ],
  },
]

const notes = [
  { titleKey: 'kada.noteWinTitle', bodyKey: 'kada.noteWinBody' },
  { titleKey: 'kada.noteLinuxTitle', bodyKey: 'kada.noteLinuxBody' },
  { titleKey: 'kada.noteMacTitle', bodyKey: 'kada.noteMacBody' },
  { titleKey: 'kada.noteUpdateTitle', bodyKey: 'kada.noteUpdateBody' },
  { titleKey: 'kada.noteExpTitle', bodyKey: 'kada.noteExpBody' },
]

function syncHtmlTheme() {
  const html = document.documentElement
  html.classList.toggle('kada-page-light', light.value)
}

function toggleTheme() {
  light.value = !light.value
  try {
    localStorage.setItem(THEME_KEY, light.value ? 'light' : 'dark')
  } catch {}
  syncHtmlTheme()
  syncThemeColor()
  // 换主题时来一次显像管「熄一下再亮」的短闪,让切换有个交代。
  const el = rootEl.value
  if (el) {
    el.classList.add('is-switching')
    window.setTimeout(() => el.classList.remove('is-switching'), 260)
  }
}

// 品牌字标：固定节奏的自动演示（与软件本体同一套）——亮一会儿，四字一起按下，
// 再回弹并迸出星屑。纯装饰，不响应鼠标；系统开了「减少动态效果」就整段不跑。
let cycleTimer = 0
let pressTimer = 0
let burstTimer = 0
let logoLoopOn = false

function startLogoLoop() {
  const logos = document.querySelectorAll('.klogo')
  if (!logos.length) return
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  logoLoopOn = true
  const CYCLE_MS = 6000
  const PRESS_MS = 240
  const BURST_MS = 760

  const beat = () => {
    if (!logoLoopOn) return
    logos.forEach((el) => el.classList.add('is-pressed'))
    pressTimer = window.setTimeout(() => {
      logos.forEach((el) => {
        el.classList.remove('is-pressed')
        // 先摘类、强制一次样式计算再挂回，星屑动画才会从头重播。
        el.classList.remove('is-burst')
        void el.offsetWidth
        el.classList.add('is-burst')
      })
      burstTimer = window.setTimeout(() => logos.forEach((el) => el.classList.remove('is-burst')), BURST_MS)
    }, PRESS_MS)
    cycleTimer = window.setTimeout(beat, CYCLE_MS)
  }

  // 首屏先亮 1.5 秒再按第一下，别一进页面就是暗的。
  cycleTimer = window.setTimeout(beat, 1500)

  document.addEventListener('visibilitychange', onVisibility)
}

function onVisibility() {
  if (document.hidden) return
  // 标签页被节流时可能正好卡在「按下」那一帧，回到前台先清中间态。
  document.querySelectorAll('.klogo').forEach((el) => el.classList.remove('is-pressed', 'is-burst'))
}

onMounted(() => {
  document.title = t('kada.docTitle')
  syncHtmlTheme()
  startLogoLoop()
})

onBeforeUnmount(() => {
  logoLoopOn = false
  window.clearTimeout(cycleTimer)
  window.clearTimeout(pressTimer)
  window.clearTimeout(burstTimer)
  document.removeEventListener('visibilitychange', onVisibility)
  document.title = 'ihomy'
  document.documentElement.classList.remove('kada-page', 'kada-page-light')
  if (themeColorMeta && prevThemeColor !== null) themeColorMeta.setAttribute('content', prevThemeColor)
})
</script>

<style scoped>
/* ==========================================================================
   像素 CRT · 深色（默认）/ 浅色
   --------------------------------------------------------------------------
   令牌与 Kada 仓 ui/src/style.css 的 [data-theme="crt"] / [data-crt="light"]
   同值同义；页面自包含，不读 ihomy 的暖居 / 光尘主题变量。
   像素感由「像素字 + 方角 + 硬影 + 扫描线」承载，配色仍是软件那套黑灰 + 橙。
   ========================================================================== */

/* 像素字体是从 Kada 仓 ui/src/assets/fonts/kada-pixel-sc.woff2 子集化来的
   （只保留本页用到的字，609KB → 24KB，OFL 1.1，见同目录 LICENSE-OFL.txt）。
   改完文案若出现缺字，用 fontTools 按页面文本重新子集化。 */
@font-face {
  font-family: 'Kada Pixel';
  src: url('../assets/fonts/kada-pixel-sc.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}

.kada {
  --bg: #111318;
  --panel: #191d24;
  --line: #2a2f3a;
  --line-soft: rgba(255, 255, 255, 0.07);
  --text: #e8eaf0;
  --muted: #8b93a3;
  --accent: #e06040;
  --accent-dim: #7a3a2a;
  --hover-bg: rgba(255, 255, 255, 0.08);
  --key-bg: #262b35;
  --key-line: #3a4150;
  --scroll-thumb: rgba(255, 255, 255, 0.14);
  --scroll-thumb-hi: rgba(255, 255, 255, 0.3);
  --crt-cyan: #63e8ff;
  --crt-blue: #4f9cf9;
  --crt-yellow: #ffd75e;
  --crt-red: #ff6b6b;
  --crt-indigo: #8b9dff;
  /* 页面自用的衍生量：图标底 / 硬影 / 屏幕底色都跟着两档主题走。 */
  --plate: rgba(224, 96, 64, 0.14);
  --plate-line: rgba(224, 96, 64, 0.3);
  --shadow-hard: 4px 4px 0 rgba(0, 0, 0, 0.6);
  --shadow-hard-hi: 6px 6px 0 rgba(0, 0, 0, 0.65);
  --glow-accent: 0 0 18px rgba(224, 96, 64, 0.1);
  --screen-top: #1a1e26;
  --font-pixel: 'Kada Pixel', 'MS Gothic', 'SimSun', monospace;
  --mono: 'Kada Pixel', Consolas, 'Cascadia Mono', monospace;

  position: relative;
  min-height: 100vh;
  color-scheme: dark;
  color: var(--text);
  font: 14px/1.65 var(--font-pixel);
  background:
    radial-gradient(ellipse 1200px 620px at 50% -6%, rgba(224, 96, 64, 0.13), transparent 62%),
    radial-gradient(ellipse 900px 500px at 92% 2%, rgba(99, 232, 255, 0.07), transparent 60%),
    radial-gradient(ellipse at 50% 32%, var(--screen-top) 0%, var(--bg) 74%);
  /* 荧光文本：近圈收边、远圈摊开（与软件本体同口径）。 */
  text-shadow:
    0 0 3px color-mix(in srgb, currentColor 22%, transparent),
    0 0 9px color-mix(in srgb, currentColor 16%, transparent);
  overflow-x: hidden;
}

/* 浅色档：沿用软件 CRT 浅色的白底 + 橙色强调，扫描线改用 lighten。 */
.kada[data-crt='light'] {
  --bg: #f2f3f5;
  --panel: #ffffff;
  --line: #d9dce2;
  --line-soft: rgba(0, 0, 0, 0.08);
  --text: #1c1f26;
  --muted: #626b7a;
  --accent: #d1553a;
  --accent-dim: #f6ddd6;
  --hover-bg: rgba(0, 0, 0, 0.05);
  --key-bg: #e6e9ee;
  --key-line: #c3c9d3;
  --scroll-thumb: rgba(0, 0, 0, 0.16);
  --scroll-thumb-hi: rgba(0, 0, 0, 0.3);
  --crt-cyan: #0a7f96;
  --crt-blue: #2d5fa8;
  --crt-yellow: #8a6400;
  --crt-red: #b23a2e;
  --crt-indigo: #4b57b8;
  --plate: rgba(209, 85, 58, 0.1);
  --plate-line: rgba(209, 85, 58, 0.24);
  --shadow-hard: 4px 4px 0 rgba(0, 0, 0, 0.16);
  --shadow-hard-hi: 6px 6px 0 rgba(0, 0, 0, 0.2);
  --glow-accent: 0 0 18px rgba(209, 85, 58, 0.06);
  --screen-top: #ffffff;

  color-scheme: light;
  background:
    radial-gradient(ellipse 1200px 620px at 50% -6%, rgba(209, 85, 58, 0.07), transparent 62%),
    radial-gradient(ellipse 900px 500px at 92% 2%, rgba(45, 95, 168, 0.05), transparent 60%),
    radial-gradient(ellipse at 50% 32%, var(--screen-top) 0%, var(--bg) 74%);
  text-shadow:
    0 0 2px color-mix(in srgb, currentColor 14%, transparent),
    0 0 7px color-mix(in srgb, currentColor 12%, transparent);
}

/* 根元素负责滚动条与页面外围（独立页没有 ihomy 外壳垫底）。 */
/* 独立页也要压过 ihomy 暖居主题给 html 的米色底（html.theme-warm 同权，
   所以用 :root 提一档）：overscroll / 橡皮筋区才不会露出一圈米色。 */
:global(html.kada-page:root) {
  color-scheme: dark;
  background: #111318;
  /* ihomy 给 html 挂了 1s 的主题过渡：独立页不需要，免得根底色从暖米色慢慢变过来。 */
  transition: none;
}
:global(html.kada-page:root.kada-page-light) {
  color-scheme: light;
  background: #f2f3f5;
}

/* 像素方块：整页去圆角。 */
.kada *,
.kada *::before,
.kada *::after {
  border-radius: 0 !important;
}

.kada ::selection {
  background: rgba(224, 96, 64, 0.35);
  color: var(--text);
}
.kada[data-crt='light'] ::selection {
  background: rgba(209, 85, 58, 0.2);
  color: var(--text);
}

/* ==========================================================================
   CRT 光学层
   ========================================================================== */

.kfx {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

/* 扫描线：color-dodge 只抬亮「发光的内容」，空背景几乎不动。 */
.kfx-scan {
  z-index: 9990;
  background: repeating-linear-gradient(to bottom, #232323 0 1px, rgba(35, 35, 35, 0.62) 1px 2px, transparent 2px 4px);
  mix-blend-mode: color-dodge;
  animation: kflicker 3.4s ease-in-out infinite;
}

/* 纯白文字已经到顶，color-dodge 再加也还是 1.0，补一道 darken 暗缝。 */
.kada[data-crt='dark'] .kfx-darken {
  z-index: 9990;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(140, 140, 140, 0.34) 2px 3px, transparent 3px 4px);
  mix-blend-mode: darken;
}

.kada[data-crt='light'] .kfx-darken {
  display: none;
}

/* 场同步亮带：一道很淡的亮带自上而下扫过。 */
.kfx-sweep {
  inset: -240px 0 auto 0;
  height: 240px;
  z-index: 9989;
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.055), transparent);
  animation: ksweep 7.5s linear infinite;
}
.kada[data-crt='light'] .kfx-sweep {
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.5), transparent);
}

/* 暗角：压住屏幕边缘。 */
.kfx-vignette {
  z-index: 9991;
  background: radial-gradient(ellipse at 50% 50%, transparent 70%, rgba(0, 0, 0, 0.16) 100%);
}
.kada[data-crt='light'] .kfx-vignette {
  background: radial-gradient(ellipse at 50% 50%, transparent 74%, rgba(0, 0, 0, 0.04) 100%);
}

.kada[data-crt='light'] .kfx-scan {
  background: repeating-linear-gradient(to bottom, rgba(115, 115, 115, 0.34) 0 1px, rgba(115, 115, 115, 0.18) 1px 2px, transparent 2px 4px);
  mix-blend-mode: lighten;
}

/* 换主题时显像管短闪。 */
.kada.is-switching {
  animation: kswitch 260ms steps(2, end);
}

@keyframes kflicker {
  0%,
  100% {
    opacity: 0.92;
  }
  46% {
    opacity: 0.88;
  }
  47% {
    opacity: 1;
  }
  49% {
    opacity: 0.9;
  }
}
@keyframes ksweep {
  to {
    transform: translateY(calc(100vh + 480px));
  }
}
@keyframes kswitch {
  0% {
    opacity: 1;
  }
  40% {
    opacity: 0.55;
  }
  70% {
    opacity: 0.92;
  }
  100% {
    opacity: 1;
  }
}

/* ==========================================================================
   布局
   ========================================================================== */

.kwrap {
  position: relative;
  z-index: 2;
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 24px;
}

.ksec {
  padding: 44px 0 0;
  scroll-margin-top: 24px;
}

/* ---------- 顶栏 ---------- */
.ktop {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 0 6px;
}

.kbrand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: var(--text);
  flex: none;
}

.kbrand-cn {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.knav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.knav a {
  color: var(--muted);
  text-decoration: none;
  font-size: 13px;
  padding: 5px 9px;
  border: 1px solid transparent;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.knav a:hover {
  color: var(--text);
  border-color: var(--line);
  background: var(--hover-bg);
}

.kact {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.kicon-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--line);
  background: var(--panel);
  color: var(--muted);
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    transform 0.15s ease;
}
.kicon-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
  transform: translateY(-1px);
}
.kicon-btn span,
.kicon-btn :deep(svg) {
  width: 16px;
  height: 16px;
  display: block;
}

.kgh {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: none;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    transform 0.15s ease;
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
  position: relative;
  text-align: center;
  padding: 54px 0 34px;
}

/* 像素网格底：只在主视觉里淡淡透出来，四周用遮罩收掉。 */
.khero::before {
  content: '';
  position: absolute;
  inset: -6% -12% 0;
  z-index: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(99, 232, 255, 0.055) 1px, transparent 1px), linear-gradient(90deg, rgba(99, 232, 255, 0.055) 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(ellipse 62% 58% at 50% 42%, #000 0%, transparent 72%);
  -webkit-mask-image: radial-gradient(ellipse 62% 58% at 50% 42%, #000 0%, transparent 72%);
}
.khero > * {
  position: relative;
  z-index: 1;
}
.kada[data-crt='light'] .khero::before {
  background-image:
    linear-gradient(rgba(45, 95, 168, 0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(45, 95, 168, 0.06) 1px, transparent 1px);
}

.kterm {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 30px;
  padding: 7px 13px;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-hard);
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 0.02em;
}
.kterm-prompt {
  color: var(--accent);
}
.kterm-cmd {
  color: var(--text);
}
.kcaret {
  width: 7px;
  height: 13px;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
  animation: kcaret 1.1s steps(1, end) infinite;
}
@keyframes kcaret {
  0%,
  55% {
    opacity: 1;
  }
  56%,
  100% {
    opacity: 0;
  }
}

.khero-title {
  margin: 0;
  line-height: 1;
  display: flex;
  justify-content: center;
}

.khero-cn {
  margin: 22px 0 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--text);
}
.khero-cn::before,
.khero-cn::after {
  content: '';
  display: inline-block;
  width: 26px;
  height: 1px;
  background: var(--line);
  vertical-align: middle;
  margin: 0 12px;
}

.ksub {
  max-width: 700px;
  margin: 10px auto 0;
  font-size: 14px;
  color: var(--muted);
  line-height: 1.8;
}

/* ---------- 动作链示意 ---------- */
.kflow {
  display: inline-flex;
  flex-direction: column;
  max-width: 100%;
  margin-top: 30px;
  padding: 0;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-hard), var(--glow-accent);
  text-align: left;
}

.kflow-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 7px 12px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.025);
}
.kada[data-crt='light'] .kflow-bar {
  background: rgba(0, 0, 0, 0.025);
}
.kflow-cap {
  font-size: 11.5px;
  color: var(--muted);
  letter-spacing: 0.06em;
}
.kflow-id {
  font-size: 11px;
  color: var(--accent);
  letter-spacing: 0.14em;
}

.kflow-row {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
  padding: 13px 14px;
}
.kflow-keys {
  display: inline-flex;
  gap: 3px;
}
.kflow-keys kbd {
  font-family: var(--mono);
  font-size: 11.5px;
  line-height: 1.5;
  padding: 0 6px;
  color: var(--text);
  background: var(--key-bg);
  border: 1px solid var(--key-line);
  border-bottom-width: 3px;
}
.kflow-arrow {
  color: var(--muted);
  opacity: 0.6;
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
  border: 2px solid var(--muted);
  box-sizing: content-box;
}
.kflow-dot.st-command {
  border-color: var(--crt-blue);
}
.kflow-dot.st-if {
  border-color: var(--crt-yellow);
}
.kflow-dot.st-os {
  border-color: #4ec97a;
}
.kflow-dot.st-keys {
  border-color: var(--accent);
}
.kflow-done {
  color: #4ec97a;
  font-size: 11.5px;
  letter-spacing: 0.08em;
}

/* ---------- CTA ---------- */
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
  border: 1px solid transparent;
  font-family: inherit;
  font-size: 14.5px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition:
    transform 0.14s ease,
    box-shadow 0.14s ease,
    filter 0.14s ease,
    border-color 0.14s ease;
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
  stroke-linecap: square;
  stroke-linejoin: miter;
}

.kbtn-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
  box-shadow:
    4px 4px 0 rgba(0, 0, 0, 0.55),
    0 0 22px rgba(224, 96, 64, 0.25);
}
.kbtn-primary:hover {
  transform: translate(-2px, -2px);
  filter: brightness(1.06);
  box-shadow:
    6px 6px 0 rgba(0, 0, 0, 0.6),
    0 0 26px rgba(224, 96, 64, 0.32);
}
.kada[data-crt='light'] .kbtn-primary {
  box-shadow:
    4px 4px 0 rgba(0, 0, 0, 0.18),
    0 0 22px rgba(209, 85, 58, 0.12);
}
.kada[data-crt='light'] .kbtn-primary:hover {
  box-shadow:
    6px 6px 0 rgba(0, 0, 0, 0.22),
    0 0 26px rgba(209, 85, 58, 0.18);
}

.kbtn-ghost {
  background: var(--panel);
  border-color: var(--line);
  color: var(--text);
}
.kbtn-ghost:hover {
  transform: translate(-2px, -2px);
  border-color: var(--muted);
  box-shadow: var(--shadow-hard);
}

.kmeta {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
  flex-wrap: wrap;
}
.kchip {
  font-size: 12px;
  color: var(--muted);
  padding: 3px 10px;
  background: var(--panel);
  border: 1px solid var(--line);
}

/* ---------- 能力速览 ---------- */
.kstats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 34px;
}
.kstat {
  padding: 15px 12px;
  text-align: center;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-hard);
}
.kstat-n {
  font-family: var(--mono);
  font-size: 27px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--accent);
  text-shadow:
    0 0 4px color-mix(in srgb, var(--accent) 60%, transparent),
    0 0 14px color-mix(in srgb, var(--accent) 30%, transparent);
}
.kstat-l {
  font-size: 11.5px;
  color: var(--muted);
  letter-spacing: 0.04em;
}

/* ---------- 分区标题 ---------- */
.klabel {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.klabel-no {
  color: var(--accent);
  font-size: 12px;
  letter-spacing: 0.16em;
}
.klabel-line {
  flex: 1;
  height: 1px;
  margin-left: 4px;
  background: linear-gradient(90deg, var(--line), transparent);
}
.ksec-hint {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--muted);
}

/* ---------- 功能卡片 ---------- */
.kgroup + .kgroup {
  margin-top: 26px;
}
.kgroup-label {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--accent);
}
.kgroup-label::before {
  content: '>';
  margin-right: 7px;
  opacity: 0.65;
}
.kgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(268px, 1fr));
  gap: 12px;
}

.kcard {
  display: flex;
  flex-direction: column;
  padding: 17px 16px 15px;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-hard);
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}
.kcard:hover {
  transform: translate(-2px, -2px);
  border-color: var(--accent);
  box-shadow: var(--shadow-hard-hi), var(--glow-accent);
}
.kcard-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 11px;
}
.kcard-ic {
  width: 34px;
  height: 34px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--plate);
  border: 1px solid var(--plate-line);
}
.kcard-ic :deep(svg) {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.7;
  stroke-linecap: square;
  stroke-linejoin: miter;
}
.ktag-exp {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--crt-yellow);
  border: 1px solid color-mix(in srgb, var(--crt-yellow) 45%, transparent);
  padding: 1px 6px;
  white-space: nowrap;
}
.kcard-t {
  margin: 0 0 6px;
  font-size: 14.5px;
  font-weight: 700;
}
.kcard-d {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--muted);
}

/* ---------- 截图 ---------- */
.kshots {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.kshot:first-child {
  grid-column: 1 / -1;
}

.kshot {
  margin: 0;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-hard);
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}
.kshot:hover {
  transform: translate(-2px, -2px);
  border-color: var(--accent);
  box-shadow: var(--shadow-hard-hi), var(--glow-accent);
}
.kshot-screen {
  position: relative;
  background: #0b0d11;
  border-bottom: 1px solid var(--line);
  overflow: hidden;
}
.kshot-screen img {
  display: block;
  width: 100%;
  height: auto;
}
/* 截图本身也吃一层扫描线，跟页面是同一块显像管。 */
.kshot-glass {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, rgba(0, 0, 0, 0.16) 0 1px, transparent 1px 3px);
  mix-blend-mode: multiply;
}
.kshot-cap {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 11px 14px;
  font-size: 12.5px;
  color: var(--muted);
  flex-wrap: wrap;
}
.kshot-no {
  color: var(--accent);
  font-size: 11px;
  letter-spacing: 0.14em;
}
.kshot-cap b {
  color: var(--text);
  font-size: 13px;
}
.kshot-d {
  color: var(--muted);
}
.kshot-d::before {
  content: '// ';
  opacity: 0.55;
}

/* ---------- 下载 ---------- */
.kplat {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.kplat-card {
  display: flex;
  flex-direction: column;
  padding: 18px 16px 16px;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-hard);
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}
.kplat-card:hover {
  transform: translate(-2px, -2px);
  border-color: var(--accent);
  box-shadow: var(--shadow-hard-hi), var(--glow-accent);
}
.kplat-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.kplat-ic {
  width: 38px;
  height: 38px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--plate);
  border: 1px solid var(--plate-line);
}
.kplat-ic :deep(svg) {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.7;
  stroke-linecap: square;
  stroke-linejoin: miter;
}
.kplat-titles {
  min-width: 0;
}
.kplat-name {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}
.kplat-arch {
  margin: 1px 0 0;
  font-size: 11.5px;
  color: var(--muted);
}
.kplat-dls {
  list-style: none;
  margin: 15px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.kdl {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  background: var(--bg);
  border: 1px solid var(--line);
  color: var(--text);
  font-size: 12.5px;
  font-weight: 600;
  text-decoration: none;
  transition:
    border-color 0.14s ease,
    background 0.14s ease,
    color 0.14s ease;
}
.kdl:hover {
  border-color: var(--accent);
  background: var(--hover-bg);
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
  font-size: 11px;
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
  background: var(--accent);
  border-color: var(--accent);
  filter: brightness(1.06);
}
.krelease {
  margin: 14px 0 0;
  font-size: 12.5px;
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

/* ---------- 首次运行与权限 ---------- */
.knotes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}
.knote {
  padding: 15px 16px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-left: 3px solid var(--accent);
}
.knote-t {
  margin-bottom: 5px;
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
}
.knote-b {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--muted);
}

/* ---------- 页脚 ---------- */
.kfoot {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 44px;
  padding: 20px 0 42px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
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
  opacity: 0.45;
}

/* ==========================================================================
   品牌字标（与软件本体同一套动效）
   --------------------------------------------------------------------------
   四色 K → a → d → a，静止时按 K → d → 左 a → 右 a 硬切微闪；
   每 6 秒四字一起按下、回弹并迸出三颗星与三根短射线。
   星屑层是字标的兄弟节点，不跟着 skewX 斜切；所有几何都用 em，
   所以同一份规则既撑得起顶栏 21px 的小字标，也撑得起主视觉的大字标。
   ========================================================================== */

.klogo {
  position: relative;
  display: inline-flex;
  align-items: baseline;
  font-size: var(--logo-fs, 21px);
  line-height: 1;
  color: var(--accent);
  user-select: none;
  text-shadow:
    0 0 2px var(--accent),
    0 0 12px color-mix(in srgb, var(--accent) 55%, transparent);
}
.klogo-sm {
  --logo-fs: 21px;
}
.klogo-lg {
  --logo-fs: clamp(60px, 9.4vw, 104px);
}

.kword {
  display: inline-flex;
  align-items: baseline;
  white-space: nowrap;
  transform: skewX(-6deg);
}

/* 四色字母：颜色 / 闪烁相位走变量，字本身只写一份规则。
   --flick-delay 按 K → d → 左 a → 右 a 递增 90ms = 一次「快速衔接」的霓虹抽搐。 */
.bw-cyan {
  --bw-c: var(--crt-cyan);
  --flick-delay: 0ms;
}
.bw-red {
  --bw-c: var(--crt-red);
  --flick-delay: 90ms;
}
.bw-yellow {
  --bw-c: var(--crt-yellow);
  --flick-delay: 180ms;
}
.bw-indigo {
  --bw-c: var(--crt-indigo);
  --flick-delay: 270ms;
}

.bw {
  position: relative;
  color: var(--bw-c);
  /* 三层辉光：近圈收边、中圈摊开、外圈留远光。模糊半径用 em，随字号一起放大。 */
  text-shadow:
    0 0 0.11em var(--bw-c),
    0 0 0.44em color-mix(in srgb, var(--bw-c) 80%, transparent),
    0 0 0.9em color-mix(in srgb, var(--bw-c) 40%, transparent);
  transition:
    transform 190ms cubic-bezier(0.2, 1.7, 0.45, 1),
    color 130ms ease-out,
    filter 130ms ease-out,
    text-shadow 90ms linear;
  animation: kneon-idle 9s steps(1, end) infinite;
  animation-delay: var(--flick-delay, 0s);
}

/* 按下：四字同步沉进屏幕一点点 + 摘掉辉光（别压太深，否则像素字会糊）。 */
.klogo.is-pressed .bw {
  transform: translateY(0.03em) scale(0.9);
  color: color-mix(in srgb, var(--bw-c) 38%, var(--panel));
  filter: saturate(0.75);
  text-shadow: none;
  transition-duration: 80ms, 80ms, 80ms, 60ms;
  transition-timing-function: ease-out;
}

/* 星屑层：钉在最后一个 a 的右上角。字体 18px 时是 top 10px / right 0，
   这里换成 0.555em；right 让出斜切带来的约 0.05em 位移。 */
.kburst {
  position: absolute;
  top: 0.555em;
  right: -0.055em;
  width: 0;
  height: 0;
  pointer-events: none;
  color: var(--crt-yellow);
}
.kburst .bz {
  position: absolute;
  display: block;
  opacity: 0;
  font-style: normal;
}
.bz-star {
  background: currentColor;
  filter: drop-shadow(0 0 0.11em currentColor) drop-shadow(0 0 0.28em color-mix(in srgb, currentColor 65%, transparent));
}

/* 三颗星：0.5 / 0.556 / 0.389em，4 角 / 5 角 / 4 角，起点贴角 3~4.8px（18px 基准）。 */
.bz-s1 {
  left: -0.092em;
  top: -0.302em;
  width: 0.5em;
  height: 0.5em;
  --dx: 0.317em;
  --dy: -0.103em;
  clip-path: polygon(50% 0%, 59% 41%, 100% 50%, 59% 59%, 50% 100%, 41% 59%, 0% 50%, 41% 41%);
  animation-duration: 470ms;
  animation-delay: 0ms;
}
.bz-s2 {
  left: -0.128em;
  top: -0.457em;
  width: 0.556em;
  height: 0.556em;
  --dx: 0.296em;
  --dy: -0.353em;
  /* 一颗冷色星，免得右上角糊成一片黄。 */
  color: var(--crt-cyan);
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
  animation-duration: 560ms;
  animation-delay: 90ms;
}
.bz-s3 {
  left: -0.171em;
  top: -0.46em;
  width: 0.389em;
  height: 0.389em;
  --dx: 0.028em;
  --dy: -0.316em;
  color: color-mix(in srgb, var(--crt-yellow) 55%, #fff);
  clip-path: polygon(50% 0%, 59% 41%, 100% 50%, 59% 59%, 50% 100%, 41% 59%, 0% 50%, 41% 41%);
  animation-duration: 440ms;
  animation-delay: 180ms;
}

/* 三根放射线：起点沿各自方向错开，都落在以角为心的三条射线上，互不相交；长度不统一。 */
.bz-ray {
  height: max(1px, 0.05em);
  background: linear-gradient(
    90deg,
    transparent,
    color-mix(in srgb, currentColor 70%, #fff) 30%,
    color-mix(in srgb, currentColor 70%, #fff)
  );
  transform-origin: 0 50%;
  box-shadow:
    0 0 0.22em color-mix(in srgb, currentColor 75%, transparent),
    0 0 0.44em color-mix(in srgb, currentColor 35%, transparent);
}
.bz-r1 {
  --ang: -20deg;
  left: 0.026em;
  top: -0.009em;
  width: 0.256em;
  animation-duration: 340ms;
  animation-delay: 0ms;
}
.bz-r2 {
  --ang: -50deg;
  left: 0.039em;
  top: -0.047em;
  width: 0.2em;
  animation-duration: 280ms;
  animation-delay: 55ms;
}
.bz-r3 {
  --ang: -80deg;
  left: 0.007em;
  top: -0.038em;
  width: 0.228em;
  animation-duration: 320ms;
  animation-delay: 105ms;
}

/* 自动循环里进入迸射阶段时才让星屑真的动起来。 */
.klogo.is-burst .bz-star {
  animation-name: kstar-fly;
  animation-timing-function: cubic-bezier(0.15, 0.75, 0.3, 1);
  animation-fill-mode: both;
}
.klogo.is-burst .bz-ray {
  animation-name: kray-flash;
  animation-timing-function: ease-out;
  animation-fill-mode: both;
}

/* 霓虹微闪：每个周期两次「两连熄」+ 一次单熄，都是几十毫秒的硬切。 */
@keyframes kneon-idle {
  0%,
  27.5% {
    opacity: 1;
  }
  28% {
    opacity: 0.08;
  }
  28.8% {
    opacity: 1;
  }
  29.6% {
    opacity: 0.14;
  }
  30.6% {
    opacity: 1;
  }
  60% {
    opacity: 1;
  }
  60.7% {
    opacity: 0.1;
  }
  61.7% {
    opacity: 1;
  }
  100% {
    opacity: 1;
  }
}
@keyframes kstar-fly {
  0% {
    opacity: 0;
    transform: translate(0, 0) scale(0.2) rotate(0deg);
  }
  18% {
    opacity: 1;
    transform: translate(calc(var(--dx) * 0.4), calc(var(--dy) * 0.4)) scale(1) rotate(40deg);
  }
  100% {
    opacity: 0;
    transform: translate(var(--dx), var(--dy)) scale(0.5) rotate(110deg);
  }
}
@keyframes kray-flash {
  0% {
    opacity: 0;
    transform: rotate(var(--ang)) scaleX(0.25);
  }
  16% {
    opacity: 1;
    transform: rotate(var(--ang)) scaleX(1);
  }
  50% {
    opacity: 0.85;
  }
  100% {
    opacity: 0;
    transform: rotate(var(--ang)) scaleX(1.2);
  }
}

/* 浅色档：深色字在亮底上辉光要收一点，按下时往暗里混（混白会看不见）。 */
.kada[data-crt='light'] .klogo {
  text-shadow:
    0 0 1px var(--accent),
    0 0 8px color-mix(in srgb, var(--accent) 35%, transparent);
}
.kada[data-crt='light'] .bw {
  text-shadow:
    0 0 2px color-mix(in srgb, var(--bw-c) 55%, transparent),
    0 0 8px color-mix(in srgb, var(--bw-c) 28%, transparent);
}
.kada[data-crt='light'] .klogo.is-pressed .bw {
  color: color-mix(in srgb, var(--bw-c) 62%, var(--text));
  text-shadow: none;
}
.kada[data-crt='light'] .bz-star {
  filter: drop-shadow(0 0 1.5px color-mix(in srgb, currentColor 60%, transparent))
    drop-shadow(0 0 4px color-mix(in srgb, currentColor 30%, transparent));
}
.kada[data-crt='light'] .bz-s3 {
  color: var(--crt-yellow);
}

/* ==========================================================================
   响应式
   ========================================================================== */

@media (max-width: 900px) {
  .knav {
    display: none;
  }
  .kact {
    margin-left: auto;
  }
  .kplat {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .kwrap {
    padding: 0 16px;
  }
  .khero {
    padding: 38px 0 26px;
  }
  .khero-cn {
    font-size: 18px;
  }
  .khero-cn::before,
  .khero-cn::after {
    width: 16px;
    margin: 0 8px;
  }
  .kstats {
    grid-template-columns: 1fr 1fr;
  }
  .kshots {
    grid-template-columns: 1fr;
  }
  .kshot:first-child {
    grid-column: auto;
  }
  .kflow-row {
    gap: 7px;
  }
  .kterm {
    margin-bottom: 22px;
  }
}

@media (max-width: 460px) {
  .kgrid {
    grid-template-columns: 1fr;
  }
  .kbrand-cn {
    display: none;
  }
  .kgh {
    padding: 0 9px;
  }
}

/* 老显像管对部分人不友好：开了「减少动态效果」就只留静止扫描线。 */
@media (prefers-reduced-motion: reduce) {
  .kfx-scan,
  .kfx-sweep,
  .kcaret,
  .bw {
    animation: none;
  }
  .kfx-sweep {
    display: none;
  }
  .klogo.is-burst .bz {
    animation: none;
  }
  .klogo.is-pressed .bw {
    transform: none;
  }
  .kada.is-switching {
    animation: none;
  }
}
</style>
