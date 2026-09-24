<!-- 咔哒(Kada)软件首页 —— 独立下载页,托管在 ihomy 域名 https://ihomy.top/kada。
     软件安装包(kada.exe)放在 ihomy 服务器 nginx 的 /files/ 静态目录(/opt/ihomy/uploads/kada/),
     本页通过下载链接分发。路由 meta.standalone 让 App.vue 不套 ihomy 外壳,直接全屏渲染。 -->
<template>
  <div class="kada">
    <div class="kada-wrap">
      <!-- 顶栏 -->
      <header class="ktop">
        <div class="kmark" aria-hidden="true"><span>K</span></div>
        <div class="kname">
          <span class="kname-cn">咔哒</span>
          <span class="kname-en">Kada</span>
        </div>
      </header>

      <!-- 主视觉 -->
      <section class="khero">
        <div class="kkey" aria-hidden="true">
          <span class="kkey-top">K</span>
          <span class="kkey-base"></span>
        </div>
        <h1 class="ktitle">咔哒 <span class="ktitle-en">Kada</span></h1>
        <p class="ktag">轻快跨平台快捷键工具</p>
        <p class="ksub">全局快捷键 · 改键 · 宏 · 文本扩展 —— 纯本地运行，托盘常驻</p>

        <div class="kcta">
          <a class="kdl" :href="downloadUrl" download>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" /></svg>
            下载 Windows 版
          </a>
        </div>

        <div class="kmeta">
          <span class="kchip">Windows x64</span>
          <span class="kchip">v{{ version }}</span>
          <span class="kchip">≈ {{ fileSize }}</span>
        </div>

        <div class="kplatforms">
          <span class="kp"><i class="kdot kdot-on"></i>Windows</span>
          <span class="kp"><i class="kdot kdot-on"></i>Linux</span>
          <span class="kp"><i class="kdot"></i>macOS 规划中</span>
        </div>
      </section>

      <!-- 特性 -->
      <section class="kfeat">
        <div v-for="f in features" :key="f.title" class="kcard">
          <div class="kcard-ic" v-html="f.icon"></div>
          <h3 class="kcard-t">{{ f.title }}</h3>
          <p class="kcard-d">{{ f.desc }}</p>
        </div>
      </section>

      <!-- 安装提示 -->
      <section class="knote">
        <div class="knote-t">首次运行提示</div>
        <p>咔哒为本地小工具、暂未做代码签名，Windows 可能弹出「Windows 已保护你的电脑」。点「更多信息」→「仍要运行」即可。</p>
      </section>

      <!-- 页脚 -->
      <footer class="kfoot">
        <span>咔哒 Kada v{{ version }}</span>
        <span class="kdot-sep">·</span>
        <span>MIT License</span>
        <span class="kdot-sep">·</span>
        <span>由 ihomy 托管</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'

// 下载配置:安装包放在 ihomy 服务器 /opt/ihomy/uploads/kada/kada.exe(nginx /files/ alias 托管),
// 换版本时更新 filename 与 version 即可(建议版本化文件名避免浏览器缓存旧包)。
const filename = 'kada.exe'
const downloadUrl = `/files/kada/${filename}`
const version = '0.1.0'
const fileSize = '9.7 MB'

const features = [
  {
    title: '全局快捷键',
    desc: '自定义组合键触发文本输入与动作链（宏），任一触发组合命中即执行。',
    icon: '<svg viewBox="0 0 24 24"><rect x="3" y="7" width="18" height="12" rx="2"/><path d="M7 10h.01M11 10h.01M15 10h.01M19 10h.01M7 14h.01M11 14h.01M15 14h.01M7 16h6"/></svg>',
  },
  {
    title: '改键',
    desc: '任意键改发另一键（如 CapsLock → Ctrl），支持短按/长按/双击/三击、单次与粘滞修饰、多层键位。',
    icon: '<svg viewBox="0 0 24 24"><path d="M4 5h16v14H4z"/><path d="M8 9h8M12 9v6"/></svg>',
  },
  {
    title: '宏录制',
    desc: '录制真实按键序列，自动折叠重复、补齐停顿，一键回放整串操作。',
    icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="7"/><path d="M12 9v3l2 2"/></svg>',
  },
  {
    title: '文本扩展',
    desc: '输入触发词 + 后缀自动展开，支持 {date}/{time}/{clipboard} 动态片段。',
    icon: '<svg viewBox="0 0 24 24"><path d="M4 5h16M4 12h16M4 19h10"/></svg>',
  },
  {
    title: '键序列 / 和弦 / 层',
    desc: 'leader key 按键序列、多键同时按下的和弦触发，以及可切换的多层键位。',
    icon: '<svg viewBox="0 0 24 24"><path d="M7 7h4v4H7zM13 7h4v4h-4zM7 13h4v4H7zM13 13h4v4h-4z"/></svg>',
  },
  {
    title: '托盘常驻 · 纯本地',
    desc: '关窗隐藏到托盘不退出，无后端、无数据库，配置 JSON 落盘、可跨平台同步。',
    icon: '<svg viewBox="0 0 24 24"><path d="M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z"/><path d="M9 12l2 2 4-4"/></svg>',
  },
]

