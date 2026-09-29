// 可点击的非按钮元素无障碍补丁:自动补 role=button / tabindex=0,并支持 Enter/Space 触发 click。
// 用法:<div class="row" @click="fn" v-a11y-click>  (仅用于无原生语义的可点击容器)
export const a11yClick = {
  mounted(el) {
    if (!el.hasAttribute('role')) el.setAttribute('role', 'button')
    if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0')
    el.__a11yKey = (e) => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); el.click() }
    }
    el.addEventListener('keydown', el.__a11yKey)
  },
  unmounted(el) {
    if (el.__a11yKey) el.removeEventListener('keydown', el.__a11yKey)
  },
}
