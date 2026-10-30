package com.ihomy.controller;

import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 日程聚合读接口:月历与近期提醒共用。
 * 写操作仍走纪念日/提醒/计划各自接口,本控制器只读。
 * 游客可读区间聚合,但只拿到演示家庭的纪念日(其余 kind 为空)。
 */
@Tag(name = "日程聚合")
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final SecurityHelper securityHelper;
    private final PublicController publicController;

    @Operation(summary = "区间日程聚合(游客只返回纪念日)")
    @GetMapping("/events")
    public Result<Map<String, Object>> events(@RequestParam(name = "from", required = false) String from,
                                              @RequestParam(name = "to", required = false) String to) {
        LocalDate start = parseDate(from, "开始日期");
        LocalDate end = parseDate(to, "结束日期");
        SysUser user = securityHelper.currentUser();
        if (user == null) {
            Long familyId = publicController.resolveVisibleFamilyId(null, null);
            return Result.success(scheduleService.events(familyId, start, end, true));
        }
        return Result.success(scheduleService.events(user.getFamilyId(), start, end, false));
    }

    @Operation(summary = "近期日程提醒(未来 N 天,默认 7,上限 30)")
    @GetMapping("/upcoming")
    public Result<List<Map<String, Object>>> upcoming(@RequestParam(name = "days", required = false) Integer days) {
        SysUser user = securityHelper.currentUser();
        if (user == null) return Result.success(List.of());
        return Result.success(scheduleService.upcoming(user.getFamilyId(), days));
    }

    private LocalDate parseDate(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择" + label);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, label + "格式不正确，请使用 YYYY-MM-DD");
        }
    }
}