onMounted(() => {
  document.title = '咔哒 Kada'
})
onBeforeUnmount(() => {
  document.title = 'ihomy'
})
</script>

<style scoped>
.kada {
  min-height: 100vh;
  background:
    radial-gradient(1100px 560px at 50% -12%, rgba(240, 165, 74, 0.16), transparent 60%),
    radial-gradient(760px 480px at 88% 6%, rgba(120, 170, 255, 0.08), transparent 60%),
    #0c0f14;
  color: #e9edf3;
}
.kada-wrap {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 24px;
}

/* 顶栏 */
.ktop {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 26px 0 4px;
}
.kmark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(145deg, #f7b563, #e8943a);
  color: #20130a;
  font-weight: 800;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(240, 165, 74, 0.35);
}
.kname {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.kname-cn {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.kname-en {
  font-size: 13px;
  font-weight: 600;
  color: #8b95a3;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* 主视觉 */
.khero {
  text-align: center;
  padding: 64px 0 56px;
}
.kkey {
  position: relative;
  width: 76px;
  height: 76px;
  margin: 0 auto 28px;
}
.kkey-top {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 76px;
  height: 66px;
  border-radius: 16px;
  background: linear-gradient(160deg, #ffd391 0%, #f0a54a 55%, #e08a2f 100%);
  color: #20130a;
  font-size: 34px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 28px rgba(240, 165, 74, 0.42), inset 0 1px 0 rgba(255, 255, 255, 0.45);
}
.kkey-base {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 90px;
  height: 18px;
  border-radius: 6px;
  background: linear-gradient(180deg, #c9782b, #a8601f);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.45);
}
.ktitle {
  font-size: 52px;
  font-weight: 800;
  letter-spacing: 0.01em;
  margin: 0 0 10px;
  line-height: 1.1;
}
.ktitle-en {
  font-size: 26px;
  font-weight: 700;
  color: #8b95a3;
  letter-spacing: 0.06em;
  margin-left: 6px;
}
.ktag {
  font-size: 19px;
  font-weight: 600;
  color: #f0a54a;
  margin: 0 0 8px;
}
.ksub {
  font-size: 15px;
  color: #8b95a3;
  margin: 0;
}

/* 下载 CTA */
.kcta {
  margin-top: 34px;
}
.kdl {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 14px 34px;
  border-radius: 999px;
  background: linear-gradient(145deg, #f7b563, #e8943a);
  color: #20130a;
  font-size: 16px;
  font-weight: 700;
  text-decoration: none;
  box-shadow: 0 10px 28px rgba(240, 165, 74, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.4);
  transition: transform 0.16s ease, box-shadow 0.16s ease, filter 0.16s ease;
}
.kdl svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.kdl:hover {
  transform: translateY(-2px);
  filter: brightness(1.04);
  box-shadow: 0 14px 34px rgba(240, 165, 74, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.4);
}
.kmeta {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}
.kchip {
  font-size: 12.5px;
  color: #9aa3af;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

/* 平台支持 */
.kplatforms {
  display: flex;
  justify-content: center;
  gap: 22px;
  margin-top: 26px;
}
.kp {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13.5px;
  color: #9aa3af;
}
.kdot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
}
.kdot-on {
  background: #5ecf82;
  box-shadow: 0 0 8px rgba(94, 207, 130, 0.6);
}

/* 特性卡片 */
.kfeat {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  padding: 8px 0 32px;
}
.kcard {
  padding: 20px 20px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}
.kcard:hover {
  border-color: rgba(240, 165, 74, 0.4);
  background: rgba(255, 255, 255, 0.05);
  transform: translateY(-2px);
}
.kcard-ic {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: rgba(240, 165, 74, 0.13);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}
.kcard-ic :deep(svg) {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: #f0a54a;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.kcard-t {
  font-size: 15.5px;
  font-weight: 700;
  margin: 0 0 7px;
  color: #eef1f6;
}
.kcard-d {
  font-size: 13px;
  line-height: 1.65;
  color: #8b95a3;
  margin: 0;
}

/* 安装提示 */
.knote {
  padding: 18px 20px;
  border-radius: 14px;
  background: rgba(240, 165, 74, 0.08);
  border: 1px solid rgba(240, 165, 74, 0.22);
  margin-bottom: 8px;
}
.knote-t {
  font-size: 13.5px;
  font-weight: 700;
  color: #f0a54a;
  margin-bottom: 6px;
}
.knote p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: #9aa3af;
}

/* 页脚 */
.kfoot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 30px 0 40px;
  font-size: 12.5px;
  color: #6b7480;
}
.kdot-sep {
  opacity: 0.6;
}

@media (max-width: 720px) {
  .khero { padding: 44px 0 40px; }
  .ktitle { font-size: 40px; }
  .ktitle-en { font-size: 20px; }
  .kfeat { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 460px) {
  .kfeat { grid-template-columns: 1fr; }
  .kplatforms { flex-wrap: wrap; gap: 12px 18px; }
}
</style>
