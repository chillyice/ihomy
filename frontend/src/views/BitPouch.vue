<!-- bit_pouch 发布页 —— 独立产品页,托管在 ihomy 域名 https://ihomy.top/bit-pouch。
     配色与控件走 bit_pouch 自己的像素麻袋品牌(深色为默认,浅色跟随浏览器偏好),
     页内自包含、不随 ihomy 的暖居/光尘主题变化;路由 meta.standalone 让 App.vue 不套
     ihomy 外壳、直接全屏渲染,也不初始化家庭数据。 -->
<template>
  <div class="bp">
    <div class="bpwrap">
      <!-- 顶栏:品牌 + GitHub 入口 -->
      <header class="bptop">
        <div class="bpbrand">
          <div class="bpmark" aria-hidden="true" v-html="pouchMark"></div>
          <span class="bpbrand-cn">{{ $t('bitPouch.brand') }}</span>
          <span class="bpbrand-en">bit_pouch</span>
        </div>
        <a class="bpgh" :href="repoUrl" target="_blank" rel="noopener noreferrer" :aria-label="$t('bitPouch.ghAria')">
          <span class="bpgh-ic" aria-hidden="true" v-html="ghIcon"></span>
          GitHub
        </a>
      </header>

      <!-- 主视觉 -->
      <section class="bpheroc">
        <div class="bpheromark" aria-hidden="true" v-html="pouchMark"></div>
        <h1 class="bptitle">{{ $t('bitPouch.brand') }}<span class="bptitle-en">bit_pouch</span></h1>
        <p class="bptag">{{ $t('bitPouch.tag') }}</p>
        <p class="bpsub">{{ $t('bitPouch.sub') }}</p>

        <!-- 一次命令打开一个工具:命令面板 → 打开插件 → 输入即算 → 复制结果 -->
        <div class="bpflow">
          <span class="bpflow-cap">{{ $t('bitPouch.flowCaption') }}</span>
          <span class="bpflow-row">
            <span class="bpflow-bolt" aria-hidden="true">⚡</span>
            <span class="bpflow-keys" aria-hidden="true"><kbd>Ctrl</kbd><kbd>K</kbd></span>
            <span class="bpflow-arrow" aria-hidden="true">→</span>
            <span v-for="(s, i) in flowSteps" :key="s.key" class="bpflow-step">
              <i class="bpflow-dot" :class="s.cls" aria-hidden="true"></i>{{ $t(s.key) }}
              <span v-if="i < flowSteps.length - 1" class="bpflow-arrow" aria-hidden="true">→</span>
            </span>
            <span class="bpflow-done" aria-hidden="true">✓</span>
          </span>
        </div>

        <div class="bpcta">
          <a class="bpbtn bpbtn-primary" :href="downloadUrl(primaryAsset)" download>
            <span class="bpbtn-ic" aria-hidden="true" v-html="dlIcon"></span>
            {{ $t('bitPouch.ctaDownload') }}
          </a>
          <a class="bpbtn bpbtn-ghost" href="#bitpouch-download">
            {{ $t('bitPouch.ctaPlatforms') }}
          </a>
        </div>

        <div class="bpmeta">
          <span class="bpchip">v{{ version }}</span>
          <span class="bpchip">{{ $t('bitPouch.chipPlatforms') }}</span>
          <span class="bpchip">{{ $t('bitPouch.chipTools') }}</span>
          <span class="bpchip">{{ $t('bitPouch.license') }}</span>
        </div>
      </section>

      <!-- 能力速览 -->
      <section class="bpstats">
        <div v-for="s in stats" :key="s.labelKey" class="bpstat">
          <div class="bpstat-n">{{ s.num }}</div>
          <div class="bpstat-l">{{ $t(s.labelKey) }}</div>
        </div>
      </section>

      <!-- 下载 -->
      <section id="bitpouch-download" class="bpsec">
        <div class="bplabel">{{ $t('bitPouch.secDownload') }}</div>
        <p class="bpsec-hint">{{ $t('bitPouch.dlHint') }}</p>

        <div class="bpplat">
          <article v-for="p in platforms" :key="p.key" class="bpplat-card">
            <div class="bpplat-head">
              <span class="bpplat-ic" aria-hidden="true" v-html="p.icon"></span>
              <div class="bpplat-titles">
                <h3 class="bpplat-name">{{ $t(p.nameKey) }}</h3>
                <p class="bpplat-arch">{{ $t(p.archKey) }}</p>
              </div>
            </div>
            <ul class="bpplat-dls">
              <li v-for="d in p.downloads" :key="d.labelKey">
                <a
                  class="bpdl"
                  :class="{ 'bpdl-primary': d.primary }"
                  :href="downloadUrl(d.asset)"
                  :download="d.asset ? '' : undefined"
                  :target="d.asset ? undefined : '_blank'"
                  :rel="d.asset ? undefined : 'noopener noreferrer'"
                >
                  <span class="bpdl-ic" aria-hidden="true" v-html="d.icon || dlIcon"></span>
                  <span class="bpdl-label">{{ $t(d.labelKey) }}</span>
                  <span class="bpdl-meta">{{ d.meta }}</span>
                </a>
              </li>
            </ul>
          </article>
        </div>

        <p class="bprelease">
          {{ $t('bitPouch.releaseHint') }}
          <a :href="releasesUrl" target="_blank" rel="noopener noreferrer"> GitHub Releases </a>
        </p>
      </section>

      <!-- 功能 -->
      <section class="bpsec">
        <div class="bplabel">{{ $t('bitPouch.secFeatures') }}</div>
        <p class="bpsec-hint">{{ $t('bitPouch.featHint') }}</p>

        <div v-for="g in featureGroups" :key="g.labelKey" class="bpgroup">
          <div class="bpgroup-label">{{ $t(g.labelKey) }}</div>
          <div class="bpgrid">
            <div v-for="f in g.items" :key="f.titleKey" class="bpcard">
              <div class="bpcard-ic" aria-hidden="true" v-html="f.icon"></div>
              <h3 class="bpcard-t">{{ $t(f.titleKey) }}</h3>
              <p class="bpcard-d">{{ $t(f.descKey) }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 首次运行与更新 -->
      <section class="bpsec">
        <div class="bplabel">{{ $t('bitPouch.notesLabel') }}</div>
        <div class="bpnotes">
          <div v-for="n in notes" :key="n.titleKey" class="bpnote">
            <div class="bpnote-t">{{ $t(n.titleKey) }}</div>
            <p class="bpnote-b">{{ $t(n.bodyKey) }}</p>
          </div>
        </div>
      </section>

      <!-- 页脚 -->
      <footer class="bpfoot">
        <span>{{ $t('bitPouch.footer', { version }) }}</span>
        <span class="bpsep" aria-hidden="true">·</span>
        <span>{{ $t('bitPouch.license') }}</span>
        <span class="bpsep" aria-hidden="true">·</span>
        <a :href="repoUrl" target="_blank" rel="noopener noreferrer">
          {{ $t('bitPouch.sourceCode') }}
        </a>
        <span class="bpsep" aria-hidden="true">·</span>
        <span>{{ $t('bitPouch.hostedBy') }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'

// ---- 分发渠道 ----
// 安装包由 bit_pouch 仓的 .github/workflows/release.yml 构建,发版后由服务器上的
// mirror-bit-pouch 每日从 GitHub 最新已发布 Release 同步到
// /opt/ihomy/uploads/bit_pouch/,nginx /files/ 反代托管;页面统一走 ihomy 直链,
// GitHub Releases 作为备用入口。换版本时同步 version 常量,并确认该 tag 已发布
// (默认建 draft,不发布则镜像与下载链接都取不到)。
const version = '0.2.0'
const repoUrl = 'https://github.com/chillyice/bit_pouch'
const releasesUrl = `${repoUrl}/releases`
const fileUrl = (name) => `/files/bit_pouch/${name}`

// 主 CTA:Windows 安装版(与下载区第一项一致)
const primaryAsset = `bit_pouch_${version}_x64-setup.exe`
const downloadUrl = (asset) => fileUrl(asset)

const pouchMark = `<svg viewBox="0 0 32 32" shape-rendering="crispEdges">
<rect width="32" height="32" rx="7" fill="#0a0d0b"/>
<rect x="13" y="6" width="4" height="4" fill="#b8ff3d" opacity=".14"/>
<rect x="17" y="4" width="3" height="3" fill="#5eead4" opacity=".16"/>
<rect x="11" y="3" width="3" height="3" fill="#5eead4" opacity=".16"/>
<rect x="15" y="1" width="3" height="3" fill="#facc15" opacity=".16"/>
<rect x="20" y="7" width="3" height="3" fill="#b8ff3d" opacity=".16"/>
<rect x="10" y="10" width="2" height="1" fill="#b08e5f"/>
<rect x="12" y="9" width="1" height="1" fill="#b08e5f"/>
<rect x="13" y="10" width="1" height="1" fill="#b08e5f"/>
<rect x="15" y="9" width="1" height="1" fill="#b08e5f"/>
<rect x="16" y="10" width="1" height="1" fill="#b08e5f"/>
<rect x="18" y="9" width="1" height="1" fill="#b08e5f"/>
<rect x="19" y="10" width="1" height="1" fill="#b08e5f"/>
<rect x="20" y="10" width="2" height="1" fill="#b08e5f"/>
<rect x="14" y="10" width="1" height="1" fill="#4a3820"/>
<rect x="17" y="10" width="1" height="1" fill="#4a3820"/>
<rect x="12" y="11" width="9" height="1" fill="#4a3820"/>
<rect x="11" y="12" width="11" height="1" fill="#4a3820"/>
<rect x="14" y="12" width="3" height="1" fill="#b8ff3d"/>
<rect x="10" y="13" width="13" height="1" fill="#d9b98a"/>
<rect x="10" y="13" width="1" height="1" fill="#efd3a6"/>
<rect x="22" y="13" width="1" height="1" fill="#efd3a6"/>
<rect x="9" y="13" width="1" height="1" fill="#6b4f2a"/>
<rect x="23" y="13" width="1" height="1" fill="#6b4f2a"/>
<rect x="10" y="14" width="13" height="1" fill="#6b4f2a"/>
<rect x="12" y="14" width="1" height="1" fill="#b8ff3d"/>
<rect x="11" y="15" width="1" height="1" fill="#6b4f2a"/>
<rect x="23" y="15" width="1" height="2" fill="#6b4f2a"/>
<rect x="10" y="15" width="13" height="1" fill="#d9b98a"/>
<rect x="9" y="16" width="15" height="1" fill="#d9b98a"/>
<rect x="8" y="17" width="16" height="1" fill="#d9b98a"/>
<rect x="7" y="18" width="17" height="1" fill="#d9b98a"/>
<rect x="7" y="19" width="18" height="1" fill="#d9b98a"/>
<rect x="6" y="20" width="18" height="1" fill="#d9b98a"/>
<rect x="7" y="21" width="17" height="1" fill="#d9b98a"/>
<rect x="8" y="22" width="16" height="1" fill="#d9b98a"/>
<rect x="8" y="23" width="15" height="1" fill="#d9b98a"/>
<rect x="9" y="24" width="14" height="1" fill="#d9b98a"/>
<rect x="10" y="25" width="13" height="1" fill="#d9b98a"/>
<rect x="11" y="26" width="11" height="1" fill="#d9b98a"/>
<rect x="12" y="27" width="10" height="1" fill="#d9b98a"/>
<rect x="14" y="28" width="5" height="1" fill="#d9b98a"/>
<rect x="12" y="15" width="9" height="1" fill="#b08e5f"/>
<rect x="23" y="16" width="1" height="3" fill="#b08e5f"/>
<rect x="24" y="19" width="1" height="1" fill="#b08e5f"/>
<rect x="23" y="20" width="1" height="3" fill="#b08e5f"/>
<rect x="22" y="23" width="1" height="3" fill="#b08e5f"/>
<rect x="11" y="26" width="11" height="1" fill="#b08e5f"/>
<rect x="12" y="27" width="10" height="1" fill="#a37f52"/>
<rect x="14" y="28" width="5" height="1" fill="#a37f52"/>
<rect x="9" y="16" width="1" height="1" fill="#efd3a6"/>
<rect x="8" y="17" width="1" height="1" fill="#efd3a6"/>
<rect x="7" y="18" width="1" height="1" fill="#efd3a6"/>
<rect x="7" y="19" width="1" height="1" fill="#c4a373"/>
<rect x="6" y="20" width="1" height="1" fill="#efd3a6"/>
<rect x="7" y="21" width="1" height="1" fill="#c4a373"/>
<rect x="8" y="22" width="1" height="1" fill="#efd3a6"/>
<rect x="13" y="16" width="1" height="1" fill="#b08e5f"/>
<rect x="12" y="17" width="1" height="1" fill="#c4a373"/>
<rect x="11" y="18" width="1" height="1" fill="#b08e5f"/>
<rect x="10" y="19" width="1" height="1" fill="#c4a373"/>
<rect x="10" y="20" width="1" height="1" fill="#b08e5f"/>
<rect x="11" y="21" width="1" height="1" fill="#c4a373"/>
<rect x="18" y="16" width="1" height="1" fill="#b08e5f"/>
<rect x="19" y="17" width="1" height="1" fill="#c4a373"/>
<rect x="19" y="18" width="1" height="1" fill="#b08e5f"/>
<rect x="20" y="19" width="1" height="1" fill="#c4a373"/>
<rect x="19" y="20" width="1" height="1" fill="#b08e5f"/>
<rect x="19" y="21" width="1" height="1" fill="#c4a373"/>
<rect x="15" y="17" width="1" height="1" fill="#b08e5f"/>
<rect x="14" y="18" width="1" height="1" fill="#c4a373"/>
<rect x="14" y="19" width="1" height="1" fill="#b08e5f"/>
<rect x="15" y="20" width="1" height="1" fill="#c4a373"/>
<rect x="15" y="21" width="1" height="1" fill="#b08e5f"/>
<rect x="14" y="7" width="2" height="2" fill="#b8ff3d"/>
<rect x="17" y="5" width="1" height="1" fill="#5eead4"/>
<rect x="12" y="4" width="1" height="1" fill="#5eead4"/>
<rect x="15" y="2" width="1" height="1" fill="#facc15"/>
<rect x="20" y="8" width="1" height="1" fill="#b8ff3d"/>
<rect x="10" y="7" width="1" height="1" fill="#facc15" opacity=".8"/>
<rect x="19" y="2" width="1" height="1" fill="#5eead4" opacity=".7"/>
<rect x="16" y="6" width="1" height="1" fill="#b8ff3d" opacity=".65"/>
</svg>`

const ghIcon =
  '<svg viewBox="0 0 16 16"><path fill="currentColor" stroke="none" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.4 7.4 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>'
const dlIcon = '<svg viewBox="0 0 24 24"><path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>'
const webIcon =
  '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.3 2.4 14.2 0 17M12 3.5c-2.4 2.3-2.4 14.2 0 17"/></svg>'
const zipIcon =
  '<svg viewBox="0 0 24 24"><path d="M4 5h16v14H4z"/><path d="M9 5v3M15 5v3M9 11v3M15 11v3M9 17v2M15 17v2"/><path d="M12 8v2"/></svg>'

const flowSteps = [
  { key: 'bitPouch.flowSearch', cls: 'st-search' },
  { key: 'bitPouch.flowOpen', cls: 'st-open' },
  { key: 'bitPouch.flowCalc', cls: 'st-calc' },
  { key: 'bitPouch.flowCopy', cls: 'st-copy' },
]

const stats = [
  { num: '11', labelKey: 'bitPouch.statTools' },
  { num: '2', labelKey: 'bitPouch.statRuntimes' },
  { num: '0', labelKey: 'bitPouch.statCloud' },
  { num: '4', labelKey: 'bitPouch.statTargets' },
]

const platformIcons = {
  win: '<svg viewBox="0 0 24 24"><path d="M4 5.6 10.6 4.7v6.8H4zM13.4 4.3 20 3.4v8.1h-6.6zM4 12.5h6.6v6.8L4 18.4zM13.4 12.5H20v8.1l-6.6-.9z"/></svg>',
  linux: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9.5 10 12l-3 2.5M13.5 15h3.5"/></svg>',
  mac: '<svg viewBox="0 0 24 24"><path d="M9 9h6v6H9z"/><circle cx="7" cy="7" r="2"/><circle cx="17" cy="7" r="2"/><circle cx="7" cy="17" r="2"/><circle cx="17" cy="17" r="2"/></svg>',
  web: webIcon,
}

const platforms = [
  {
    key: 'win',
    icon: platformIcons.win,
    nameKey: 'bitPouch.platWin',
    archKey: 'bitPouch.archWin',
    downloads: [
      {
        labelKey: 'bitPouch.dlWinSetup',
        meta: 'x64',
        asset: `bit_pouch_${version}_x64-setup.exe`,
        primary: true,
      },
      {
        labelKey: 'bitPouch.dlWinPortable',
        meta: 'zip',
        asset: `bit_pouch_${version}_x64-portable.zip`,
        icon: zipIcon,
      },
    ],
  },
  {
    key: 'linux',
    icon: platformIcons.linux,
    nameKey: 'bitPouch.platLinux',
    archKey: 'bitPouch.archLinux',
    downloads: [
      {
        labelKey: 'bitPouch.dlLinuxAppImage',
        meta: 'amd64',
        asset: `bit_pouch_${version}_amd64.AppImage`,
        primary: true,
      },
      {
        labelKey: 'bitPouch.dlLinuxDeb',
        meta: 'amd64',
        asset: `bit_pouch_${version}_amd64.deb`,
      },
    ],
  },
  {
    key: 'mac',
    icon: platformIcons.mac,
    nameKey: 'bitPouch.platMac',
    archKey: 'bitPouch.archMac',
    downloads: [
      {
        labelKey: 'bitPouch.dlMacDmg',
        meta: 'universal',
        asset: `bit_pouch_${version}_universal.dmg`,
        primary: true,
      },
    ],
  },
  {
    key: 'web',
    icon: platformIcons.web,
    nameKey: 'bitPouch.platWeb',
    archKey: 'bitPouch.archWeb',
    downloads: [
      {
        labelKey: 'bitPouch.dlWebZip',
        meta: 'static',
        asset: `bit_pouch_${version}_web.zip`,
        primary: true,
        icon: zipIcon,
      },
    ],
  },
]

const featureGroups = [
  {
    labelKey: 'bitPouch.grpTools',
    items: [
      {
        titleKey: 'bitPouch.f1Title',
        descKey: 'bitPouch.f1Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 8h10M7 12h10M7 16h6"/></svg>',
      },
      {
        titleKey: 'bitPouch.f2Title',
        descKey: 'bitPouch.f2Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3v18M5 8l7-5 7 5M5 16l7 5 7-5"/></svg>',
      },
      {
        titleKey: 'bitPouch.f3Title',
        descKey: 'bitPouch.f3Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 7h16M4 12h10M4 17h13"/><circle cx="19" cy="17" r="2"/></svg>',
      },
      {
        titleKey: 'bitPouch.f4Title',
        descKey: 'bitPouch.f4Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 9h4M7 13h8M7 16h5"/><path d="M16 9h2"/></svg>',
      },
      {
        titleKey: 'bitPouch.f5Title',
        descKey: 'bitPouch.f5Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/></svg>',
      },
    ],
  },
  {
    labelKey: 'bitPouch.grpCore',
    items: [
      {
        titleKey: 'bitPouch.f6Title',
        descKey: 'bitPouch.f6Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M8 12h8M8 15h5"/></svg>',
      },
      {
        titleKey: 'bitPouch.f7Title',
        descKey: 'bitPouch.f7Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3 4 7v5c0 5 3.5 8 8 9 4.5-1 8-4 8-9V7z"/><path d="m9 12 2 2 4-4"/></svg>',
      },
      {
        titleKey: 'bitPouch.f8Title',
        descKey: 'bitPouch.f8Desc',
        icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/></svg>',
      },
      {
        titleKey: 'bitPouch.f9Title',
        descKey: 'bitPouch.f9Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 4v9"/><circle cx="12" cy="17" r="4"/><path d="M7 7l2 2M17 7l-2 2"/></svg>',
      },
      {
        titleKey: 'bitPouch.f10Title',
        descKey: 'bitPouch.f10Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M4 5h16v14H4z"/><path d="M8 9h8M8 13h5"/><circle cx="17" cy="16" r="1.5"/></svg>',
      },
    ],
  },
  {
    labelKey: 'bitPouch.grpCross',
    items: [
      {
        titleKey: 'bitPouch.f11Title',
        descKey: 'bitPouch.f11Desc',
        icon: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="12" height="9" rx="1.5"/><rect x="9" y="11" width="12" height="9" rx="1.5"/><path d="M7 8h4M13 15h4"/></svg>',
      },
      {
        titleKey: 'bitPouch.f12Title',
        descKey: 'bitPouch.f12Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6z"/><path d="M9 12h6M12 9v6"/></svg>',
      },
      {
        titleKey: 'bitPouch.f13Title',
        descKey: 'bitPouch.f13Desc',
        icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19"/></svg>',
      },
      {
        titleKey: 'bitPouch.f14Title',
        descKey: 'bitPouch.f14Desc',
        icon: '<svg viewBox="0 0 24 24"><path d="M12 3v12m0 0 4-4m-4 4-4-4M5 20h14"/></svg>',
      },
    ],
  },
]

const notes = [
  { titleKey: 'bitPouch.noteWinTitle', bodyKey: 'bitPouch.noteWinBody' },
  { titleKey: 'bitPouch.noteLinuxTitle', bodyKey: 'bitPouch.noteLinuxBody' },
  { titleKey: 'bitPouch.noteMacTitle', bodyKey: 'bitPouch.noteMacBody' },
  { titleKey: 'bitPouch.noteUpdateTitle', bodyKey: 'bitPouch.noteUpdateBody' },
]

const { t } = useI18n()

onMounted(() => {
  document.title = t('bitPouch.docTitle')
  // 独立页整屏渲染,根元素也得跟着换色(滚动条/橡皮筋区,见样式里的 :global 段);
  // 类在挂载/卸载时增删,保证这份 chunk 的规则不会漏给 ihomy 其他页面。
  document.documentElement.classList.add('bitpouch-page')
})
onBeforeUnmount(() => {
  document.title = 'ihomy'
  document.documentElement.classList.remove('bitpouch-page')
})
</script>

<style scoped>
/* 页面自包含:默认深色(bit_pouch 品牌像素麻袋 + 荧光绿),
   浅色由浏览器/系统的 prefers-color-scheme 触发——两套只换令牌,
   版式与组件规则共用下面这一份(故别在别处写死颜色,新增颜色一律走变量)。 */
.bp {
  --bg: #0a0d0b;
  --panel: #111512;
  --line: #263128;
  --line-soft: rgba(184, 255, 61, 0.07);
  --text: #e7efe3;
  --muted: #8b9a8d;
  --accent: #b8ff3d;
  --accent-ink: #0a0d0b;
  --accent-dim: #2c3a1e;
  --key-bg: #1a221b;
  --key-line: #344531;
  --tan: #d9b98a;
  /* 页面自用衍生量:图标底/提示卡底是 accent 的淡色,主按钮与投影随主题走。 */
  --plate: rgba(184, 255, 61, 0.11);
  --accent-soft: rgba(184, 255, 61, 0.05);
  --accent-soft-line: rgba(184, 255, 61, 0.18);
  --hover-line: rgba(184, 255, 61, 0.42);
  --shadow-card: 0 2px 10px rgba(0, 0, 0, 0.35);
  --shadow-lift: 0 14px 30px rgba(0, 0, 0, 0.5);
  --cta-bg: #b8ff3d;
  --cta-ink: #0a0d0b;
  --cta-shadow: 0 8px 22px rgba(184, 255, 61, 0.18);
  --cta-shadow-hi: 0 12px 30px rgba(184, 255, 61, 0.26);
  --halo:
    radial-gradient(880px 420px at 50% -8%, rgba(184, 255, 61, 0.09), transparent 62%),
    radial-gradient(680px 380px at 94% 3%, rgba(94, 234, 212, 0.06), transparent 60%), var(--bg);
  /* 流程节点色:工具 / 打开 / 计算 / 复制 */
  --t-search: #b8ff3d;
  --t-open: #5eead4;
  --t-calc: #facc15;
  --t-copy: #e8a87c;
  --mono: Consolas, 'Cascadia Mono', monospace;

  color-scheme: dark;
  min-height: 100vh;
  color: var(--text);
  font:
    14px/1.6 'Microsoft YaHei UI',
    'Segoe UI',
    system-ui,
    sans-serif;
  background: var(--halo);
}

/* 浅色主题:跟随浏览器/系统偏好。强调色改深绿保证文字对比度,
   主按钮用深绿底白字;像素麻袋本身仍用自己的固定色。 */
@media (prefers-color-scheme: light) {
  .bp {
    --bg: #f4f4ed;
    --panel: #ffffff;
    --line: #d9ddcf;
    --line-soft: rgba(23, 27, 20, 0.08);
    --text: #171b14;
    --muted: #5f6b5c;
    --accent: #3f6b00;
    --accent-ink: #ffffff;
    --accent-dim: #dfe8c8;
    --key-bg: #eef0e6;
    --key-line: #c9cfbd;
    --plate: rgba(63, 107, 0, 0.1);
    --accent-soft: rgba(63, 107, 0, 0.05);
    --accent-soft-line: rgba(63, 107, 0, 0.18);
    --hover-line: rgba(63, 107, 0, 0.4);
    --shadow-card: 0 2px 10px rgba(23, 27, 20, 0.05);
    --shadow-lift: 0 14px 28px rgba(23, 27, 20, 0.1);
    --cta-bg: #3f6b00;
    --cta-ink: #ffffff;
    --cta-shadow: 0 8px 20px rgba(63, 107, 0, 0.24);
    --cta-shadow-hi: 0 12px 26px rgba(63, 107, 0, 0.3);
    --halo:
      radial-gradient(880px 420px at 50% -8%, rgba(63, 107, 0, 0.08), transparent 62%),
      radial-gradient(680px 380px at 94% 3%, rgba(32, 150, 130, 0.05), transparent 60%), var(--bg);
    --t-search: #3f6b00;
    --t-open: #0f766e;
    --t-calc: #a16207;
    --t-copy: #9a5b32;

    color-scheme: light;
  }
}
/* 独立页整屏渲染时,根元素也是页面的一部分:ihomy 外壳默认暖居·暮,html 上带着深色底与
   深色滚动条,浅色浏览器下会在页面上顶出一条深色边。底色取上面两套 --bg 的同值
   (令牌声明在 .bp 上,html 取不到)。 */
:global(html.bitpouch-page:root) {
  color-scheme: dark;
  background: #0a0d0b;
}
@media (prefers-color-scheme: light) {
  :global(html.bitpouch-page:root) {
    color-scheme: light;
    background: #f4f4ed;
  }
}
.bpwrap {
  max-width: 1060px;
  margin: 0 auto;
  padding: 0 24px;
}

/* ---------- 顶栏 ---------- */
.bptop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 24px 0 4px;
}
.bpbrand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bpmark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  overflow: hidden;
  box-shadow: 0 0 0 1px var(--line);
}
.bpmark :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}
.bpbrand-cn {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.bpbrand-en {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.1em;
}
.bpgh {
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
.bpgh:hover {
  color: var(--text);
  border-color: var(--muted);
  transform: translateY(-1px);
}
.bpgh-ic {
  width: 15px;
  height: 15px;
  display: block;
}
.bpgh-ic :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

/* ---------- 主视觉 ---------- */
.bpheroc {
  text-align: center;
  padding: 52px 0 44px;
}
.bpheromark {
  width: 96px;
  height: 96px;
  margin: 0 auto 24px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow:
    0 0 0 1px var(--line),
    0 18px 40px rgba(0, 0, 0, 0.45),
    0 0 48px rgba(184, 255, 61, 0.12);
}
.bpheromark :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}
.bptitle {
  font-size: 50px;
  font-weight: 800;
  letter-spacing: 0.01em;
  line-height: 1.1;
  margin: 0 0 10px;
}
.bptitle-en {
  font-size: 22px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.04em;
  margin-left: 12px;
  vertical-align: 4px;
}
.bptag {
  font-size: 19px;
  font-weight: 600;
  color: var(--accent);
  margin: 0 0 8px;
}
.bpsub {
  font-size: 15px;
  color: var(--muted);
  margin: 0 auto;
  max-width: 660px;
}

/* 工具链示意 */
.bpflow {
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
.bpflow-cap {
  font-size: 11.5px;
  color: var(--muted);
  letter-spacing: 0.04em;
}
.bpflow-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
.bpflow-bolt {
  color: var(--accent);
  font-size: 13px;
}
.bpflow-keys {
  display: inline-flex;
  gap: 3px;
}
.bpflow-keys kbd {
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
.bpflow-arrow {
  color: var(--muted);
  opacity: 0.65;
  font-size: 12px;
}
.bpflow-step {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: var(--muted);
}
.bpflow-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid var(--muted);
  box-sizing: content-box;
}
.bpflow-dot.st-search {
  border-color: var(--t-search);
}
.bpflow-dot.st-open {
  border-color: var(--t-open);
}
.bpflow-dot.st-calc {
  border-color: var(--t-calc);
}
.bpflow-dot.st-copy {
  border-color: var(--t-copy);
}
.bpflow-done {
  color: var(--t-search);
  font-size: 12.5px;
}

/* CTA */
.bpcta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 30px;
  flex-wrap: wrap;
}
.bpbtn {
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
.bpbtn-ic,
.bpdl-ic {
  width: 17px;
  height: 17px;
  display: block;
}
.bpbtn-ic :deep(svg),
.bpdl-ic :deep(svg) {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.bpbtn-primary {
  background: var(--cta-bg);
  border: 1px solid var(--cta-bg);
  color: var(--cta-ink);
  box-shadow: var(--cta-shadow);
}
.bpbtn-primary:hover {
  transform: translateY(-2px);
  filter: brightness(1.05);
  box-shadow: var(--cta-shadow-hi);
}
.bpbtn-ghost {
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text);
}
.bpbtn-ghost:hover {
  transform: translateY(-2px);
  border-color: var(--muted);
  box-shadow: var(--shadow-lift);
}

.bpmeta {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
  flex-wrap: wrap;
}
.bpchip {
  font-size: 12.5px;
  color: var(--muted);
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--panel);
  border: 1px solid var(--line);
}

/* ---------- 能力速览 ---------- */
.bpstats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding-bottom: 8px;
}
.bpstat {
  padding: 16px 14px;
  border-radius: 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  text-align: center;
}
.bpstat-n {
  font-family: var(--mono);
  font-size: 26px;
  font-weight: 700;
  color: var(--accent);
  line-height: 1.2;
}
.bpstat-l {
  font-size: 12.5px;
  color: var(--muted);
}

