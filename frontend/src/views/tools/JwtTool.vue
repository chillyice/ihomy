<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.jwt.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="tab" size="small">
          <el-radio-button value="decode">{{ $t('tools.jwt.tabDecode') }}</el-radio-button>
          <el-radio-button value="verify">{{ $t('tools.jwt.tabVerify') }}</el-radio-button>
          <el-radio-button value="keys">{{ $t('tools.jwt.tabKeys') }}</el-radio-button>
          <el-radio-button value="crypt">{{ $t('tools.jwt.tabCrypt') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <span class="jwt-privacy">{{ $t('tools.jwt.privacy') }}</span>
      </div>
    </PageToolbar>

    <div class="jwt-grid">
      <div class="card jwt-pane">
        <template v-if="tab === 'decode' || tab === 'verify'">
          <div class="section-label">{{ $t('tools.jwt.token') }}</div>
          <textarea
            v-model="token"
            class="jwt-area"
            spellcheck="false"
            autocomplete="off"
            :placeholder="$t('tools.jwt.tokenPlaceholder')"
          ></textarea>
          <span class="jwt-hint">{{ $t('tools.jwt.tokenHint', { n: token.length }) }}</span>
          <el-button size="small" :disabled="!token" @click="clearToken">{{ $t('tools.jwt.clear') }}</el-button>
        </template>

        <template v-else-if="tab === 'keys'">
          <div class="jwt-field">
            <label>{{ $t('tools.jwt.alg') }}</label>
            <el-select v-model="keyAlg" size="small" style="width: 140px">
              <el-option v-for="alg in KEY_ALGS" :key="alg" :value="alg" :label="alg" />
            </el-select>
          </div>
          <el-button size="small" type="primary" :loading="working" @click="makeKeys">{{ $t('tools.jwt.generate') }}</el-button>
          <div class="jwt-field">
            <label>{{ $t('tools.jwt.convert') }}</label>
            <textarea
              v-model="convertInput"
              class="jwt-area jwt-area-sm"
              spellcheck="false"
              :placeholder="$t('tools.jwt.convertPlaceholder')"
            ></textarea>
            <div class="jwt-row">
              <el-select v-model="convertAlg" size="small" style="width: 120px">
                <el-option v-for="alg in ASYMMETRIC_ALGS" :key="alg" :value="alg" :label="alg" />
              </el-select>
              <el-button size="small" @click="toJwk">{{ $t('tools.jwt.pemToJwk') }}</el-button>
              <el-button size="small" @click="toPem">{{ $t('tools.jwt.jwkToPem') }}</el-button>
            </div>
          </div>
          <p class="jwt-note">{{ $t('tools.jwt.keyNote') }}</p>
        </template>

        <template v-else>
          <div class="jwt-field">
            <label for="jwt-enc-pw">{{ $t('tools.jwt.password') }}</label>
            <el-input id="jwt-enc-pw" v-model="encryptPassword" type="password" show-password size="small" />
          </div>
          <div class="jwt-field">
            <label>{{ $t('tools.jwt.plaintext') }}</label>
            <textarea v-model="plainInput" class="jwt-area jwt-area-sm" spellcheck="false"></textarea>
          </div>
          <el-button size="small" type="primary" :loading="working" @click="encrypt">{{ $t('tools.jwt.encrypt') }}</el-button>
          <div class="jwt-field">
            <label>{{ $t('tools.jwt.ciphertext') }}</label>
            <textarea v-model="decryptInput" class="jwt-area jwt-area-sm" spellcheck="false"></textarea>
          </div>
          <el-button size="small" :loading="working" @click="decrypt">{{ $t('tools.jwt.decrypt') }}</el-button>
          <p class="jwt-note">{{ $t('tools.jwt.cryptNote') }}</p>
        </template>
      </div>

      <div class="card jwt-pane">
        <div class="section-label">{{ $t(`tools.jwt.panel.${tab}`) }}</div>

        <div v-if="error" class="jwt-error">{{ error }}</div>

        <template v-else-if="tab === 'decode'">
          <div v-if="!decoded" class="jwt-empty">{{ $t('tools.jwt.emptyDecode') }}</div>
          <template v-else>
            <div class="jwt-claims">
              <span v-for="item in decoded.times" :key="item.key" class="jwt-badge" :class="`is-${item.status}`">
                {{ $t(`tools.jwt.claim.${item.key}`) }}: {{ item.iso }}
              </span>
            </div>
            <div v-for="section in decodeSections" :key="section.key" class="jwt-section">
              <div class="jwt-section-head">
                <span class="jwt-section-title">{{ section.title }}</span>
                <el-button size="small" text @click="copy(section.text)">{{ $t('tools.jwt.copy') }}</el-button>
              </div>
              <pre class="jwt-json">{{ section.text }}</pre>
            </div>
          </template>
        </template>

        <template v-else-if="tab === 'verify'">
          <div class="jwt-field">
            <label>{{ $t('tools.jwt.verifyAlg') }}</label>
            <el-select v-model="verifyAlg" size="small" style="width: 140px">
              <el-option v-for="alg in ALL_ALGS" :key="alg" :value="alg" :label="alg" />
            </el-select>
          </div>
          <div class="jwt-field">
            <label>{{ isHmac ? $t('tools.jwt.secret') : $t('tools.jwt.publicKey') }}</label>
            <el-input v-if="isHmac" v-model="secret" type="password" show-password size="small" />
            <textarea
              v-else
              v-model="publicKey"
              class="jwt-area jwt-area-sm"
              spellcheck="false"
              :placeholder="$t('tools.jwt.publicKeyPlaceholder')"
            ></textarea>
          </div>
          <el-button size="small" type="primary" :disabled="!token" :loading="working" @click="verify">
            {{ $t('tools.jwt.verify') }}
          </el-button>
          <div v-if="verifyResult" class="jwt-verdict" :class="verifyResult === 'ok' ? 'is-ok' : 'is-fail'">
            {{ verifyResult === 'ok' ? $t('tools.jwt.verifyOk') : $t('tools.jwt.verifyFail') }}
          </div>
          <p class="jwt-note">{{ $t('tools.jwt.verifyNote') }}</p>
        </template>

        <template v-else-if="tab === 'keys'">
          <div v-if="!keyPair" class="jwt-empty">{{ $t('tools.jwt.emptyKeys') }}</div>
          <template v-else>
            <div class="jwt-section">
              <div class="jwt-section-head">
                <span class="jwt-section-title">{{ $t('tools.jwt.fingerprint') }}</span>
                <el-button size="small" text @click="copy(keyPair.fingerprint)">{{ $t('tools.jwt.copy') }}</el-button>
              </div>
              <code class="jwt-fingerprint">{{ keyPair.fingerprint }}</code>
            </div>
            <div v-for="section in keySections" :key="section.key" class="jwt-section">
              <div class="jwt-section-head">
                <span class="jwt-section-title">{{ section.title }}</span>
                <el-button size="small" text @click="copy(section.text)">{{ $t('tools.jwt.copy') }}</el-button>
              </div>
              <pre class="jwt-json">{{ section.text }}</pre>
            </div>
          </template>
          <div v-if="converted" class="jwt-section">
            <div class="jwt-section-head">
              <span class="jwt-section-title">{{ $t('tools.jwt.converted') }}</span>
              <el-button size="small" text @click="copy(converted)">{{ $t('tools.jwt.copy') }}</el-button>
            </div>
            <pre class="jwt-json">{{ converted }}</pre>
          </div>
        </template>

        <template v-else>
          <div v-if="cryptOutput" class="jwt-section">
            <div class="jwt-section-head">
              <span class="jwt-section-title">{{ $t('tools.jwt.cryptOutput') }}</span>
              <el-button size="small" text @click="copy(cryptOutput)">{{ $t('tools.jwt.copy') }}</el-button>
            </div>
            <pre class="jwt-json">{{ cryptOutput }}</pre>
          </div>
          <div v-else class="jwt-empty">{{ $t('tools.jwt.emptyCrypt') }}</div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  ASYMMETRIC_ALGS,
  HMAC_ALGS,
  decodeToken,
  decryptText,
  describeTimeClaims,
  encryptText,
  generateKeyPair,
  jwkToPem,
  pemToJwk,
  verifyHmac,
  verifyWithKey,
} from '@/utils/tools/jwtTool'

const { t, te } = useI18n()

const ALL_ALGS = [...HMAC_ALGS, ...ASYMMETRIC_ALGS]
const KEY_ALGS = ['RS256', 'RS384', 'RS512', 'ES256', 'ES384', 'ES512']

const tab = ref('decode')
const token = ref('')
const decoded = ref(null)
const error = ref('')
const working = ref(false)

const verifyAlg = ref('HS256')
const secret = ref('')
const publicKey = ref('')
const verifyResult = ref('')

const keyAlg = ref('RS256')
const keyPair = ref(null)
const convertInput = ref('')
const convertAlg = ref('RS256')
const converted = ref('')

const encryptPassword = ref('')
const plainInput = ref('')
const decryptInput = ref('')
const cryptOutput = ref('')

const isHmac = computed(() => HMAC_ALGS.includes(verifyAlg.value))
const decodeSections = computed(() => {
  if (!decoded.value) return []
  return [
    { key: 'header', title: t('tools.jwt.header'), text: JSON.stringify(decoded.value.header, null, 2) },
    { key: 'payload', title: t('tools.jwt.payload'), text: JSON.stringify(decoded.value.payload, null, 2) },
  ]
})
const keySections = computed(() => {
  if (!keyPair.value) return []
  return [
    { key: 'publicPem', title: t('tools.jwt.publicPem'), text: keyPair.value.publicPem },
    { key: 'privatePem', title: t('tools.jwt.privatePem'), text: keyPair.value.privatePem },
    { key: 'publicJwk', title: t('tools.jwt.publicJwk'), text: JSON.stringify(keyPair.value.publicJwk, null, 2) },
  ]
})

const fail = (err, fallback) => {
  const code = err?.message
  error.value = t(`tools.jwt.err.${te(`tools.jwt.err.${code}`) ? code : fallback}`)
}

const resetError = () => {
  error.value = ''
}

const analyze = () => {
  resetError()
  decoded.value = null
  if (!token.value.trim()) return
  try {
    const parsed = decodeToken(token.value)
    decoded.value = { header: parsed.header, payload: parsed.payload, times: describeTimeClaims(parsed.payload) }
  } catch (err) {
    fail(err, 'invalidToken')
  }
}

watch(token, analyze)
watch(tab, () => {
  resetError()
  verifyResult.value = ''
  if (tab.value === 'decode') analyze()
})

const clearToken = () => {
  token.value = ''
  decoded.value = null
  verifyResult.value = ''
  resetError()
}

const verify = async () => {
  resetError()
  verifyResult.value = ''
  working.value = true
  try {
    const ok = isHmac.value
      ? await verifyHmac(token.value, secret.value, verifyAlg.value)
      : await verifyWithKey(token.value, publicKey.value, verifyAlg.value)
    verifyResult.value = ok ? 'ok' : 'fail'
  } catch (err) {
    fail(err, isHmac.value ? 'invalidSecret' : 'invalidKey')
  } finally {
    working.value = false
  }
}

const makeKeys = async () => {
  resetError()
  working.value = true
  try {
    keyPair.value = await generateKeyPair(keyAlg.value)
    converted.value = ''
  } catch (err) {
    fail(err, 'cryptoUnavailable')
  } finally {
    working.value = false
  }
}

const toJwk = async () => {
  resetError()
  try {
    const jwk = await pemToJwk(convertInput.value, convertAlg.value)
    converted.value = JSON.stringify(jwk, null, 2)
  } catch (err) {
    fail(err, 'invalidKey')
  }
}

const toPem = async () => {
  resetError()
  try {
    const kind = convertInput.value.includes('"d"') || convertInput.value.includes('"p"') ? 'private' : 'public'
    converted.value = await jwkToPem(convertInput.value, kind)
  } catch (err) {
    fail(err, 'invalidKey')
  }
}

const encrypt = async () => {
  resetError()
  cryptOutput.value = ''
  working.value = true
  try {
    decryptInput.value = await encryptText(plainInput.value, encryptPassword.value)
    cryptOutput.value = decryptInput.value
  } catch (err) {
    fail(err, 'invalidPassword')
  } finally {
    working.value = false
  }
}

const decrypt = async () => {
  resetError()
  cryptOutput.value = ''
  working.value = true
  try {
    cryptOutput.value = await decryptText(decryptInput.value, encryptPassword.value)
  } catch (err) {
    fail(err, 'badPayload')
  } finally {
    working.value = false
  }
}

const copy = async (text) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('tools.jwt.copied'))
  } catch {
    ElMessage.error(t('tools.jwt.copyFailed'))
  }
}
</script>

