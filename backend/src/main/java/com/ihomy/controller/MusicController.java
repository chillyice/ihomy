package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.FamilyMusic;
import com.ihomy.mapper.FamilyMusicMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 家庭共享歌单:所有家庭成员可查看,家长可增删
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class MusicController {

    private final FamilyMusicMapper musicMapper;
    private final SecurityHelper securityHelper;

    @Operation(summary = "歌单列表")
    @GetMapping("/list")
    public Result<List<FamilyMusic>> list() {
        LoginUser user = securityHelper.current();
        if (user == null || user.getFamilyId() == null) throw new BizException(ResultCode.UNAUTHORIZED);
        List<FamilyMusic> list = musicMapper.selectList(
                new LambdaQueryWrapper<FamilyMusic>()
                        .eq(FamilyMusic::getFamilyId, user.getFamilyId())
                        .orderByAsc(FamilyMusic::getSortOrder)
                        .orderByAsc(FamilyMusic::getId));
        return Result.success(list);
    }

    @Operation(summary = "添加曲目")
    @PostMapping
    public Result<FamilyMusic> add(@RequestBody FamilyMusic dto) {
        LoginUser user = securityHelper.current();
        if (user == null || user.getFamilyId() == null) throw new BizException(ResultCode.UNAUTHORIZED);
        if (dto.getUrl() == null || dto.getUrl().isBlank()) throw new BizException(ResultCode.BAD_REQUEST);
        dto.setFamilyId(user.getFamilyId());
        dto.setAddedBy(user.getUserId());
        if (dto.getSortOrder() == null) dto.setSortOrder(0);
        musicMapper.insert(dto);
        return Result.success(dto);
    }

    @Operation(summary = "删除曲目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = securityHelper.current();
        if (user == null || user.getFamilyId() == null) throw new BizException(ResultCode.UNAUTHORIZED);
        FamilyMusic m = musicMapper.selectById(id);
        if (m == null || !m.getFamilyId().equals(user.getFamilyId())) throw new BizException(ResultCode.NOT_FOUND);
        musicMapper.deleteById(id);
        return Result.success(null);
    }
}
