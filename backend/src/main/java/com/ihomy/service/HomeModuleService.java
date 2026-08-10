package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.dto.HomeModuleDTO;
import com.ihomy.entity.HomeModule;
import com.ihomy.mapper.HomeModuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首页模块业务:模块分为全局(家庭无关)与家庭自定义两类,
 * 查询/启停均按 familyId 合并过滤,支撑模块化扩展入口。
 */
@Service
@RequiredArgsConstructor
public class HomeModuleService {

    private final HomeModuleMapper homeModuleMapper;

    /** 已启用模块(全局 + 本家庭),按位置/排序号升序 */
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
    public List<HomeModule> listAll(Long familyId) {
        LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.isNull(HomeModule::getFamilyId)
                    .or().eq(HomeModule::getFamilyId, familyId))
          .orderByAsc(HomeModule::getPosition)
          .orderByAsc(HomeModule::getSortOrder);
        return homeModuleMapper.selectList(qw);
    }

    /** 批量更新模块配置(位置/排序/启停),逐条落库 */
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
    @Transactional
    public HomeModule addModule(HomeModule module) {
        if (module.getCategory() == null || module.getCategory().isBlank()) {
            module.setCategory("content");
        }
        homeModuleMapper.insert(module);
        return module;
    }
}
