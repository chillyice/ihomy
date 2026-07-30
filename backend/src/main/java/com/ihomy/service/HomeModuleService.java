package com.ihomy.service;

import com.ihomy.dto.HomeModuleDTO;
import com.ihomy.entity.HomeModule;

import java.util.List;

public interface HomeModuleService {
    List<HomeModule> listEnabled(Long familyId);

    List<HomeModule> listAll(Long familyId);

    void updateConfig(Long familyId, HomeModuleDTO dto);

    HomeModule addModule(HomeModule module);
}
