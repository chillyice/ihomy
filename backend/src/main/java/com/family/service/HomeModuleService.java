package com.family.service;

import com.family.dto.HomeModuleDTO;
import com.family.entity.HomeModule;

import java.util.List;

public interface HomeModuleService {
    List<HomeModule> listEnabled(Long familyId);

    List<HomeModule> listAll(Long familyId);

    void updateConfig(Long familyId, HomeModuleDTO dto);

    HomeModule addModule(HomeModule module);
}
