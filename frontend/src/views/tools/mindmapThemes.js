/**
 * 脑图主题定义:npm 包 simple-mind-map@0.14.0 只内置 default 主题,
 * 其余主题(classic/dark/blackGold 等)是库官方 web 项目的文件,不在 npm 包内。
 * 此处用 MindMap.defineTheme(default 主题覆盖合并)自注册 7 个主题。
 * 配色取自 mind-map 官方 web 项目 themes 目录(github.com/wanglin2/mind-map)。
 * 每个主题只需覆盖与 default 不同的键;结构:根节点 root / 二级 second / 其余 node / 连线 lineColor。
 */

const THEMES = {
  classic: {
    lineColor: '#87ad7f',
    root: { fillColor: '#47b262', color: '#fff', startColor: '#47b262' },
    second: { fillColor: '#ffffff', color: '#565656', borderColor: '#daech1' },
    node: { fillColor: '#a2d08f', color: '#ffffff', borderColor: '#a2d08f' },
  },
  dark: {
    lineColor: '#54494c',
    root: { fillColor: '#3d3242', color: '#c8c0c3', startColor: '#3d3242' },
    second: { fillColor: '#54494c', color: '#c8c0c3', borderColor: '#54494c' },
    node: { fillColor: '#585062', color: '#c8c0c3', borderColor: '#585062' },
  },
  blackGold: {
    lineColor: '#d6b25c',
    root: { fillColor: '#242018', color: '#d6b25c', startColor: '#242018' },
    second: { fillColor: '#33302c', color: '#d6b25c', borderColor: '#33302c' },
    node: { fillColor: '#33302c', color: '#ded9cd', borderColor: '#d6b25c' },
  },
  avocado: {
    lineColor: '#94bf8b',
    root: { fillColor: '#94bf8b', color: '#ffffff', startColor: '#94bf8b' },
    second: { fillColor: '#f5f9ec', color: '#565656', borderColor: '#f5f9ec' },
    node: { fillColor: '#f5f9ec', color: '#565656', borderColor: '#f5f9ec' },
  },
  mintGreen: {
    lineColor: '#42b883',
    root: { fillColor: '#42b883', color: '#fff', startColor: '#42b883' },
    second: { fillColor: '#f2fff9', color: '#424242', borderColor: '#f2fff9' },
    node: { fillColor: '#f2fff9', color: '#424242', borderColor: '#f2fff9' },
  },
  blueSky: {
    lineColor: '#67a7e0',
    root: { fillColor: '#2d71c8', color: '#fff', startColor: '#2d71c8' },
    second: { fillColor: '#eef5fd', color: '#424242', borderColor: '#eef5fd' },
    node: { fillColor: '#eef5fd', color: '#424242', borderColor: '#eef5fd' },
  },
  colored: {
    lineColor: '#597ef7',
    root: { fillColor: '#597ef7', color: '#fff', startColor: '#597ef7' },
    second: { fillColor: '#85a5ff', color: '#fff', borderColor: '#85a5ff' },
    node: { fillColor: '#d6e4ff', color: '#31465f', borderColor: '#d6e4ff' },
  },
}

/** 注册全部自定义主题(模块级一次) */
export const defineMindmapThemes = (MindMap) => {
  for (const [name, config] of Object.entries(THEMES)) {
    MindMap.defineTheme(name, config)
  }
}
