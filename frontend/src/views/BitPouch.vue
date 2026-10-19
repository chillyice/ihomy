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
          <a href="#download"><b>01</b>{{ $t('bitPouch.secDownload') }}</a>
          <a href="#features"><b>02</b>{{ $t('bitPouch.secFeatures') }}</a>
          <a href="#notes"><b>03</b>{{ $t('bitPouch.notesLabel') }}</a>
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

// ---- 分发渠道 ----
// 安装包由 bit_pouch 仓的 .github/workflows/release.yml 构建,发版后由服务器上的
// mirror-bit-pouch 每日从 GitHub 最新已发布 Release 同步到
// /opt/ihomy/uploads/bit_pouch/,nginx /files/ 反代托管;页面统一走 ihomy 直链,
// GitHub Releases 作为备用入口。换版本时同步 version 常量,并确认该 tag 已发布
// (默认建 draft,不发布则镜像与下载链接都取不到)。
const version = '0.3.0'
const repoUrl = 'https://github.com/chillyice/bit_pouch'
const releasesUrl = `${repoUrl}/releases`
const fileUrl = (name) => `/files/bit_pouch/${name}`

// 主 CTA:Windows 安装版(与下载表第一行一致)
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

<style scoped>
/* 页面自包含:墨绿底 + 荧光绿 + 麻布棕,方格纸底纹、细线分隔、等宽标号。
   浅色由浏览器/系统的 prefers-color-scheme 触发——两套只换令牌,版式共用;
   新增颜色一律走变量,终端面板始终保持深色,作为这页的视觉锚点。 */
.bp {
  --bg: #070a07;
  --panel: #0c110c;
  --line: #253126;
  --line-soft: rgba(184, 255, 61, 0.07);
  --grid: rgba(184, 255, 61, 0.045);
  --text: #e6efdf;
  --muted: #8b9a8d;
  --dim: #7d8c78;
  --accent: #b8ff3d;
  --accent-ink: #0a0d0b;
  --accent-soft: rgba(184, 255, 61, 0.07);
  --tan: #d9b98a;
  --term-bg: #050805;
  --term-line: #1f2c1c;
  --term-text: #c9d8bf;
  --term-dim: #6b7f66;
  --term-shadow: rgba(184, 255, 61, 0.08);
  --mono: 'Cascadia Code', Consolas, 'Courier New', monospace;
  --sans: 'Microsoft YaHei UI', 'Segoe UI', system-ui, sans-serif;
  --pad: 28px;

  color-scheme: dark;
  min-height: 100vh;
  color: var(--text);
  font: 14px/1.65 var(--sans);
  background: var(--bg);
}

/* 方格纸底纹:只在页面上半部显现,向下淡出,避免整页噪点。 */
.bp::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image:
    linear-gradient(to right, var(--grid) 1px, transparent 1px), linear-gradient(to bottom, var(--grid) 1px, transparent 1px);
  background-size: 40px 40px;
  -webkit-mask-image: radial-gradient(ellipse 140% 100% at 50% 0%, #000 0%, transparent 74%);
  mask-image: radial-gradient(ellipse 140% 100% at 50% 0%, #000 0%, transparent 74%);
}

/* 浅色:纸感暖白,强调色改深绿保证对比度;终端仍走深色令牌。 */
@media (prefers-color-scheme: light) {
  .bp {
    --bg: #f2f1e7;
    --panel: #fbfaf2;
    --line: #ccd0bd;
    --line-soft: rgba(30, 40, 20, 0.1);
    --grid: rgba(35, 50, 20, 0.06);
    --text: #161a12;
    --muted: #5f6b58;
    --dim: #64705d;
    --accent: #3f6b00;
    --accent-ink: #ffffff;
    --accent-soft: rgba(63, 107, 0, 0.07);
    --tan: #8a6434;
    --term-shadow: rgba(63, 107, 0, 0.15);

    color-scheme: light;
  }
}

/* 独立页整屏渲染时,根元素也是页面的一部分:ihomy 外壳默认暖居·暮,html 上带着深色底,
   浅色浏览器下会在页面上顶出一条深色边。底色取上面两套 --bg 的同值。 */
:global(html.bitpouch-page:root) {
  color-scheme: dark;
  background: #070a07;
  scroll-behavior: smooth;
}
@media (prefers-color-scheme: light) {
  :global(html.bitpouch-page:root) {
    color-scheme: light;
    background: #f2f1e7;
  }
}
@media (prefers-reduced-motion: reduce) {
  :global(html.bitpouch-page:root) {
    scroll-behavior: auto;
  }
}

.bp ::selection {
  background: var(--accent);
  color: var(--accent-ink);
}

.bpwrap {
  position: relative;
  z-index: 1;
  max-width: 1140px;
  margin: 0 auto;
  padding: 0 var(--pad);
}

.bp a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}

