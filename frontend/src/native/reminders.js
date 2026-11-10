// 提醒闹钟闭环(仅原生壳):拉 /schedule/occurrences → 与已注册通知对账 → 注册/取消/重排。
// 只做「客户端消费日期事实」:日期算法唯一来源是服务端 RepeatSchedule/AnniversaryDate,这里不推算。
// 对账事实来源 = 插件 getPending()(通知 extra 里带 key),不需要本地登记表,杀进程后依然幂等。
import { LocalNotifications } from '@capacitor/local-notifications'
import { App } from '@capacitor/app'
import { ElMessageBox } from 'element-plus'
import i18n from '@/i18n'
import { scheduleApi, reminderApi } from '@/api'
import { useUserStore } from '@/stores/user'

/** 通知通道 id。通道参数(声音/重要级)创建后不可改,想改只能换新 id 重新建一条——
 * 故「补上提示音」= 换 id,见 initReminders。 */
const CHANNEL_ID = 'reminders-v2'
/** 通道提示音:res/raw/ihomy_notify.wav(插件按 raw 资源名解析,不认 'default') */
const CHANNEL_SOUND = 'ihomy_notify'

/** 注册窗口:今天 00:00 → +30 天(计划 §6.4.2) */
const WINDOW_DAYS = 30
/** 单次注册上限,超出按时间升序取前 200(§6.4.2) */
const MAX_SCHEDULED = 200
/** 静默时段口径与服务端一致:23:00–06:00 的提醒不按点响(§6.4.5) */
const QUIET_END_MINUTES = 6 * 60
const QUIET_START_MINUTES = 23 * 60

/** 通知 id:FNV-1a(key) 取正;冲突时顺延 +1 直到空闲(概率极低,必须处理否则会误取消别人)。
 * takenIds 由调用方用「一次 getPending」的结果传入——桥调用有开销,绝不能每条各查一遍。 */
function idForKey(key, takenIds) {
  let hash = 0x811c9dc5
  for (let i = 0; i < key.length; i++) {
    hash ^= key.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  let id = hash & 0x7fffffff
  while (takenIds.has(id)) id = (id + 1) & 0x7fffffff
  takenIds.add(id)
  return id
}

/** 时刻「HH:mm」→ 当天分钟数;空时刻 = 全天事项,按 09:00 提醒 */
function minutesOf(time) {
  if (!time || !/^\d{2}:\d{2}$/.test(time)) return 9 * 60
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5))
}

/** 通知权限被拒的引导:每次会话最多弹一次,不静默失败也不反复打扰 */
let permissionGuideShown = false
function showPermissionGuide() {
  if (permissionGuideShown) return
  permissionGuideShown = true
  ElMessageBox.alert(i18n.global.t('reminder.permissionGuide'), i18n.global.t('reminder.permissionTitle'), {
    confirmButtonText: i18n.global.t('reminder.gotIt'),
    type: 'warning',
  }).catch(() => {})
}

/** 精确闹钟未授权的引导(一次性):闹钟仍会响,只是可能被系统推迟几分钟(计划 R2 的降级口径) */
let exactGuideShown = false
function showExactAlarmGuide() {
  if (exactGuideShown) return
  exactGuideShown = true
  console.warn('[reminders] 精确闹钟未授权,本次闹钟按非精确注册(可能延后几分钟)')
}

/**
 * 把 occurrence 的 date+time 换算成设备本地触发时刻(Date,墙上时间,不做时区换算)。
 * 落在静默窗口的按 §6.4.5 延后:23:00 之后 → 次日 06:00(标题加「(昨晚)」);06:00 之前 → 当天 06:00。
 */
function fireDate(date, time) {
  const [y, m, d] = date.split('-').map(Number)
  const mins = minutesOf(time)
  const fire = new Date(y, m - 1, d, Math.floor(mins / 60), mins % 60, 0, 0)
  if (mins >= QUIET_START_MINUTES) {
    fire.setDate(fire.getDate() + 1)
    fire.setHours(6, 0, 0, 0)
    return { at: fire, late: true }
  }
  if (mins < QUIET_END_MINUTES) {
    fire.setHours(6, 0, 0, 0)
  }
  return { at: fire, late: false }
}

/** 通知 id 是否已过触发时刻(对账时跳过,避免取消不掉的历史噪音) */
function isPast(date, time) {
  const [y, m, d] = date.split('-').map(Number)
  const mins = minutesOf(time)
  const at = new Date(y, m - 1, d, Math.floor(mins / 60), mins % 60)
  return at.getTime() <= Date.now()
}

