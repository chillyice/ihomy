<template>
  <svg
    class="settings-icon"
    viewBox="0 0 32 32"
    fill="none"
    stroke="var(--icon-ink)"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <g v-if="name === 'profile'" transform="rotate(-5 16 16)">
      <rect x="6" y="4" width="21" height="25" rx="3" fill="var(--icon-paper)" />
      <path d="M10 4v25M4.5 9h3.8m-3.8 7h3.8m-3.8 7h3.8" />
      <circle cx="18.5" cy="12.5" r="3.3" fill="var(--icon-honey)" />
      <path d="M12.9 23c.3-3.1 2.4-5.3 5.6-5.3s5.3 2.2 5.5 5.3Z" fill="var(--icon-clay)" />
    </g>
    <g v-else-if="name === 'health'">
      <path d="M11 11V8c0-2 1.3-3 3-3h4c1.7 0 3 1 3 3v3" />
      <rect x="3.5" y="9.5" width="25" height="18" rx="4" fill="var(--icon-sage)" />
      <path d="M16 23.2c-2.6-1.6-6-4.1-6-6.7a3.3 3.3 0 0 1 6-1.7 3.3 3.3 0 0 1 6 1.7c0 2.6-3.5 5.2-6 6.7Z" fill="var(--icon-paper)" />
    </g>
    <g v-else-if="name === 'family'">
      <path d="M6.5 14.5 16 6l10 8v13H6.5Z" fill="var(--icon-paper)" />
      <path d="M21 9V4.5h3.5v7" fill="var(--icon-honey)" />
      <path d="m3 14.1 12-10a1.6 1.6 0 0 1 2 0L29 14l-2.2 2.8L16 8 5.2 17Z" fill="var(--icon-clay)" />
      <path d="M10 27v-8a3.4 3.4 0 0 1 6.8 0v8Z" fill="var(--icon-honey)" />
      <path d="M19.5 16.5h4v4h-4Z" fill="var(--icon-sage)" />
      <path d="M4.5 27.5h23" />
    </g>
    <g v-else-if="name === 'pointsRule'">
      <path d="m10.5 18-3 10 5-2 3 3 2.5-10M21 18l4 10-5-1.8-2.5 2.8-3.5-10" fill="var(--icon-clay)" />
      <circle cx="16" cy="12.8" r="9.4" fill="var(--icon-honey)" />
      <path d="m16 7.1 1.7 3.6 4 .6-2.9 2.8.7 4-3.5-1.9-3.5 1.9.7-4-2.9-2.8 4-.6Z" fill="var(--icon-paper)" />
    </g>
    <g v-else-if="name === 'ai'">
      <path d="M15.5 10.5V7.3C15.5 4 18.8 3 22 3c-.5 3.5-2.8 5.5-6.5 4.7" fill="var(--icon-sage)" />
      <path d="M6 17H3.5v5H6m20-5h2.5v5H26" fill="var(--icon-honey)" />
      <rect x="5.8" y="10.2" width="20.5" height="16.5" rx="5.5" fill="var(--icon-paper)" />
      <path d="M11 16.5v2m10-2v2m-7 3c1.1 1.3 3 1.3 4.1 0" stroke-width="2" />
    </g>
    <g v-else-if="name === 'backup'">
      <path d="m5.5 13 1 14.3 19.3-.1 1-14.2Z" fill="var(--icon-honey)" />
      <path d="M4 13.5h24v-5H4Z" fill="var(--icon-paper)" />
      <path d="M16 23.3v-8m-3.5 3.5 3.5-3.5 3.5 3.5" />
      <path d="m9 8.5 1-4.2L23 5l-.2 3.5" fill="var(--icon-sage)" />
    </g>
    <g v-else-if="name === 'content'" transform="rotate(-4 16 16)">
      <rect x="5" y="6.5" width="22" height="21.5" rx="2.8" fill="var(--icon-paper)" />
      <path d="M5 13V9.2a2.7 2.7 0 0 1 2.7-2.7h16.6A2.7 2.7 0 0 1 27 9.2V13Z" fill="var(--icon-clay)" />
      <path d="M10.5 4.2v5m11-5v5m-12 8.3h3m-3 5h3m3.3-2.5 2.5 2.5 4.6-5.5" />
    </g>
    <g v-else-if="name === 'media'">
      <path d="M5 13h22v12a2.2 2.2 0 0 1-2.2 2.2H7.2A2.2 2.2 0 0 1 5 25Z" fill="var(--icon-sage)" />
      <g transform="rotate(-12 5 13)">
        <path d="M5 8h22v5H5Z" fill="var(--icon-paper)" />
        <path d="m9 8 2.8 5m4-5 2.8 5m4-5 2.8 5" />
      </g>
      <path d="m13.5 16.4 6.4 3.5-6.4 3.7Z" fill="var(--icon-paper)" />
    </g>
    <g v-else-if="name === 'storage'">
      <path d="m5 7 3-3h16l3 3" fill="var(--icon-paper)" />
      <rect x="5" y="7" width="22" height="20" rx="1.6" fill="var(--icon-honey)" />
      <path d="M5 17h22m-19 10v1.5m16-1.5v1.5" />
      <path d="M12.5 10.5h7v3h-7Zm0 10h7v3h-7Z" fill="var(--icon-paper)" />
    </g>
    <g v-else-if="name === 'appearance'">
      <path
        d="M16.5 4.2C9.2 3.7 4.2 9.8 4.1 16.5S9.9 28 16.4 27.6c2.8-.2 4.3-1.4 3.5-3.7-.7-2-.6-3.3.8-4.2 1.6-1 3.4.1 5.1-1 2-1.3 2.2-4.2 1.3-6.5-1.4-4.1-5.6-7.6-10.6-8Z"
        fill="var(--icon-paper)"
      />
      <circle cx="10.3" cy="13.8" r="2" fill="var(--icon-clay)" />
      <circle cx="16" cy="9.6" r="2" fill="var(--icon-honey)" />
      <circle cx="22" cy="12.8" r="2" fill="var(--icon-sage)" />
      <circle cx="12" cy="21" r="1.5" />
    </g>
    <g v-else-if="name === 'features'" transform="rotate(-3 16 16)">
      <rect x="4" y="5" width="24" height="22" rx="3" fill="var(--icon-paper)" />
      <rect x="7.5" y="9" width="17" height="5" rx="2.5" fill="var(--icon-sage)" />
      <circle cx="21.5" cy="11.5" r="1.8" fill="var(--icon-paper)" />
      <rect x="7.5" y="17" width="17" height="5" rx="2.5" fill="var(--icon-clay)" />
      <circle cx="10.5" cy="19.5" r="1.8" fill="var(--icon-paper)" />
      <path d="M7.5 24.6h4m3 0h4" stroke-width="1.8" />
    </g>
    <g v-else-if="name === 'client'" transform="rotate(5 16 16)">
      <rect x="7.5" y="2.8" width="17" height="26.4" rx="3.2" fill="var(--icon-clay)" />
      <rect x="9.7" y="6" width="12.6" height="17.5" rx="1.3" fill="var(--icon-paper)" />
      <path d="M13.7 26.1h4.5m-5.9-8.5 7.4-7.4m-5.2 8.5 4.1-4.1" />
    </g>
    <g v-else-if="name === 'dawn'">
      <path d="M16 3v3m8.8.8-2 2.2m5.7 6.7h-3.1m-20.8 0H2.9m3.7-9 2.2 2.2" />
      <circle cx="16" cy="16" r="7" fill="var(--icon-honey)" />
      <path d="M3.3 20.8q5-2.8 11.6-.8 7.1 2.3 13.8-1.6l-.6 5q-5.3 3.2-13 1Q8.4 22.2 4.3 25Z" fill="var(--icon-sage)" />
    </g>
    <g v-else-if="name === 'dusk'">
      <path d="M18.4 4.2A11.7 11.7 0 1 0 27.1 21a10.5 10.5 0 0 1-8.7-16.8Z" fill="var(--icon-honey)" />
      <path d="m24.7 5.3 1.1 3.3 3.2 1.2-3.2 1.1-1.1 3.3-1.2-3.3-3.1-1.1 3.1-1.2Z" fill="var(--icon-paper)" />
    </g>
    <g v-else-if="name === 'language'">
      <path d="M23 3.8A12 12 0 0 1 10.3 24m5.5.9v2.5m-6 1.1q6.1-2.4 12.1 0Z" fill="var(--icon-honey)" />
      <g transform="rotate(-18 15.5 13.4)">
        <circle cx="15.5" cy="13.4" r="9.5" fill="var(--icon-sage)" />
        <ellipse cx="15.5" cy="13.4" rx="4" ry="9.4" />
        <path d="M6 13.4h19" />
      </g>
    </g>
    <g v-else-if="name === 'settings'" transform="rotate(-5 16 16)">
      <rect x="5" y="4" width="22" height="24" rx="3" fill="var(--icon-paper)" />
      <path d="M9 10.5h14m-14 5.7h14m-14 5.7h14" />
      <circle cx="12" cy="10.5" r="2.3" fill="var(--icon-clay)" />
      <circle cx="20" cy="16.2" r="2.3" fill="var(--icon-honey)" />
      <circle cx="14" cy="21.9" r="2.3" fill="var(--icon-sage)" />
    </g>
    <g v-else-if="name === 'members'">
      <circle cx="21.5" cy="10.3" r="4.3" fill="var(--icon-honey)" />
      <path d="M14.4 25.8v-3.1c.3-4.3 2.7-7.1 7.2-7.1s7.2 3.3 7.3 7.5v2.7Z" fill="var(--icon-sage)" />
      <circle cx="11.5" cy="12.2" r="4.8" fill="var(--icon-paper)" />
      <path d="M3.2 27.7v-2.4c.2-4.8 3.7-7.6 8.3-7.6s8.1 2.8 8.3 7.6v2.4Z" fill="var(--icon-clay)" />
    </g>
  </svg>
</template>

<script setup>
defineProps({ name: { type: String, required: true } })
</script>

<style scoped>
.settings-icon {
  --icon-ink: #674d36;
  --icon-paper: #f7e9ca;
  --icon-clay: #ce8b77;
  --icon-honey: #d7b17b;
  --icon-sage: #a4b18b;
  display: block;
  width: 36px;
  height: 36px;
  flex: none;
}
html.dark .settings-icon {
  --icon-ink: #efdcc0;
  --icon-paper: #735d48;
  --icon-clay: #955f51;
  --icon-honey: #8c6b40;
  --icon-sage: #606f51;
}
</style>
