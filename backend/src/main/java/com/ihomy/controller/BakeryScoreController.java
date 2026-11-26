package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.dto.BakeryScoreDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BakeryScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 小面包房接口:上报当前净资产 + 家庭净资产榜。
 * 进度本身在本机 localStorage,服务端只存榜单口径的一个数字;查询一律按登录令牌的家庭过滤。
 */
@Tag(name = "小面包房")
@RestController
@RequestMapping("/game/bakery")
@RequiredArgsConstructor
public class BakeryScoreController {

    private final BakeryScoreService bakeryScoreService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    /** 游玩中每 15 秒随存档顺手上报一次,属高频端点,不记操作日志(口径同游戏存档同步) */
    @Operation(summary = "上报当前净资产")
    @PostMapping("/score")
    public Result<Map<String, Object>> submit(@RequestBody @Valid BakeryScoreDTO dto) {
        LoginUser user = current();
        return Result.success(bakeryScoreService.submit(user.getFamilyId(), user.getUserId(), dto.getScore()));
    }

    @Operation(summary = "家庭净资产榜")
    @GetMapping("/ranking")
    public Result<Map<String, Object>> ranking() {
        LoginUser user = current();
        return Result.success(bakeryScoreService.ranking(user.getFamilyId(), user.getUserId()));
    }
}