/** 单条 occurrence → 插件 schedule 参数;不可注册(已过时刻)返回 null */
function toRequest(item, takenIds, exactAllowed) {
  const { at, late } = fireDate(item.date, item.time)
  if (at.getTime() <= Date.now()) return null
  const key = item.key
  const id = idForKey(key, takenIds)
  const title = item.title + (late ? ' ' + i18n.global.t('reminder.lateNightTag') : '')
  return {
    id,
    title,
    body: buildBody(item),
    schedule: { at, allowWhileIdle: true, exact: exactAllowed },
    channelId: CHANNEL_ID,
    smallIcon: 'ic_notification',
    extra: { key, kind: item.kind, link: item.link || '/plan?date=' + item.date, date: item.date },
    // REMINDER/TASK 才有「完成」语义;其余 kind 点击进对应页
    actionTypeId: item.kind === 'REMINDER' || item.kind === 'TASK' ? 'REMINDER_ACTIONS' : undefined,
  }
}

function buildBody(item) {
  const t = i18n.global.t
  if (item.time) return t('reminder.notifBodyTimed', { time: item.time })
  return t('reminder.notifBodyAllDay')
}

/**
 * 同步入口:权限就绪才拉接口;已登录才拉(游客没有提醒语义)。
 * 对账算法(§6.4.3):远端 key 集合与已注册 extra.key 比对,新增注册、变更重排、消失取消。
 * 重入保护:启动/回前台/完成动作/手动都会触发,并发跑两份会把对账做花(实测桥调用被拖死),在途时直接跳过。
 */
let syncing = false
export async function syncNotifications() {
  if (syncing) return { registered: 0, cancelled: 0, reason: 'syncing' }
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) return { registered: 0, cancelled: 0, reason: 'guest' }
  syncing = true
  try {
    return await doSync()
  } finally {
    syncing = false
  }
}

async function doSync() {
  try {
    const perm = await LocalNotifications.checkPermissions()
    if (perm.display !== 'granted') {
      const req = await LocalNotifications.requestPermissions()
      if (req.display !== 'granted') {
        showPermissionGuide()
        return { registered: 0, cancelled: 0, reason: 'permission' }
      }
    }
  } catch (e) {
    console.warn('[reminders] 权限检查失败', e)
    return { registered: 0, cancelled: 0, reason: 'permission' }
  }

  // 精确闹钟(Android 12+)默认拒绝:必须先查再定 exact。
  // 绝不能带着 exact:true 盲发——插件在未授权时会拉起系统「闹钟和提醒」设置页并挂住本次调用,
  // 同步就永远停在这(实测整条链路死锁)。未授权 → 全部降级 inexact + 一次性引导。
  let exactAllowed = false
  try {
    const st = await LocalNotifications.checkExactNotificationSetting()
    exactAllowed = st && (st.state === 'granted' || st === 'granted')
  } catch (e) {
    console.warn('[reminders] 精确闹钟状态查询失败,按未授权降级', e)
  }
  if (!exactAllowed) showExactAlarmGuide()

  let items
  try {
    const from = new Date()
    const to = new Date()
    to.setDate(to.getDate() + WINDOW_DAYS - 1)
    const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const res = await scheduleApi.occurrences(fmt(from), fmt(to))
    items = (res && res.items) || []
  } catch (e) {
    console.warn('[reminders] 拉取日程失败,保持现有闹钟不动', e)
    return { registered: 0, cancelled: 0, reason: 'network' }
  }

  // 已完成的 / 只读展示类不注册;先按 key 去重(理论不会重,接口同 key 只会出一条)
  const remote = new Map()
  for (const it of items) {
    if (!it || it.done || !it.key) continue
    if (remote.size >= MAX_SCHEDULED) break
    if (isPast(it.date, it.time)) continue
    remote.set(it.key, it)
  }

  let pending
  try {
    pending = (await LocalNotifications.getPending()).notifications
  } catch (e) {
    console.warn('[reminders] 读取已注册通知失败', e)
    return { registered: 0, cancelled: 0, reason: 'pending' }
  }
  const localByKey = new Map()
  const takenIds = new Set()
  for (const p of pending) {
    if (typeof p.id === 'number') takenIds.add(p.id)
    const k = p.extra && p.extra.key
    if (k) localByKey.set(k, p)
  }

  // 变更检测:触发时刻、标题或通道变了才重排;同 key 同内容不折腾(避免每天重复 set 闹钟)。
  // 通道也进比对——升级到带提示音的通道后,旧通道上已排的闹钟必须重建才会响。
  const toSchedule = []
  const toCancel = []
  for (const [key, item] of remote) {
    const cur = localByKey.get(key)
    const { at } = fireDate(item.date, item.time)
    const curAt = cur && cur.schedule && cur.schedule.at ? new Date(cur.schedule.at).getTime() : null
    if (cur && curAt === at.getTime() && cur.channelId === CHANNEL_ID) {
      localByKey.delete(key)
      continue
    }
    const req = toRequest(item, takenIds, exactAllowed)
    if (!req) continue
    if (cur) toCancel.push(cur.id)
    toSchedule.push(req)
    localByKey.delete(key)
  }
  // 剩下的 local key = 远端已消失(完成/删除/窗口外),全部取消
  for (const p of localByKey.values()) toCancel.push(p.id)

  if (toCancel.length) {
    try {
      await LocalNotifications.cancel({ notifications: toCancel.map((id) => ({ id })) })
    } catch (e) {
      console.warn('[reminders] 取消通知失败', e)
    }
  }
  if (toSchedule.length) {
    try {
      await LocalNotifications.schedule({ notifications: toSchedule })
    } catch (e) {
      // 理论走不到(exact 已按授权位定好);万一系统拒了,降级 inexact 兜底再试一次
      try {
        await LocalNotifications.schedule({
          notifications: toSchedule.map((n) => ({ ...n, schedule: { ...n.schedule, exact: false } })),
        })
        console.warn('[reminders] 注册降级为非精确后成功', e)
      } catch (e2) {
        console.warn('[reminders] 注册闹钟失败', e2)
      }
    }
  }
  return { registered: toSchedule.length, cancelled: toCancel.length }
}

