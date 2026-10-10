import { describe, it, expect } from 'vitest'
import { isMobileHomePath, mobileHeaderTitleKey } from './mobileNav'

// 回归:经典首页由 '/' 迁到 '/home' 后,移动三 Tab 判据一度仍只认 '/',导致移动端渲染桌面首页且无底部 TabBar
describe('isMobileHomePath', () => {
  it('同时认 /home 与 /(路由把 / 重定向到 /home)', () => {
    expect(isMobileHomePath('/home')).toBe(true)
    expect(isMobileHomePath('/')).toBe(true)
  })

  it('子页面不算首页(否则子页面会被塞进 TabBar 外壳)', () => {
    for (const p of ['/blog', '/help', '/tools', '/home/extra', '/diary/book/2']) {
      expect(isMobileHomePath(p)).toBe(false)
    }
  })
})

describe('mobileHeaderTitleKey', () => {
  it('V10.26 补齐的路径都有标题键(此前进了子页面模式页头空白)', () => {
    const paths = [
      '/announcement',
      '/recycle',
      '/iot',
      '/weather',
      '/storage/files',
      '/tools',
      '/tools/mindmap',
      '/tools/ai-playground',
      '/tools/light-lab',
      '/tools/flash',
      '/tools/gba',
      '/tools/plant',
      '/games',
      '/games/petlink',
      '/games/play/12',
      '/help',
      '/reset-password',
    ]
    for (const p of paths) {
      expect(mobileHeaderTitleKey(p), p).toBeTruthy()
    }
  })

  it('详情页与新建/编辑页取不同文案键(新建无 id 也要命中文案,旧判据 includes("/edit/") 会漏)', () => {
    expect(mobileHeaderTitleKey('/blog/9')).toBe('mobile.title.blogDetail')
    expect(mobileHeaderTitleKey('/blog/edit')).toBe('mobile.title.blogEdit')
    expect(mobileHeaderTitleKey('/blog/edit/9')).toBe('mobile.title.blogEdit')
    expect(mobileHeaderTitleKey('/library/3')).toBe('mobile.title.bookDetail')
    expect(mobileHeaderTitleKey('/library/edit')).toBe('mobile.title.bookEdit')
    expect(mobileHeaderTitleKey('/kitchen/recipe/8')).toBe('mobile.title.recipeDetail')
    expect(mobileHeaderTitleKey('/kitchen/recipe/8/edit')).toBe('mobile.title.recipeEdit')
    expect(mobileHeaderTitleKey('/kitchen/recipe/new')).toBe('mobile.title.recipeEdit')
    expect(mobileHeaderTitleKey('/kitchen/ingredients')).toBe('mobile.title.ingredients')
    expect(mobileHeaderTitleKey('/diary/book/2')).toBe('diary.title')
    expect(mobileHeaderTitleKey('/diary/edit')).toBe('mobile.title.diaryWrite')
    expect(mobileHeaderTitleKey('/diary/edit/2')).toBe('mobile.title.diaryWrite')
  })

  it('独立产品页与未知路径返回空串(页头只留返回按钮)', () => {
    for (const p of ['/kada', '/wallpaper', '/nope', '']) {
      expect(mobileHeaderTitleKey(p)).toBe('')
    }
  })
})
