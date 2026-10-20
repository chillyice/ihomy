<!-- bit_pouch 独立页的顶栏:发布页与在线工具页共用,视觉与发布页保持同一套手册语言。
     版式类(.bptop/.bpbrand/.bpnav)在 styles/bit-pouch.css 里,两个页面共用一份。 -->
<template>
  <header class="bptop">
    <router-link class="bpbrand" to="/bit-pouch">
      <span class="bpmark" aria-hidden="true" v-html="pouchMark"></span>
      <span class="bpbrand-name">bit_pouch</span>
      <span class="bpver">v{{ version }}</span>
    </router-link>

    <nav class="bpnav" :aria-label="$t('bitPouch.navAria')">
      <router-link to="/bit-pouch/tools"><b>01</b>{{ $t('bitPouch.secOnline') }}</router-link>
      <router-link :to="{ path: '/bit-pouch', hash: '#download' }"><b>02</b>{{ $t('bitPouch.secDownload') }}</router-link>
      <router-link :to="{ path: '/bit-pouch', hash: '#features' }"><b>03</b>{{ $t('bitPouch.secFeatures') }}</router-link>
      <router-link :to="{ path: '/bit-pouch', hash: '#notes' }"><b>04</b>{{ $t('bitPouch.notesLabel') }}</router-link>
    </nav>

    <a class="bpgh" :href="repoUrl" target="_blank" rel="noopener noreferrer" :aria-label="$t('bitPouch.ghAria')">
      <span class="bpgh-ic" aria-hidden="true" v-html="ghIcon"></span>
      <span class="bpgh-label">GitHub</span>
    </a>
  </header>
</template>

<script setup>
import { BIT_POUCH_REPO, BIT_POUCH_VERSION, ghIcon, pouchMark } from './brand'

const version = BIT_POUCH_VERSION
const repoUrl = BIT_POUCH_REPO
</script>
