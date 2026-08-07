package com.ihomy.service;

import com.ihomy.dto.HomeModuleDTO;
import com.ihomy.entity.HomeModule;

import java.util.List;

/**
 * 首页模块服务接口:启停/排序配置与模块新增(模块化扩展入口)。
 */
public interface HomeModuleService {
    List<HomeModule> listEnabled(Long familyId);

    List<HomeModule> listAll(Long familyId);

    void updateConfig(Long familyId, HomeModuleDTO dto);

    HomeModule addModule(HomeModule module);
}
