package top.ihomy.app.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 系统日历单向导出:把服务端算好的提醒/纪念日/还款日逐条写进本机日历(专属「ihomy」日历),
 * 即使 App 被系统清掉,系统日历的闹钟仍会响——这是国内 OEM 后台管控下的可靠性兜底。
 *
 * <p>约定:
 * <ul>
 *   <li>只写不回读业务数据;每次导出先清空本日历再全量插入(幂等,不依赖本地登记表);</li>
 *   <li>日期/时刻用服务端给的 timeZone 换算(客户端不做历法推算);</li>
 *   <li>全天事项按 UTC 零点 + ALL_DAY=1 写入,定时事项写 30 分钟块并挂 0 分钟提醒。</li>
 * </ul>
 */
@CapacitorPlugin(
        name = "IhomyCalendar",
        permissions = {
            @Permission(
                    alias = "calendar",
                    strings = { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR })
        })
public class CalendarSyncPlugin extends Plugin {

    /** 账号与日历标识固定,用于每次导出时找回同一个日历(显示名可随语言变) */
    private static final String ACCOUNT_NAME = "ihomy";
    private static final String ACCOUNT_TYPE = "com.ihomy";
    private static final String CALENDAR_NAME = "ihomy";
    private static final String DEFAULT_DISPLAY_NAME = "ihomy";
    private static final int CALENDAR_COLOR = 0xFF1F3A5F;
    /** 定时事项默认占用 30 分钟 */
    private static final long EVENT_DURATION_MS = 30L * 60L * 1000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("state", getPermissionState("calendar").toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (getPermissionState("calendar") == PermissionState.GRANTED) {
            JSObject ret = new JSObject();
            ret.put("state", PermissionState.GRANTED.toString());
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias("calendar", call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("state", getPermissionState("calendar").toString());
        call.resolve(ret);
    }

    /** 导出状态:权限、日历是否存在、本日历当前事件数(读权限缺失时返回 0) */
    @PluginMethod
    public void status(PluginCall call) {
        io.execute(() -> {
            try {
                JSObject ret = new JSObject();
                boolean granted = getPermissionState("calendar") == PermissionState.GRANTED;
                ret.put("granted", granted);
                long calendarId = granted ? findCalendarId() : -1L;
                ret.put("calendarId", calendarId > 0 ? calendarId : JSONObject.NULL);
                ret.put("count", calendarId > 0 ? countEvents(calendarId) : 0);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("读取日历状态失败:" + e.getMessage(), e);
            }
        });
    }

    /**
     * 全量导出:清空本日历里我们自己的事件后按 items 重新写入。
     * items 每项 {key,title,description,date(YYYY-MM-DD),time(HH:mm 可空),timeZone,link}
     */
    @PluginMethod
    public void export(PluginCall call) {
        JSArray items = call.getArray("items");
        String displayName = call.getString("calendarName", DEFAULT_DISPLAY_NAME);
        if (items == null) {
            call.reject("items required");
            return;
        }
        if (getPermissionState("calendar") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED");
            return;
        }
        io.execute(() -> {
            try {
                long calendarId = ensureCalendar(displayName == null ? DEFAULT_DISPLAY_NAME : displayName);
                if (calendarId <= 0) {
                    call.reject("创建日历失败");
                    return;
                }
                int removed = getContext()
                        .getContentResolver()
                        .delete(CalendarContract.Events.CONTENT_URI,
                                CalendarContract.Events.CALENDAR_ID + "=?",
                                new String[] { String.valueOf(calendarId) });

                int inserted = 0;
                int skipped = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject raw = items.optJSONObject(i);
                    if (raw == null) continue;
                    if (writeEvent(calendarId, raw)) inserted++;
                    else skipped++;
                }

                JSObject ret = new JSObject();
                ret.put("calendarId", calendarId);
                ret.put("inserted", inserted);
                ret.put("removed", Math.max(removed, 0));
                ret.put("skipped", skipped);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("PERMISSION_DENIED", e);
            } catch (Exception e) {
                call.reject("写入日历失败:" + e.getMessage(), e);
            }
        });
    }

    /** 清空本日历(保留日历本身,便于再次导出) */
    @PluginMethod
    public void clear(PluginCall call) {
        if (getPermissionState("calendar") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED");
            return;
        }
        io.execute(() -> {
            try {
                long calendarId = findCalendarId();
                int removed = 0;
                if (calendarId > 0) {
                    removed = getContext()
                            .getContentResolver()
                            .delete(CalendarContract.Events.CONTENT_URI,
                                    CalendarContract.Events.CALENDAR_ID + "=?",
                                    new String[] { String.valueOf(calendarId) });
                }
                JSObject ret = new JSObject();
                ret.put("removed", Math.max(removed, 0));
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("PERMISSION_DENIED", e);
            } catch (Exception e) {
                call.reject("清空日历失败:" + e.getMessage(), e);
            }
        });
    }

    // ---- CalendarContract 读写 ----

    /**
     * 读取系统日历区间内的事项(只读,供日程页合并展示)。
     * 跳过本 App 自己的「ihomy」日历,避免与站内数据重复;调用方还需按标题+日期+时间再去一次重。
     * 入参 {from,to} 为毫秒时间戳;返回 {events:[{id,title,begin,end,allDay,calendarName}]}。
     */
    @PluginMethod
    public void readEvents(PluginCall call) {
        Long from = call.getLong("from");
        Long to = call.getLong("to");
        if (from == null || to == null || to <= from) {
            call.reject("from/to required");
            return;
        }
        if (getPermissionState("calendar") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED");
            return;
        }
        io.execute(() -> {
            JSArray out = new JSArray();
            try {
                Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(builder, from);
                ContentUris.appendId(builder, to);
                Uri uri = builder.build();
                String[] projection = {
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY,
                        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                };
                long ownId = findCalendarId();
                String selection = null;
                String[] args = null;
                if (ownId > 0) {
                    selection = CalendarContract.Instances.CALENDAR_ID + "!=?";
                    args = new String[] { String.valueOf(ownId) };
                }
                try (Cursor c = resolver().query(uri, projection, selection, args,
                        CalendarContract.Instances.BEGIN + " ASC")) {
                    while (c != null && c.moveToNext() && out.length() < 300) {
                        JSObject e = new JSObject();
                        e.put("id", c.getLong(0));
                        String title = c.getString(1);
                        e.put("title", title == null ? "" : title);
                        e.put("begin", c.getLong(2));
                        e.put("end", c.getLong(3));
                        e.put("allDay", c.getInt(4) == 1);
                        String cal = c.getString(5);
                        e.put("calendarName", cal == null ? "" : cal);
                        out.put(e);
                    }
                }
                JSObject ret = new JSObject();
                ret.put("events", out);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("PERMISSION_DENIED", e);
            } catch (Exception e) {
                call.reject("读取系统日历失败:" + e.getMessage(), e);
            }
        });
    }

    private ContentResolver resolver() {
        return getContext().getContentResolver();
    }

    private long findCalendarId() {
        String selection = CalendarContract.Calendars.ACCOUNT_NAME + "=? AND "
                + CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND "
                + CalendarContract.Calendars.NAME + "=?";
        try (Cursor c = resolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[] { CalendarContract.Calendars._ID },
                selection,
                new String[] { ACCOUNT_NAME, ACCOUNT_TYPE, CALENDAR_NAME },
                null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        }
        return -1L;
    }

    /** 本地日历账户不存在就建一个;CALLER_IS_SYNCADAPTER 才允许写账户字段 */
    private long ensureCalendar(String displayName) {
        long existing = findCalendarId();
        if (existing > 0) return existing;

        ContentValues cv = new ContentValues();
        cv.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME);
        cv.put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE);
        cv.put(CalendarContract.Calendars.NAME, CALENDAR_NAME);
        cv.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName);
        cv.put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR);
        cv.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER);
        cv.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME);
        cv.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().getID());
        cv.put(CalendarContract.Calendars.VISIBLE, 1);
        cv.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        Uri uri = CalendarContract.Calendars.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build();
        Uri created = resolver().insert(uri, cv);
        if (created == null) return -1L;
        try {
            return ContentUris.parseId(created);
        } catch (Exception e) {
            long id = findCalendarId();
            return id;
        }
    }

    private int countEvents(long calendarId) {
        try (Cursor c = resolver().query(CalendarContract.Events.CONTENT_URI,
                new String[] { CalendarContract.Events._ID },
                CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DELETED + "=0",
                new String[] { String.valueOf(calendarId) },
                null)) {
            return c == null ? 0 : c.getCount();
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean writeEvent(long calendarId, JSONObject raw) {
        String date = trimToNull(raw.optString("date", null));
        String title = trimToNull(raw.optString("title", null));
        if (date == null || title == null) return false;

        String time = trimToNull(raw.optString("time", null));
        String tz = trimToNull(raw.optString("timeZone", null));
        boolean allDay = time == null;

        ContentValues ev = new ContentValues();
        ev.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        ev.put(CalendarContract.Events.TITLE, title);
        String description = trimToNull(raw.optString("description", null));
        if (description != null) ev.put(CalendarContract.Events.DESCRIPTION, description);
        String link = trimToNull(raw.optString("link", null));
        if (link != null) ev.put(CalendarContract.Events.CUSTOM_APP_URI, link);

        long startMillis;
        try {
            LocalDate day = LocalDate.parse(date);
            if (allDay) {
                // 全天事项:按日历约定用 UTC 零点 + ALL_DAY=1
                startMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                ev.put(CalendarContract.Events.ALL_DAY, 1);
                ev.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
                ev.put(CalendarContract.Events.DTSTART, startMillis);
                ev.put(CalendarContract.Events.DTEND, startMillis + 24L * 60L * 60L * 1000L);
            } else {
                ZoneId zone = tz == null ? ZoneId.systemDefault() : ZoneId.of(tz);
                ZonedDateTime at = ZonedDateTime.of(day, LocalTime.parse(time), zone);
                startMillis = at.toInstant().toEpochMilli();
                ev.put(CalendarContract.Events.ALL_DAY, 0);
                ev.put(CalendarContract.Events.EVENT_TIMEZONE, zone.getId());
                ev.put(CalendarContract.Events.DTSTART, startMillis);
                ev.put(CalendarContract.Events.DTEND, startMillis + EVENT_DURATION_MS);
            }
        } catch (Exception e) {
            return false;
        }

        Uri eventUri = resolver().insert(CalendarContract.Events.CONTENT_URI, ev);
        if (eventUri == null) return false;
        long eventId;
        try {
            eventId = ContentUris.parseId(eventUri);
        } catch (Exception e) {
            return true;
        }

        // 定时事项挂 0 分钟提醒,让系统日历也能准点响;全天事项不挂(否则半夜响)
        if (!allDay) {
            try {
                ContentValues rem = new ContentValues();
                rem.put(CalendarContract.Reminders.EVENT_ID, eventId);
                rem.put(CalendarContract.Reminders.MINUTES, 0);
                rem.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                resolver().insert(CalendarContract.Reminders.CONTENT_URI, rem);
            } catch (Exception ignored) {
                // 某些厂商日历不支持提醒表,事件本身已写入,不影响兜底
            }
        }
        return true;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() || "null".equals(t) ? null : t;
    }
}
