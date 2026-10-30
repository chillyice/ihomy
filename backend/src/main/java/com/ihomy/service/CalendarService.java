package com.ihomy.service;

import cn.hutool.core.date.ChineseDate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.CalendarDay;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysHoliday;
import com.ihomy.mapper.SysHolidayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日历基础数据:农历、节气、传统节日、法定节假日与调休。
 * <p>
 * 农历/节气/传统节日由 Hutool ChineseDate 现算(不引前端农历库、不落库);
 * 法定节假日与调休来自 sys_holiday(按年维护,缺年份时只少「休/班」角标,不报错)。
 * 节日名称规范化口径只在 {@link #normalizeFestivals(String)} 一处,
 * 月历格子与当日详情共用这一份响应。
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;
    private static final String MONTH_FORMAT_HINT = "月份格式不正确,请使用 YYYY-MM";

    /** 农历月文案(1-12,1-based);闰月在前面加「闰」 */
    private static final String[] LUNAR_MONTH_TEXT = {
            "", "正月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "冬月", "腊月"
    };

    /**
     * 传统节日别名 → 规范名。这张表同时是白名单:
     * Hutool 的节日集合还含佛教/民俗条目(如「阿弥陀佛圣诞」「犬日」),
     * 不在此表内的名称一律丢弃,避免格子里出现无关内容。
     */
    private static final Map<String, String> FESTIVAL_ALIASES = new HashMap<>();

    static {
        FESTIVAL_ALIASES.put("春节", "春节");
        FESTIVAL_ALIASES.put("元宵节", "元宵节");
        FESTIVAL_ALIASES.put("上元节", "元宵节");
        FESTIVAL_ALIASES.put("龙抬头", "龙抬头");
        FESTIVAL_ALIASES.put("春龙节", "龙抬头");
        FESTIVAL_ALIASES.put("中和节", "中和节");
        FESTIVAL_ALIASES.put("花朝节", "花朝节");
        FESTIVAL_ALIASES.put("上巳节", "上巳节");
        FESTIVAL_ALIASES.put("寒食节", "寒食节");
        FESTIVAL_ALIASES.put("端午节", "端午节");
        FESTIVAL_ALIASES.put("端阳节", "端午节");
        FESTIVAL_ALIASES.put("七夕", "七夕");
        FESTIVAL_ALIASES.put("乞巧节", "七夕");
        FESTIVAL_ALIASES.put("中元节", "中元节");
        FESTIVAL_ALIASES.put("盂兰盆节", "中元节");
        FESTIVAL_ALIASES.put("中秋节", "中秋节");
        FESTIVAL_ALIASES.put("重阳节", "重阳节");
        FESTIVAL_ALIASES.put("下元节", "下元节");
        FESTIVAL_ALIASES.put("腊八节", "腊八节");
        FESTIVAL_ALIASES.put("小年", "小年");
        FESTIVAL_ALIASES.put("除夕", "除夕");
    }

    private final SysHolidayMapper holidayMapper;

    /**
     * 某月的日历基础数据。
     *
     * @param month YYYY-MM;为空取当月
     * @return { month: "YYYY-MM", days: [CalendarDay...] }
     */
    public Map<String, Object> month(String month) {
        YearMonth ym = parseMonth(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<SysHoliday> rows = holidayMapper.selectList(new LambdaQueryWrapper<SysHoliday>()
                .ge(SysHoliday::getHolidayDate, start)
                .le(SysHoliday::getHolidayDate, end));
        Map<LocalDate, SysHoliday> holidays = new HashMap<>();
        for (SysHoliday h : rows) {
            holidays.put(h.getHolidayDate(), h);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("month", ym.toString());
        data.put("days", buildMonth(ym, holidays));
        return data;
    }

    /** 逐日组装整月(纯函数,不碰数据库,便于单测) */
    public static List<CalendarDay> buildMonth(YearMonth ym, Map<LocalDate, SysHoliday> holidays) {
        List<CalendarDay> days = new ArrayList<>(ym.lengthOfMonth());
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            days.add(buildDay(date, holidays == null ? null : holidays.get(date)));
        }
        return days;
    }

    /**
     * 单日信息(纯函数)。
     * Hutool 的 getMonth() 对闰月返回「闰几月的下一个月号」(闰六月返回 7),需按 isLeapMonth 减 1 还原。
     */
    public static CalendarDay buildDay(LocalDate date, SysHoliday holiday) {
        ChineseDate chinese = new ChineseDate(date);
        boolean leapMonth = chinese.isLeapMonth();
        int lunarMonth = leapMonth ? chinese.getMonth() - 1 : chinese.getMonth();
        CalendarDay.HolidayInfo info = holiday == null ? null
                : new CalendarDay.HolidayInfo(holiday.getType(), holiday.getName());
        return new CalendarDay(
                date,
                lunarMonth,
                chinese.getDay(),
                chinese.getChineseDay(),
                lunarMonthText(lunarMonth, leapMonth),
                leapMonth,
                chinese.getTerm() == null ? "" : chinese.getTerm(),
                normalizeFestivals(chinese.getFestivals()),
                info,
                isWeekend(date));
    }

    /**
     * Hutool 的节日串用空格或逗号分隔(如「元宵节 上元节」「中元节,盂兰盆节 中元节」),
     * 且同一节日可能有多个别名;这里按别名表归一、去重,保留出现顺序。
     */
    private static List<String> normalizeFestivals(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> names = new ArrayList<>();
        for (String piece : raw.split("[\\s,，]+")) {
            String canonical = FESTIVAL_ALIASES.get(piece);
            if (canonical != null && !names.contains(canonical)) {
                names.add(canonical);
            }
        }
        return names;
    }

    private static String lunarMonthText(int month, boolean leap) {
        String text = month >= 1 && month < LUNAR_MONTH_TEXT.length ? LUNAR_MONTH_TEXT[month] : "";
        return leap ? "闰" + text : text;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /** 解析 YYYY-MM;为空取当月,格式或年份越界抛 BAD_REQUEST */
    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(ZONE);
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(month.trim());
        } catch (Exception ex) {
            throw new BizException(ResultCode.BAD_REQUEST, MONTH_FORMAT_HINT);
        }
        if (ym.getYear() < MIN_YEAR || ym.getYear() > MAX_YEAR) {
            throw new BizException(ResultCode.BAD_REQUEST, MONTH_FORMAT_HINT);
        }
        return ym;
    }
}
