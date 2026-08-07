package com.ihomy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.dto.HomeModuleDTO;
import com.ihomy.entity.HomeModule;
import com.ihomy.mapper.HomeModuleMapper;
import com.ihomy.service.HomeModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 首页模块业务实现:模块分为全局(家庭无关)与家庭自定义两类,
 * 查询/启停均按 familyId 合并过滤。
 */
@Service
@RequiredArgsConstructor
public class HomeModuleServiceImpl implements HomeModuleService {

    private final HomeModuleMapper homeModuleMapper;

    /** 已启用模块(全局 + 本家庭),按位置/排序号升序 */
    @Override
    public List<HomeModule> listEnabled(Long familyId) {
        LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.isNull(HomeModule::getFamilyId)
                    .or().eq(HomeModule::getFamilyId, familyId))
          .eq(HomeModule::getEnabled, 1)
          .orderByAsc(HomeModule::getPosition)
          .orderByAsc(HomeModule::getSortOrder);
        return homeModuleMapper.selectList(qw);
    }

    /** 全部模块(含停用),供管理端配置 */
    @Override
    public List<HomeModule> listAll(Long familyId) {
        LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.isNull(HomeModule::getFamilyId)
                    .or().eq(HomeModule::getFamilyId, familyId))
          .orderByAsc(HomeModule::getPosition)
          .orderByAsc(HomeModule::getSortOrder);
        return homeModuleMapper.selectList(qw);
    }

    /** 批量更新模块配置(位置/排序/启停),逐条落库 */
    @Override
    @Transactional
    public void updateConfig(Long familyId, HomeModuleDTO dto) {
        if (dto.getModules() == null) return;
        for (HomeModuleDTO.HomeModuleItem item : dto.getModules()) {
            HomeModule update = new HomeModule();
            update.setId(item.getId());
            update.setPosition(item.getPosition());
            update.setSortOrder(item.getSortOrder());
            update.setEnabled(item.getEnabled());
            update.setFamilyId(familyId);
            homeModuleMapper.updateById(update);
        }
    }

    /** 新增模块,未填分类时默认归入 content(内容创作) */
    @Override
    @Transactional
    public HomeModule addModule(HomeModule module) {
        if (module.getCategory() == null || module.getCategory().isBlank()) {
            module.setCategory("content");
        }
        homeModuleMapper.insert(module);
        return module;
    }

    /** 已启用模块的 code 列表,供其它服务判断入口是否存在 */
    public List<String> enabledCodes(Long familyId) {
        return listEnabled(familyId).stream().map(HomeModule::getCode).collect(Collectors.toList());
    }
}