/* ---------- 顶栏 ---------- */
.bptop {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 66px;
  margin: 0 calc(-1 * var(--pad));
  padding: 0 var(--pad);
  border-bottom: 1px solid var(--line);
}
.bpbrand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.bpmark {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 0 0 1px var(--line);
}
.bpmark :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}
.bpbrand-name {
  font-family: var(--mono);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.bpver {
  flex: none;
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: 2px;
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--muted);
}
.bpnav {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-left: auto;
}
.bpnav a {
  display: inline-flex;
  align-items: baseline;
  gap: 7px;
  padding: 8px 0;
  font-size: 12.5px;
  color: var(--muted);
  text-decoration: none;
  transition: color 0.16s ease;
}
.bpnav b {
  font-family: var(--mono);
  font-size: 9.5px;
  font-weight: 400;
  letter-spacing: 0.08em;
  color: var(--dim);
  transition: color 0.16s ease;
}
.bpnav a:hover {
  color: var(--text);
}
.bpnav a:hover b {
  color: var(--accent);
}
.bpgh {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 2px;
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--muted);
  text-decoration: none;
  transition:
    color 0.16s ease,
    border-color 0.16s ease;
}
.bpgh:hover {
  color: var(--accent);
  border-color: var(--accent);
}
.bpgh-ic {
  width: 14px;
  height: 14px;
  display: block;
}
.bpgh-ic :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

/* ---------- 主视觉 ---------- */
.bpher {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, 0.98fr);
  gap: clamp(36px, 5vw, 72px);
  align-items: center;
  padding: clamp(56px, 7vw, 92px) 0 clamp(44px, 6vw, 72px);
}
.bpeyebrow {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 18px;
  font-family: var(--mono);
  font-size: 10.5px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--muted);
}
.bpeyebrow-dot {
  width: 7px;
  height: 7px;
  flex: none;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}
.bptitle {
  display: flex;
  align-items: baseline;
  margin: 0;
  font-family: var(--mono);
  font-size: clamp(46px, 6.6vw, 84px);
  font-weight: 700;
  line-height: 0.98;
  letter-spacing: -0.055em;
}
.bptitle-caret {
  width: 0.34em;
  height: 0.68em;
  margin-left: 0.06em;
  flex: none;
  background: var(--accent);
  animation: bpblink 1.2s steps(1) infinite;
}
.bpclaim {
  margin: 18px 0 0;
  font-size: clamp(19px, 2.2vw, 25px);
  font-weight: 700;
  letter-spacing: 0.01em;
}
.bpsub {
  max-width: 520px;
  margin: 10px 0 0;
  font-size: 14.5px;
  line-height: 1.75;
  color: var(--muted);
}
.bpcta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}
.bpbtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  height: 46px;
  padding: 0 20px;
  border: 1px solid var(--line);
  border-radius: 2px;
  font-family: var(--mono);
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-decoration: none;
  transition:
    transform 0.16s ease,
    filter 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease;
}
.bpbtn-ic {
  width: 16px;
  height: 16px;
  display: block;
}
.bpbtn-ic :deep(svg) {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.bpbtn-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
  clip-path: polygon(9px 0, 100% 0, 100% calc(100% - 9px), calc(100% - 9px) 100%, 0 100%, 0 9px);
}
.bpbtn-primary:hover {
  transform: translateY(-1px);
  filter: brightness(1.06);
}
.bpbtn-primary:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--text);
}
.bpbtn-ghost {
  background: transparent;
  color: var(--text);
}
.bpbtn-ghost:hover {
  transform: translateY(-1px);
  border-color: var(--accent);
  color: var(--accent);
}
.bpkv {
  display: flex;
  flex-wrap: wrap;
  max-width: 520px;
  margin: 32px 0 0;
  border-top: 1px solid var(--line);
}
.bpkv > div {
  flex: 1 1 120px;
  padding: 12px 18px 0 0;
}
.bpkv dt {
  font-family: var(--mono);
  font-size: 9.5px;
  letter-spacing: 0.15em;
  color: var(--dim);
}
.bpkv dd {
  margin: 3px 0 0;
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--text);
}

