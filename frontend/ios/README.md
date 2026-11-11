# ihomy iOS 客户端(Capacitor 8 壳)

把 `frontend/` 的 Web 产物装进 iOS 容器,并把 Android 侧已有的原生能力在 iOS 上补齐。
**只在 macOS + Xcode 15+ 上编译**(Windows 上只能改代码与静态复核)。

```bash
cd frontend
npm run ios:sync   # app 模式构建前端 + npx cap sync ios(同步 Web 产物与 SPM 依赖)
npm run ios:open   # 用 Xcode 打开 ios/App/App.xcodeproj
```

依赖走 **SPM**(`App/CapApp-SPM/Package.swift`),**没有 CocoaPods / Podfile**。

## 三个 target

| target | Bundle ID | 产物 | 说明 |
|--------|-----------|------|------|
| `App` | `top.ihomy.app` | App.app | Capacitor WebView 壳 + 13 个本地插件 |
| `IhomyWidgets` | `top.ihomy.app.IhomyWidgets` | .appex | WidgetKit 四张卡(今日/提醒/纪念日/记账),`@main IhomyWidgetsBundle` |
| `IhomyShare` | `top.ihomy.app.IhomyShare` | .appex | 分享扩展,收图/视频/文件/文本/URL 落 App Group 收件箱 |

App Group 统一 `group.top.ihomy.app`,三 target 共用同一份 `App/App.entitlements`(只有 App Group 一项)。
共享代码在 `App/App/IhomyShared.swift`(App Group 读写、`IhomyAPI`、`IhomyWidgetStore`、`IhomyWeather`、
`IhomyNotify`、`IhomyBackground`、`IhomyShareStore`、`IhomyCrashStore`),**两个扩展是独立进程,不加载 Capacitor**,
靠 `SWIFT_ACTIVE_COMPILATION_CONDITIONS = IHOMY_EXTENSION` 把主 App 专属代码挡在外面。

部署目标 iOS 15.0 / Swift 5.0;iOS 16、17 的 API(`requestFullAccessToEvents`、`containerBackground`、
`Button(intent:)`、`AppIntent`)全部 `#available` 包裹,低版本走退化分支。

## 13 个插件 ↔ 前端调用点

插件**必须**与 `frontend/src/native/*.js` 的 `registerPlugin('...')` 同名同参,并且在
`MainViewController.capacitorDidLoad()` 里 `bridge.registerPluginInstance(...)` —— 壳里没有插件清单,
少注册一个就是「JS 调用直接 rejected」。

| 插件(jsName) | Swift 文件 | 前端入口 | 能力 |
|--------------|-----------|---------|------|
| `IhomyBiometric` | IhomyPluginsLite | `native/biometric.js` | Face ID / Touch ID 校验 |
| `IhomyHaptics` | IhomyPluginsLite | `native/haptics.js`、`utils/haptics.js` | 触感五档(`UIImpactFeedbackGenerator` 等) |
| `IhomyScreen` | IhomyPluginsLite | `native/screen.js` | 防截屏(切后台/录屏遮罩)、常亮 |
| `IhomyCall` | IhomyPluginsLite | `native/call.js` | 麦克风/摄像头权限 |
| `IhomySpeech` | IhomyPluginsLite | `native/speech.js` | 语音识别(`SFSpeechRecognizer`) |
| `IhomyCamera` | IhomyPluginsLite | `native/camera.js` | 拍照(`UIImagePickerController`) |
| `IhomyCalendar` | IhomyPluginsData | `native/calendarExport.js` | EventKit 导出/清理/读事件 |
| `IhomyPhotoBackup` | IhomyPluginsData | `native/photoBackup.js` | PhotoKit 增量备份 |
| `IhomyShare` | IhomyPluginsData | `native/shareInbox.js` | 分享扩展收件箱 |
| `IhomyDiagnostics` | IhomyPluginsData | `native/diagnostics.js` | 崩溃日志列表/读取/清理 |
| `IhomyWidgets` | IhomyPluginsSystem | `native/widgets.js`、`native/live.js` | 卡片令牌/缓存刷新/常驻卡片/天气推送设置 |
| `IhomyUpdater` | IhomyPluginsSystem | `native/updater.js` | App Store 模式:`canInstall` 恒 false,下载安装一律 `not-supported` |
| `IhomyPip` | IhomyPluginsSystem | `native/pip.js` | 画中画(JS 注入 `webkitSetPresentationMode`) |

## 与 Android 的三处差异(不要「补齐」)

1. **应用内自更新 → App Store**:iOS 不允许 App 自己装包,设置页在 iOS 只显示「去 App Store」;
   `IhomyUpdater` 保留同名方法只为 JS 分支不炸。
2. **下拉栏磁贴 → 无对应物**:不实现,iOS 没有等价入口。
3. **30 秒常驻前台服务 → `BGAppRefresh`**:`top.ihomy.app.refresh` 由系统按使用习惯调度,**不保证频率**;
   标识必须与 `Info.plist` 的 `BGTaskSchedulerPermittedIdentifiers` 一致,否则 `register()` 直接崩。
   每次退后台都会 `IhomyBackground.schedule()` 重新排一次(系统跑完一轮不会自动续排)。

