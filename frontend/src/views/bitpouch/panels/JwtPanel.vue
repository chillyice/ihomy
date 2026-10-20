<!-- JWT 面板:解码结构 + 时间声明,可选 HMAC 验签。令牌只在本机解析。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.jwt.token') }}</span>
        <span>{{ $t('tools.jwt.tokenHint', { n: token.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <textarea
          v-model="token"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.jwt.tokenPlaceholder')"
        ></textarea>

        <div class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-jwt-alg">{{ $t('tools.jwt.verifyAlg') }}</label>
            <select id="bp-jwt-alg" v-model="alg" class="bpc-select">
              <option v-for="item in HMAC_ALGS" :key="item" :value="item">{{ item }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-jwt-secret">{{ $t('tools.jwt.secret') }}</label>
            <input id="bp-jwt-secret" v-model="secret" class="bpc-input" type="password" autocomplete="off" />
          </div>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" :disabled="!token || !secret" @click="verify">{{ $t('tools.jwt.verify') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.jwt.clear') }}</button>
        </div>

        <p v-if="verifyState === 'ok'" class="bpc-hint bpc-flash">{{ $t('tools.jwt.verifyOk') }}</p>
        <p v-else-if="verifyState === 'fail'" class="bpc-err">{{ $t('tools.jwt.verifyFail') }}</p>
        <p v-else-if="verifyState === 'error'" class="bpc-err">{{ verifyError }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.jwt.verifyNote') }}</p>
        <p class="bpc-hint">{{ $t('tools.jwt.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-stack">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.jwt.header') }}</span>
          <BpcCopy :value="headerText" :label="$t('tools.jwt.copy')" />
        </div>
        <div class="bpc-card-bd">
          <pre class="bpc-out bpc-out-short" :class="{ 'bpc-out-empty': !headerText }">{{ headerText || $t('tools.jwt.emptyDecode') }}</pre>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.jwt.payload') }}</span>
          <BpcCopy :value="payloadText" :label="$t('tools.jwt.copy')" />
        </div>
        <div class="bpc-card-bd">
          <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !payloadText }">{{
            payloadText || $t('tools.jwt.emptyDecode')
          }}</pre>
          <p v-if="error" class="bpc-err">{{ error }}</p>
          <div v-if="times.length" class="bpc-rows">
            <div v-for="item in times" :key="item.key" class="bpc-row">
              <span class="bpc-row-k">{{ item.label }}</span>
              <span class="bpc-row-v" :class="{ 'is-accent': item.status === 'active', 'is-error': item.status === 'expired' }">
                {{ item.iso }}
              </span>
              <span class="bpc-tag" :class="{ 'is-danger': item.status === 'expired' }">{{ item.status }}</span>
            </div>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { HMAC_ALGS, run, verifyHmac } from '@/utils/tools/jwtTool'

const { t, locale } = useI18n()

const token = ref('')
const alg = ref('HS256')
const secret = ref('')
const result = ref(null)
const error = ref('')
const verifyState = ref('idle')
const verifyError = ref('')

const SAMPLE =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJiaXRfcG91Y2giLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMzYwMH0.4r0Yb0l1YjJj1YjJj1YjJj1YjJj1YjJj1YjJj1YjJj1Y'

const pretty = (value) => (value ? JSON.stringify(value, null, 2) : '')

const headerText = computed(() => (result.value ? pretty(result.value.data.header) : ''))
const payloadText = computed(() => (result.value ? pretty(result.value.data.payload) : ''))
const times = computed(() => (result.value ? result.value.data.times : []))

const parse = () => {
  verifyState.value = 'idle'
  verifyError.value = ''
  if (!token.value.trim()) {
    result.value = null
    error.value = ''
    return
  }
  const out = run(token.value, {}, { locale: locale.value?.startsWith('en') ? 'en' : 'zh' })
  if (!out.ok) {
    result.value = null
    error.value = out.error.message
    return
  }
  error.value = ''
  result.value = out
}

watch(token, parse, { immediate: true })

const verify = async () => {
  verifyError.value = ''
  try {
    const ok = await verifyHmac(token.value, secret.value, alg.value)
    verifyState.value = ok ? 'ok' : 'fail'
  } catch (e) {
    verifyState.value = 'error'
    verifyError.value = e?.message || t('tools.jwt.verifyFail')
  }
}

const clearAll = () => {
  token.value = ''
  secret.value = ''
  result.value = null
  error.value = ''
  verifyState.value = 'idle'
}

// 首次打开给一段示例令牌,方便直接看到结构;内容只在内存里,不写入任何存储。
token.value = SAMPLE
</script>