/* 终端演示:页面视觉锚点,深色不随主题反转 */
.bpher-visual {
  position: relative;
  min-width: 0;
}
.bppatch {
  position: absolute;
  top: -22px;
  right: -14px;
  z-index: 2;
  width: 62px;
  height: 62px;
  border-radius: 8px;
  overflow: hidden;
  transform: rotate(5deg);
  box-shadow:
    0 0 0 1px var(--line),
    0 10px 24px rgba(0, 0, 0, 0.32);
}
.bppatch :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}
.bpterm {
  position: relative;
  background: var(--term-bg);
  border: 1px solid var(--term-line);
  box-shadow:
    10px 10px 0 var(--term-shadow),
    0 30px 60px rgba(0, 0, 0, 0.35);
}
.bpterm::before,
.bpterm::after {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  border: 1px solid var(--accent);
  pointer-events: none;
}
.bpterm::before {
  top: -1px;
  left: -1px;
  border-right: 0;
  border-bottom: 0;
}
.bpterm::after {
  right: -1px;
  bottom: -1px;
  border-top: 0;
  border-left: 0;
}
.bpterm-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid var(--term-line);
}
.bpterm-dots {
  display: flex;
  gap: 5px;
}
.bpterm-dots i {
  width: 7px;
  height: 7px;
  background: #2b3a26;
}
.bpterm-dots i:first-child {
  background: var(--accent);
  opacity: 0.8;
}
.bpterm-title {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--term-dim);
}
.bpterm-body {
  padding: 18px 20px 16px;
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--term-text);
  overflow-x: auto;
}
.bpterm-line {
  white-space: pre;
}
.t-prompt,
.t-ok {
  color: var(--accent);
}
.t-dim,
.t-comment {
  color: var(--term-dim);
}
.t-out {
  color: var(--tan);
}
.bpterm-caret {
  display: inline-block;
  width: 7px;
  height: 13px;
  margin-left: 7px;
  vertical-align: -2px;
  background: var(--accent);
  animation: bpblink 1.1s steps(1) infinite;
}
.bpterm-foot {
  display: flex;
  gap: 16px;
  padding: 10px 20px;
  border-top: 1px solid var(--term-line);
  font-family: var(--mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
  color: var(--term-dim);
}
.bpterm-foot span:last-child {
  margin-left: auto;
  color: var(--accent);
}

/* ---------- 指标带 ---------- */
.bpstrip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.bpstat {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 18px 20px;
}
.bpstat + .bpstat {
  border-left: 1px solid var(--line);
}
.bpstat-n {
  font-family: var(--mono);
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--accent);
}
.bpstat-l {
  font-size: 11.5px;
  color: var(--muted);
}