/* ---------- 分区 ---------- */
.bpsec {
  padding: 38px 0 0;
}
.bplabel {
  font-size: 12px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.bpsec-hint {
  font-size: 14px;
  color: var(--muted);
  margin: 4px 0 16px;
}

/* 下载 */
.bpplat {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(228px, 1fr));
  gap: 14px;
}
.bpplat-card {
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
.bpplat-card:hover {
  border-color: var(--hover-line);
  box-shadow: var(--shadow-lift);
  transform: translateY(-2px);
}
.bpplat-head {
  display: flex;
  align-items: center;
  gap: 11px;
}
.bpplat-ic {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 10px;
  background: var(--plate);
  display: flex;
  align-items: center;
  justify-content: center;
}
.bpplat-ic :deep(svg) {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.bpplat-titles {
  min-width: 0;
}
.bpplat-name {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
.bpplat-arch {
  font-size: 12px;
  color: var(--muted);
  margin: 1px 0 0;
}
.bpplat-dls {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bpdl {
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
.bpdl:hover {
  border-color: var(--muted);
}
.bpdl-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bpdl-meta {
  margin-left: auto;
  flex: none;
  font-family: var(--mono);
  font-size: 11.5px;
  font-weight: 400;
  color: var(--muted);
}
.bpdl-primary {
  background: var(--cta-bg);
  border-color: var(--cta-bg);
  color: var(--cta-ink);
}
.bpdl-primary .bpdl-meta {
  color: var(--cta-ink);
  opacity: 0.72;
}
.bpdl-primary:hover {
  filter: brightness(1.06);
  border-color: var(--cta-bg);
}
.bprelease {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--muted);
}
.bprelease a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid var(--accent-dim);
}
.bprelease a:hover {
  border-bottom-color: var(--accent);
}

/* 功能 */
.bpgroup + .bpgroup {
  margin-top: 22px;
}
.bpgroup-label {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 10px;
}
.bpgrid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(258px, 1fr));
  gap: 12px;
}
.bpcard {
  padding: 18px 18px 16px;
  border-radius: 14px;
  background: var(--panel);
  border: 1px solid var(--line);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}
.bpcard:hover {
  border-color: var(--hover-line);
  box-shadow: var(--shadow-lift);
  transform: translateY(-2px);
}
.bpcard-ic {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--plate);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.bpcard-ic :deep(svg) {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.bpcard-t {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 6px;
}
.bpcard-d {
  font-size: 12.8px;
  line-height: 1.7;
  color: var(--muted);
  margin: 0;
}

/* 首次运行与更新 */
.bpnotes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.bpnote {
  padding: 16px 18px;
  border-radius: 14px;
  background: var(--accent-soft);
  border: 1px solid var(--accent-soft-line);
}
.bpnote-t {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 6px;
}
.bpnote-b {
  margin: 0;
  font-size: 12.8px;
  line-height: 1.7;
  color: var(--muted);
}

/* 页脚 */
.bpfoot {
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
.bpfoot a {
  color: var(--muted);
  text-decoration: none;
}
.bpfoot a:hover {
  color: var(--accent);
}
.bpsep {
  opacity: 0.6;
}

@media (max-width: 900px) {
  .bpplat {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 720px) {
  .bpheroc {
    padding: 40px 0 34px;
  }
  .bptitle {
    font-size: 38px;
  }
  .bptitle-en {
    font-size: 17px;
    vertical-align: 3px;
  }
  .bpstats {
    grid-template-columns: 1fr 1fr;
  }
  .bpnotes {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .bpplat {
    grid-template-columns: 1fr;
  }
  .bpgrid {
    grid-template-columns: 1fr;
  }
  .bpflow-row {
    gap: 6px;
  }
}
</style>