/** 通知点击/动作:done=完成并重排;snooze=本地 +10 分钟(不回写服务端,§6.4.4) */
async function onActionPerformed(notification, actionId) {
  const extra = (notification && notification.extra) || {}
  if (actionId === 'snooze') {
    const at = new Date(Date.now() + 10 * 60 * 1000)
    try {
      await LocalNotifications.schedule({
        notifications: [
          {
            id: notification.id,
            title: notification.title,
            body: notification.body,
            schedule: { at, allowWhileIdle: true },
            channelId: CHANNEL_ID,
            smallIcon: 'ic_notification',
            extra,
            actionTypeId: extra.kind === 'REMINDER' || extra.kind === 'TASK' ? 'REMINDER_ACTIONS' : undefined,
          },
        ],
      })
    } catch (e) {
      console.warn('[reminders] 稍后再响失败', e)
    }
    return
  }
  // 点击通知本体或「完成」动作:先深链进日程页(完成动作也要让界面跟上来)
  if (extra.link) openLink(extra.link)
  if (actionId === 'done' && extra.kind && (extra.kind === 'REMINDER' || extra.kind === 'TASK') && extra.date) {
    try {
      const id = Number(String(extra.key || '').split(':')[1])
      if (id) await reminderApi.toggleDone(id, extra.date)
    } catch (e) {
      console.warn('[reminders] 通知内完成失败', e)
    }
  }
  syncNotifications().catch(() => {})
}

function openLink(link) {
  // 深链统一口径:deeplink 模块监听本事件做 router.push,与 appUrlOpen(外部唤起)共用一套落点
  window.dispatchEvent(new CustomEvent('ihomy-deeplink', { detail: link }))
}

/** 通知通道与动作注册 + 触发时机接线(App 启动 / 回到前台) */
export async function initReminders() {
  try {
    // 注意 API 名是 createChannel(registerChannel 不存在);importance ≥3 才有横幅。
    // 通道创建后参数被系统冻结(改代码不生效),所以「响铃」这项必须落在新 id 上并显式给 sound:
    // 插件把 sound 解析成 android.resource://<包名>/raw/<名>(不认 'default'),不传就是静音通道。
    await LocalNotifications.createChannel({
      id: CHANNEL_ID,
      name: i18n.global.t('reminder.channelName'),
      description: i18n.global.t('reminder.channelDesc'),
      importance: 4, // IMPORTANCE_HIGH:横幅 + 提示音
      visibility: 1, // PUBLIC:锁屏可见标题
      sound: CHANNEL_SOUND,
      vibration: true,
      lights: true,
    })
    // 旧的无声音通道(reminders)留着只会让用户看到两条同名通道,直接删掉
    await LocalNotifications.deleteChannel({ id: 'reminders' }).catch(() => {})
  } catch (e) {
    console.warn('[reminders] 创建通知通道失败', e)
  }
  try {
    await LocalNotifications.registerActionTypes({
      types: [
        {
          id: 'REMINDER_ACTIONS',
          actions: [
            { id: 'done', title: i18n.global.t('reminder.actionDone'), requiresAuthentication: false },
            { id: 'snooze', title: i18n.global.t('reminder.actionSnooze'), requiresAuthentication: false },
          ],
        },
      ],
    })
  } catch (e) {
    console.warn('[reminders] 注册通知动作失败', e)
  }
  LocalNotifications.addListener('localNotificationActionPerformed', (evt) => {
    onActionPerformed(evt.notification, evt.actionId).catch((e) => console.warn('[reminders] 通知动作处理失败', e))
  })
  // 回到前台重排:内容可能被网页端改过
  App.addListener('appStateChange', (state) => {
    if (state.isActive) syncNotifications().catch(() => {})
  })
  // 启动即同步一次(延迟几秒避开首屏请求风暴)
  setTimeout(() => syncNotifications().catch(() => {}), 4000)
}