<style scoped>
.jwt-privacy {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.jwt-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .jwt-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.jwt-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.jwt-area {
  width: 100%;
  min-height: 150px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-soft);
  color: var(--color-text);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  resize: vertical;
}
.jwt-area-sm {
  min-height: 90px;
}
.jwt-area:focus {
  outline: none;
  border-color: var(--color-accent);
}
.jwt-hint,
.jwt-note {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.jwt-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.jwt-field label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.jwt-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.jwt-claims {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.jwt-badge {
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 11.5px;
}
.jwt-badge.is-expired {
  background: rgba(220, 80, 80, 0.14);
  color: var(--color-danger, #c0392b);
}
.jwt-badge.is-active {
  background: rgba(46, 160, 90, 0.14);
  color: #2e9a5a;
}
.jwt-badge.is-pending {
  background: rgba(230, 162, 60, 0.16);
  color: #b7791f;
}
.jwt-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.jwt-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.jwt-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.jwt-json {
  margin: 0;
  padding: 10px 12px;
  max-height: 280px;
  overflow: auto;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.jwt-fingerprint {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 11.5px;
  word-break: break-all;
}
.jwt-verdict {
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.jwt-verdict.is-ok {
  background: rgba(46, 160, 90, 0.14);
  color: #2e9a5a;
}
.jwt-verdict.is-fail {
  background: rgba(220, 80, 80, 0.12);
  color: var(--color-danger, #c0392b);
}
.jwt-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(220, 80, 80, 0.12);
  color: var(--color-danger, #c0392b);
  font-size: 13px;
}
.jwt-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
</style>
