package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.Result;
import com.ihomy.entity.Album;
import com.ihomy.entity.Family;
import com.ihomy.entity.HomeModule;
import com.ihomy.entity.Photo;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.HomeModuleMapper;
import com.ihomy.mapper.PhotoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "公开访问")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final FamilyMapper familyMapper;
    private final HomeModuleMapper homeModuleMapper;
    private final PhotoMapper photoMapper;
    private final AlbumMapper albumMapper;

    @Operation(summary = "首页聚合（访客可访问）")
    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        Family family = familyMapper.selectDefault();
        Long familyId = family != null ? family.getId() : null;

        Map<String, Object> data = new HashMap<>();
        data.put("family", family);

        LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.isNull(HomeModule::getFamilyId)
                    .or().eq(HomeModule::getFamilyId, familyId))
           .eq(HomeModule::getEnabled, 1)
           .orderByAsc(HomeModule::getPosition)
           .orderByAsc(HomeModule::getSortOrder);
        data.put("modules", homeModuleMapper.selectList(qw));

        data.put("photos", familyId != null
                ? photoMapper.selectPublicByFamily(familyId, 20)
                : List.of());

        return Result.success(data);
    }
}
