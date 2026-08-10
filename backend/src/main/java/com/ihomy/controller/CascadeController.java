package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 照片瀑布接口:随机返回当前家庭照片(含上传者昵称/描述/地点),供首页飘落动效与全屏浏览。
 */
@Tag(name = "照片瀑布")
@RestController
@RequestMapping("/photo")
@RequiredArgsConstructor
public class CascadeController {

    private static final int DEFAULT_LIMIT = 60;

    private final PhotoMapper photoMapper;
    private final SecurityHelper securityHelper;

    @Operation(summary = "随机照片瀑布流(当前家庭,登录可用)")
    @GetMapping("/cascade")
    public Result<List<Map<String, Object>>> cascade(@RequestParam(defaultValue = "60") int limit) {
        LoginUser user = securityHelper.current();
        int capped = Math.min(limit, 200);
        Long userId = user == null ? null : user.getUserId();
        return Result.success(photoMapper.selectCascadeByFamily(user.getFamilyId(), userId, capped));
    }
}