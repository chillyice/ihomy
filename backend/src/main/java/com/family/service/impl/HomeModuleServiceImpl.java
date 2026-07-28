package com.family.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.dto.HomeModuleDTO;
import com.family.entity.HomeModule;
import com.family.mapper.HomeModuleMapper;
import com.family.service.HomeModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeModuleServiceImpl implements HomeModuleService {

    private final HomeModuleMapper homeModuleMapper;

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

    @Override
    public List<HomeModule> listAll(Long familyId) {
        LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.isNull(HomeModule::getFamilyId)
                    .or().eq(HomeModule::getFamilyId, familyId))
          .orderByAsc(HomeModule::getPosition)
          .orderByAsc(HomeModule::getSortOrder);
        return homeModuleMapper.selectList(qw);
    }

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

    @Override
    @Transactional
    public HomeModule addModule(HomeModule module) {
        homeModuleMapper.insert(module);
        return module;
    }

    public List<String> enabledCodes(Long familyId) {
        return listEnabled(familyId).stream().map(HomeModule::getCode).collect(Collectors.toList());
    }
}
