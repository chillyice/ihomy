<!-- bit_pouch 发布页 —— 独立产品页,托管在 ihomy 域名 https://ihomy.top/bit-pouch。
     视觉走 bit_pouch 自己的「工具手册 / 终端工作台」方向:方格纸底纹、等宽字标号、
     非对称 Hero + 终端演示、构建产物清单表、编号规格清单;不用居中主视觉与圆角卡片网格。
     页内自包含、不随 ihomy 的暖居/光尘主题变化;路由 meta.standalone 让 App.vue 不套
     ihomy 外壳、直接全屏渲染,也不初始化家庭数据。 -->
<template>
  <div class="bp">
    <div class="bpwrap">
      <!-- 顶栏:品牌 + 分区索引 + GitHub 入口 -->
      <header class="bptop">
        <div class="bpbrand">
          <span class="bpmark" aria-hidden="true" v-html="pouchMark"></span>
          <span class="bpbrand-name">bit_pouch</span>
          <span class="bpver">v{{ version }}</span>
        </div>
        <nav class="bpnav" :aria-label="$t('bitPouch.navAria')">
          <router-link to="/bit-pouch/tools"><b>01</b>{{ $t('bitPouch.secOnline') }}</router-link>
          <a href="#download"><b>02</b>{{ $t('bitPouch.secDownload') }}</a>
          <a href="#features"><b>03</b>{{ $t('bitPouch.secFeatures') }}</a>
          <a href="#notes"><b>04</b>{{ $t('bitPouch.notesLabel') }}</a>
        </nav>
        <a class="bpgh" :href="repoUrl" target="_blank" rel="noopener noreferrer" :aria-label="$t('bitPouch.ghAria')">
          <span class="bpgh-ic" aria-hidden="true" v-html="ghIcon"></span>
          <span class="bpgh-label">GitHub</span>
        </a>
      </header>

      <!-- 主视觉:左文案,右终端 -->
      <section id="top" class="bpher">
        <div class="bpher-copy">
          <p class="bpeyebrow"><span class="bpeyebrow-dot" aria-hidden="true"></span>{{ $t('bitPouch.eyebrow') }}</p>
          <h1 class="bptitle">bit_pouch<span class="bptitle-caret" aria-hidden="true"></span></h1>
          <p class="bpclaim">{{ $t('bitPouch.tag') }}</p>
          <p class="bpsub">{{ $t('bitPouch.sub') }}</p>

          <div class="bpcta">
            <a class="bpbtn bpbtn-primary" :href="downloadUrl(primaryAsset)" download>
              <span class="bpbtn-ic" aria-hidden="true" v-html="dlIcon"></span>
              {{ $t('bitPouch.ctaDownload') }}
            </a>
            <router-link class="bpbtn bpbtn-ghost bpbtn-online" to="/bit-pouch/tools">
              <span class="bpbtn-ic" aria-hidden="true" v-html="webIcon"></span>
              {{ $t('bitPouch.ctaOnline') }}
            </router-link>
            <a class="bpbtn bpbtn-ghost" href="#download">{{ $t('bitPouch.ctaPlatforms') }}</a>
          </div>

          <dl class="bpkv">
            <div>
              <dt>{{ $t('bitPouch.kvVersion') }}</dt>
              <dd>v{{ version }}</dd>
            </div>
            <div>
              <dt>{{ $t('bitPouch.kvRuntime') }}</dt>
              <dd>main / worker</dd>
            </div>
            <div>
              <dt>{{ $t('bitPouch.kvNetwork') }}</dt>
              <dd>0 uploads</dd>
            </div>
          </dl>
        </div>

        <div class="bpher-visual">
          <div class="bppatch" aria-hidden="true" v-html="pouchMark"></div>
          <div class="bpterm" aria-hidden="true">
            <div class="bpterm-bar">
              <span class="bpterm-dots"><i></i><i></i><i></i></span>
              <span class="bpterm-title">bit_pouch — shell</span>
            </div>
            <div class="bpterm-body">
              <div class="bpterm-line"><span class="t-prompt">$</span> bit_pouch</div>
              <div class="bpterm-line t-comment"># open a tool by name</div>
              <div class="bpterm-line"><span class="t-prompt">&gt;</span> json.format</div>
              <div class="bpterm-line"><span class="t-ok">✓</span> plugin loaded <span class="t-dim">· worker</span></div>
              <div class="bpterm-line"><span class="t-prompt">&gt;</span> {"id":1,"tags":["a","b"]}</div>
              <div class="bpterm-line t-out">{</div>
              <div class="bpterm-line t-out">"id": 1,</div>
              <div class="bpterm-line t-out">"tags": ["a", "b"]</div>
              <div class="bpterm-line t-out">}</div>
              <div class="bpterm-line">
                <span class="t-ok">✓</span> 0.8 ms <span class="t-dim">· 0 B uploaded</span><span class="bpterm-caret"></span>
              </div>
            </div>
            <div class="bpterm-foot">
              <span>12 TOOLS</span>
              <span>LOCAL-FIRST</span>
              <span class="t-ok">NO CLOUD</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 能力速览:一条横向指标带,不做卡片 -->
      <section class="bpstrip">
        <div v-for="s in stats" :key="s.labelKey" class="bpstat">
          <span class="bpstat-n">{{ s.num }}</span>
          <span class="bpstat-l">{{ $t(s.labelKey) }}</span>
        </div>
      </section>

      <!-- 下载:构建产物清单表 -->
      <section id="download" class="bpsec">
        <div class="bpsec-index" aria-hidden="true">01</div>
        <div class="bpsec-body">
          <header class="bpsec-head">
            <h2 class="bpsec-title">{{ $t('bitPouch.secDownload') }}</h2>
            <p class="bpsec-hint">{{ $t('bitPouch.dlHint') }}</p>
          </header>

          <div class="bptable">
            <div class="bptable-head" aria-hidden="true">
              <span>{{ $t('bitPouch.colTarget') }}</span>
              <span>{{ $t('bitPouch.colBuild') }}</span>
              <span>{{ $t('bitPouch.colGet') }}</span>
            </div>

            <div v-for="p in platforms" :key="p.key" class="bprow">
              <div class="bprow-os">
                <span class="bprow-ic" aria-hidden="true" v-html="p.icon"></span>
                <span class="bprow-meta">
                  <span class="bprow-name">{{ $t(p.nameKey) }}</span>
                  <span class="bprow-arch">{{ $t(p.archKey) }}</span>
                </span>
              </div>
              <div class="bprow-build" aria-hidden="true">
                <span v-for="d in p.downloads" :key="d.asset" class="bpfile">{{ d.asset }}</span>
              </div>
              <div class="bprow-get">
                <a
                  v-for="d in p.downloads"
                  :key="d.labelKey"
                  class="bpdl"
                  :class="{ 'is-primary': d.primary }"
                  :href="downloadUrl(d.asset)"
                  download
                >
                  <span class="bpdl-ic" aria-hidden="true" v-html="d.icon || dlIcon"></span>
                  <span class="bpdl-label">{{ $t(d.labelKey) }}</span>
                  <span class="bpdl-meta">{{ d.meta }}</span>
                </a>
              </div>
            </div>
          </div>

          <p class="bprelease">
            {{ $t('bitPouch.releaseHint') }}
            <a :href="releasesUrl" target="_blank" rel="noopener noreferrer">GitHub Releases ↗</a>
          </p>
        </div>
      </section>

      <!-- 功能:编号规格清单 -->
      <section id="features" class="bpsec">
        <div class="bpsec-index" aria-hidden="true">02</div>
        <div class="bpsec-body">
          <header class="bpsec-head">
            <h2 class="bpsec-title">{{ $t('bitPouch.secFeatures') }}</h2>
            <p class="bpsec-hint">{{ $t('bitPouch.featHint') }}</p>
          </header>

          <div class="bpspec">
            <div v-for="g in featureGroups" :key="g.labelKey" class="bpspec-group">
              <div class="bpspec-head">
                <span>{{ g.num }}</span
                >{{ $t(g.labelKey) }}
              </div>
              <div class="bpspec-items">
                <div v-for="f in g.items" :key="f.titleKey" class="bpspec-item">
                  <span class="bpspec-num">{{ f.num }}</span>
                  <h3 class="bpspec-t">{{ $t(f.titleKey) }}</h3>
                  <p class="bpspec-d">{{ $t(f.descKey) }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 首次运行与更新:readme 式说明 -->
      <section id="notes" class="bpsec">
        <div class="bpsec-index" aria-hidden="true">03</div>
        <div class="bpsec-body">
          <header class="bpsec-head">
            <h2 class="bpsec-title">{{ $t('bitPouch.notesLabel') }}</h2>
          </header>

          <div class="bpnotes">
            <div v-for="n in notes" :key="n.titleKey" class="bpnote">
              <span class="bpnote-mark" aria-hidden="true">!</span>
              <div>
                <h3 class="bpnote-t">{{ $t(n.titleKey) }}</h3>
                <p class="bpnote-b">{{ $t(n.bodyKey) }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 页脚 -->
      <footer class="bpfoot">
        <span>{{ $t('bitPouch.footer', { version }) }} · {{ $t('bitPouch.license') }}</span>
        <span class="bpfoot-links">
          <a :href="repoUrl" target="_blank" rel="noopener noreferrer">{{ $t('bitPouch.sourceCode') }}</a>
          <span class="bpfoot-sep" aria-hidden="true">/</span>
          <span>{{ $t('bitPouch.hostedBy') }}</span>
        </span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { BIT_POUCH_REPO, BIT_POUCH_VERSION, pouchMark, ghIcon, dlIcon, webIcon, zipIcon } from './bitpouch/brand'

// ---- 分发渠道 ----
// 安装包由 bit_pouch 仓的 .github/workflows/release.yml 构建,发版后由服务器上的
// mirror-bit-pouch 每日从 GitHub 最新已发布 Release 同步到
// /opt/ihomy/uploads/bit_pouch/,nginx /files/ 反代托管;页面统一走 ihomy 直链,
// GitHub Releases 作为备用入口。换版本时同步 version 常量,并确认该 tag 已发布
// (默认建 draft,不发布则镜像与下载链接都取不到)。
const version = BIT_POUCH_VERSION
const repoUrl = BIT_POUCH_REPO
const releasesUrl = `${repoUrl}/releases`
const fileUrl = (name) => `/files/bit_pouch/${name}`

// 主 CTA:Windows 安装版(与下载表第一行一致)
const primaryAsset = `bit_pouch_${version}_x64-setup.exe`
const downloadUrl = (asset) => fileUrl(asset)

const stats = [
  { num: '12', labelKey: 'bitPouch.statTools' },
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

// 规格清单的编号:分组 01/02/03,条目 01–14 连续,方便对照与引用。
let specNo = 0
const featureGroups = [
  {
    labelKey: 'bitPouch.grpTools',
    items: [
      { titleKey: 'bitPouch.f1Title', descKey: 'bitPouch.f1Desc' },
      { titleKey: 'bitPouch.f2Title', descKey: 'bitPouch.f2Desc' },
      { titleKey: 'bitPouch.f3Title', descKey: 'bitPouch.f3Desc' },
      { titleKey: 'bitPouch.f4Title', descKey: 'bitPouch.f4Desc' },
      { titleKey: 'bitPouch.f5Title', descKey: 'bitPouch.f5Desc' },
    ],
  },
  {
    labelKey: 'bitPouch.grpCore',
    items: [
      { titleKey: 'bitPouch.f6Title', descKey: 'bitPouch.f6Desc' },
      { titleKey: 'bitPouch.f7Title', descKey: 'bitPouch.f7Desc' },
      { titleKey: 'bitPouch.f8Title', descKey: 'bitPouch.f8Desc' },
      { titleKey: 'bitPouch.f9Title', descKey: 'bitPouch.f9Desc' },
      { titleKey: 'bitPouch.f10Title', descKey: 'bitPouch.f10Desc' },
    ],
  },
  {
    labelKey: 'bitPouch.grpCross',
    items: [
      { titleKey: 'bitPouch.f11Title', descKey: 'bitPouch.f11Desc' },
      { titleKey: 'bitPouch.f12Title', descKey: 'bitPouch.f12Desc' },
      { titleKey: 'bitPouch.f13Title', descKey: 'bitPouch.f13Desc' },
      { titleKey: 'bitPouch.f14Title', descKey: 'bitPouch.f14Desc' },
    ],
  },
].map((group, gi) => ({
  ...group,
  num: String(gi + 1).padStart(2, '0'),
  items: group.items.map((item) => {
    specNo += 1
    return { ...item, num: String(specNo).padStart(2, '0') }
  }),
}))

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

<style src="../styles/bit-pouch.css"></style>
