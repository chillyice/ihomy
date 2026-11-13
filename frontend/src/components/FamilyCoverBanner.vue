<!-- 家庭封面门脸:分享落地首屏的横幅(封面 + 族名 + 简介)。纯展示,不挂点击,不抢页面手势 -->
<template>
  <div class="fc-banner" :class="{ 'fc-strip': height > 0 }" :style="bannerStyle">
    <img class="fc-img" :src="family.coverImage" :alt="family.name || ''" />
    <div class="fc-text">
      <div v-if="family.name" class="fc-name">{{ family.name }}</div>
      <div v-if="family.description" class="fc-desc">{{ family.description }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 家庭信息(取 coverImage / name / description) */
  family: { type: Object, default: () => ({}) },
  /** 固定高度(px):光尘自由布局用低矮横条;0 表示按 16:9 */
  height: { type: Number, default: 0 },
})

const bannerStyle = computed(() => (props.height > 0 ? { height: props.height + 'px' } : null))
</script>

<style scoped>
.fc-banner {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 14px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.06);
}
.fc-banner.fc-strip {
  aspect-ratio: auto;
  border-radius: 12px;
}
.fc-img {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* 文字压在图上:只给底部渐暗,保证深浅封面都读得清,不加整块遮罩(图本身就是内容) */
.fc-text {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 28px 18px 14px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55), rgba(0, 0, 0, 0));
  color: #fff;
  pointer-events: none;
}
.fc-name {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fc-desc {
  margin-top: 3px;
  font-size: 12px;
  opacity: 0.86;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fc-banner.fc-strip .fc-text {
  padding: 18px 16px 10px;
}
.fc-banner.fc-strip .fc-name {
  font-size: 16px;
}
.fc-banner.fc-strip .fc-desc {
  font-size: 11px;
}
</style>