/* ---------- 分区骨架:左侧编号,右侧内容 ---------- */
.bpsec {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 8px 28px;
  padding-top: clamp(56px, 7vw, 84px);
}
.bpsec-index {
  padding-top: 9px;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--accent);
}
.bpsec-index::after {
  content: '';
  display: block;
  width: 24px;
  height: 1px;
  margin-top: 12px;
  background: var(--line);
}
.bpsec-head {
  margin-bottom: 22px;
}
.bpsec-title {
  margin: 0;
  font-size: clamp(24px, 3vw, 30px);
  font-weight: 800;
  letter-spacing: -0.02em;
}
.bpsec-hint {
  max-width: 720px;
  margin: 8px 0 0;
  font-size: 13.5px;
  color: var(--muted);
}

/* ---------- 下载:构建产物清单表 ---------- */
.bptable {
  border-top: 1px solid var(--line);
}
.bptable-head {
  display: grid;
  grid-template-columns: 1.05fr 1.2fr 1fr;
  gap: 18px;
  padding: 9px 0;
  font-family: var(--mono);
  font-size: 9.5px;
  letter-spacing: 0.16em;
  color: var(--dim);
}
.bprow {
  display: grid;
  grid-template-columns: 1.05fr 1.2fr 1fr;
  gap: 18px;
  align-items: center;
  margin: 0 -10px;
  padding: 18px 10px;
  border-top: 1px solid var(--line-soft);
  transition: background 0.16s ease;
}
.bprow:hover {
  background: var(--accent-soft);
}
.bprow-os {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.bprow-ic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: none;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--panel);
}
.bprow-ic :deep(svg) {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: var(--accent);
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.bprow-meta {
  min-width: 0;
}
.bprow-name {
  display: block;
  font-size: 14.5px;
  font-weight: 700;
}
.bprow-arch {
  display: block;
  margin-top: 2px;
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--muted);
}
.bprow-build {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.bpfile {
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--muted);
  overflow-wrap: anywhere;
}
.bprow-get {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.bpdl {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: transparent;
  color: var(--text);
  font-family: var(--mono);
  font-size: 11.5px;
  font-weight: 600;
  text-decoration: none;
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease,
    filter 0.16s ease;
}
.bpdl:hover {
  transform: translateY(-1px);
  border-color: var(--accent);
  color: var(--accent);
}
.bpdl-ic {
  width: 14px;
  height: 14px;
  flex: none;
  display: block;
}
.bpdl-ic :deep(svg) {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.bpdl-meta {
  font-size: 9.5px;
  font-weight: 400;
  letter-spacing: 0.06em;
  color: var(--dim);
}
.bpdl.is-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}
.bpdl.is-primary .bpdl-meta {
  color: var(--accent-ink);
  opacity: 0.65;
}
.bpdl.is-primary:hover {
  color: var(--accent-ink);
  filter: brightness(1.06);
}
.bprelease {
  margin: 16px 0 0;
  font-family: var(--mono);
  font-size: 12px;
  color: var(--dim);
}
.bprelease a {
  color: var(--muted);
  text-decoration: none;
  border-bottom: 1px solid var(--line);
}
.bprelease a:hover {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

/* ---------- 功能:编号规格清单 ---------- */
.bpspec {
  border-top: 1px solid var(--line);
}
.bpspec-group {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 10px 28px;
  padding: 24px 0;
  border-bottom: 1px solid var(--line);
}
.bpspec-head {
  padding-top: 14px;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--tan);
}
.bpspec-head span {
  margin-right: 9px;
  color: var(--dim);
}
.bpspec-items {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 32px;
}
.bpspec-item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: start;
  padding: 13px 0;
  border-top: 1px solid var(--line-soft);
}
.bpspec-item:nth-child(-n + 2) {
  padding-top: 0;
  border-top: 0;
}
.bpspec-num {
  padding-top: 3px;
  font-family: var(--mono);
  font-size: 10px;
  color: var(--accent);
}
.bpspec-t {
  margin: 0;
  font-size: 13.5px;
  font-weight: 700;
}
.bpspec-d {
  grid-column: 2;
  margin: 4px 0 0;
  font-size: 12.3px;
  line-height: 1.7;
  color: var(--muted);
}