另:防截屏在 iOS 上**做不到真正拦截**,`setSecure` 只在切后台/录屏时盖遮罩。
本地通知待触发上限是 **64** 条(Android 是 500),`native/reminders.js` 按 60 排。

## 深链 / 快捷方式(改之前先看这段)

统一链路:`ihomy://...` → **Capacitor 原生通知 `CapacitorOpenURLNotification`** → App 插件
`notifyListeners('appUrlOpen', retainUntilConsumed: true)` → `native/deeplink.js` 的 `App.addListener`。

- **别用 `bridge.triggerJSEvent('appUrlOpen', target: 'window')`**:前端监听走 App 插件的原生通道,
  打 window 事件收不到(踩过一次)。
- 外链/`widgetURL`/分享回跳:URL 打开由 `SceneDelegateProxy` 接,冷启动会延到
  `capacitorViewDidAppear`(插件就绪)后再 post。
- 桌面长按快捷方式(8 条,见 `Info.plist` 的 `UIApplicationShortcutItems`)与通知冷启动:
  `AppDelegate`/`SceneDelegate` → `IhomyLaunch.capture()` 暂存,`MainViewController.capacitorDidLoad()`
  交出桥后再投递;`retainUntilConsumed` 保证 JS 监听晚注册也能补发。
- `AppDelegate` **不再**接 `launchOptions[.url]`,URL 型深链只走 `SceneDelegateProxy`,避免双投递。

## 工程文件注意事项

- `npx cap sync ios` 只重写 `App/CapApp-SPM/Package.swift` 和 `App/App/capacitor.config.json`(后者与本目录
  `App/App/config.xml`、`App/App/public/` 一起被 `ios/.gitignore` 忽略),**不会碰 `project.pbxproj`**。
  所以扩展 target、`Embed App Extensions` 构建阶段、扩展的 `INFOPLIST_FILE` / Bundle ID / `SKIP_INSTALL` /
  `IHOMY_EXTENSION` 编译条件全是手工写进 pbxproj 的:新增 Swift 文件也得手工加 `PBXBuildFile` + `PBXFileReference`
  + `Sources` 阶段,别指望 Xcode 之外的工具帮你同步。
- `Info.plist` **不声明** `UIMainStoryboardFile` / `UISceneStoryboardFile`:根视图控制器只由
  `SceneDelegate` 编程创建(`MainViewController`),这样插件注册点唯一。仓库里那份 `Base.lproj/Main.storyboard`
  是 `cap add ios` 的模板残留,已不被引用;**别把它接回去**,否则会多出来一个没注册插件的 `CAPBridgeViewController`。
- 不设 `server.iosScheme`:iOS origin 就是 `capacitor://localhost`,后端 `AuthCookie` 与 CORS 白名单都已同时
  接受它与 Android 的 `https://localhost`。
- 后台同步的接口基址读 `Info.plist` 的 `IHOMYApiBase`(默认 `https://ihomy.top/api`);扩展里
  `Bundle.main` 是扩展自己的 plist,读不到就落回 `IhomyAPI.defaultBase` 兜底。
- `App/App/public/` 是 `npm run ios:sync` 拷进来的 Web 产物,**不入库**;改前端后必须重新 sync 才会进包。

## 图标与启动图

`AppIcon.appiconset/AppIcon-512@2x.png`(1024×1024)与 `Splash.imageset/splash-2732x2732*.png`(2732×2732 ×3 档)
都是按 `frontend/public/favicon.svg` 的品牌画的(`#1F3A5F` 底 + 白色「家」),**不是** Capacitor 模板默认图,也没走
`@capacitor/assets` 生成:图标满幅、无圆角、无 alpha 通道(App Store 自己裁圆角,带 alpha 会被校验拒),
启动图白底居中圆角图标(与 PWA `background_color:#ffffff` 同口径)。换品牌时按同样约束重出即可,
三档 splash 用同一张图(contentMode 是 scaleAspectFill,居中元素不会被裁)。

## 尚未验证(需真机 / macOS)

`xcodebuild` 实编与签名;Face ID、EventKit 授权、PhotoKit 增量、后台刷新是否真被调度、四张卡片、
分享扩展、画中画这几条只能上机走查。架构、方法与参数已与 `frontend/src/native/*.js` 逐一对齐,
`IhomyShared.swift` 里打点用的接口字段也已与后端核对(`/schedule/occurrences`、`/public/calendar`、
`/public/weather`、`/book/summary`、`/notification/unread-count`、`/chat/unread`、
`/reminder/{id}/toggle-done`)。

⚠ 一个待实测的链接风险:`IhomyWidgets` 用了 `AppIntents`(iOS 16+ 的框架,交互勾选那段是 17+),扩展的
`IPHONEOS_DEPLOYMENT_TARGET` 跟着主工程设成 15.0。若 iOS 15 上加载该扩展时因 `AppIntents` 强链接失败,
两条路二选一:给 `IhomyWidgets` 加 `OTHER_LDFLAGS = ("-weak_framework", AppIntents)` 做弱链接,
或把该扩展的部署目标单独提到 16.0(App 仍保持 15.0)。实编后第一时间在 iOS 15 模拟器上确认一次。