/* ---------- 首次运行与更新 ---------- */
.bpnotes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 40px;
  border-top: 1px solid var(--line);
}
.bpnote {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 10px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line-soft);
}
.bpnote-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border: 1px solid var(--tan);
  color: var(--tan);
  font-family: var(--mono);
  font-size: 11px;
  line-height: 1;
}
.bpnote-t {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 700;
}
.bpnote-b {
  margin: 0;
  font-size: 12.3px;
  line-height: 1.7;
  color: var(--muted);
}

/* ---------- 页脚 ---------- */
.bpfoot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 20px;
  margin-top: clamp(56px, 7vw, 84px);
  padding: 20px 0 40px;
  border-top: 1px solid var(--line);
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--dim);
}
.bpfoot-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
}
.bpfoot a {
  color: var(--muted);
  text-decoration: none;
}
.bpfoot a:hover {
  color: var(--accent);
}
.bpfoot-sep {
  opacity: 0.4;
}

@keyframes bpblink {
  0%,
  54% {
    opacity: 1;
  }
  55%,
  100% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bp *,
  .bp *::before,
  .bp *::after {
    animation: none !important;
    transition: none !important;
  }
}

/* ---------- 响应式 ---------- */
@media (max-width: 980px) {
  .bpher {
    grid-template-columns: 1fr;
    gap: 52px;
    padding-top: 56px;
  }
  .bpher-visual {
    width: 100%;
    max-width: 660px;
    margin: 0 auto;
  }
  .bpsec {
    grid-template-columns: 56px minmax(0, 1fr);
    gap: 8px 20px;
  }
  .bpspec-group {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .bpspec-head {
    padding-top: 0;
  }
}
@media (max-width: 820px) {
  .bpnav {
    display: none;
  }
  .bpgh {
    margin-left: auto;
  }
}
@media (max-width: 720px) {
  .bpstrip {
    grid-template-columns: 1fr 1fr;
  }
  .bpstat:nth-child(odd) {
    border-left: 0;
  }
  .bpstat:nth-child(n + 3) {
    border-top: 1px solid var(--line);
  }
  .bpspec-items {
    grid-template-columns: 1fr;
  }
  .bpspec-item:nth-child(-n + 2) {
    padding-top: 13px;
    border-top: 1px solid var(--line-soft);
  }
  .bpspec-item:first-child {
    padding-top: 0;
    border-top: 0;
  }
  .bpnotes {
    grid-template-columns: 1fr;
  }
  .bptable-head {
    display: none;
  }
  .bprow {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 18px 10px;
  }
  .bprow-build {
    display: none;
  }
}
@media (max-width: 560px) {
  .bp {
    --pad: 18px;
  }
  .bptop {
    height: 58px;
    gap: 14px;
  }
  .bpbrand-name {
    font-size: 14px;
  }
  .bpver {
    display: none;
  }
  .bpgh {
    width: 32px;
    padding: 0;
    justify-content: center;
  }
  .bpgh-label {
    display: none;
  }
  .bpsec {
    grid-template-columns: 1fr;
    gap: 6px;
  }
  .bpsec-index {
    padding-top: 0;
  }
  .bpsec-index::after {
    display: none;
  }
  .bpterm-body {
    padding: 14px 14px 12px;
    font-size: 11px;
    line-height: 1.85;
  }
  .bpterm-foot {
    gap: 10px;
    padding: 10px 14px;
    letter-spacing: 0.08em;
  }
  .bppatch {
    top: -16px;
    right: 4px;
    width: 48px;
    height: 48px;
  }
  .bpcta .bpbtn {
    width: 100%;
  }
  .bpkv {
    display: grid;
    grid-template-columns: 1fr;
  }
  .bpkv > div {
    padding: 10px 0;
    border-bottom: 1px dotted var(--line-soft);
  }
  .bpkv > div:last-child {
    border-bottom: 0;
  }
}
</style>
