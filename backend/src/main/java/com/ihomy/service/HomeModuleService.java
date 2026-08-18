package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.dto.HomeModuleDTO;
import com.ihomy.entity.HomeModule;
import com.ihomy.mapper.HomeModuleMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 首页模块业务:模块分为全局(家庭无关)与家庭自定义两类,
 * 查询/启停均按 familyId 合并过滤,支撑模块化扩展入口。
 *
 * 性能:全局模块几乎不变,启动时加载到内存;家庭模块按 familyId 缓存,
 * updateConfig/addModule 时清空对应缓存。无需 Redis(数据量小,内存够用)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeModuleService {

    private final HomeModuleMapper homeModuleMapper;

    // ponytail: 启动加载全局模块 + familyId 缓存,变更时 evict;未引入 Caffeine 等库
    private volatile List<HomeModule> globalModules = List.of();
    private final ConcurrentHashMap<Long, List<HomeModule>> familyCache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        try {
            LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
            qw.isNull(HomeModule::getFamilyId).orderByAsc(HomeModule::getPosition).orderByAsc(HomeModule::getSortOrder);
            globalModules = homeModuleMapper.selectList(qw);
            log.info("loaded {} global home modules", globalModules.size());
        } catch (Exception e) {
            log.warn("init global modules failed, will lazy load", e);
        }
    }

    /** 已启用模块(全局 + 本家庭),按位置/排序号升序 */
    public List<HomeModule> listEnabled(Long familyId) {
        List<HomeModule> family = familyModules(familyId);
        List<HomeModule> result = new ArrayList<>(globalModules.size() + family.size());
        for (HomeModule m : globalModules) {
            if (m.getEnabled() != null && m.getEnabled() == 1) result.add(m);
        }
        for (HomeModule m : family) {
            if (m.getEnabled() != null && m.getEnabled() == 1) result.add(m);
        }
        result.sort((a, b) -> {
            String pa = a.getPosition() == null ? "" : a.getPosition();
            String pb = b.getPosition() == null ? "" : b.getPosition();
            int p = pa.compareTo(pb);
            if (p != 0) return p;
            return Integer.compare(a.getSortOrder() == null ? 0 : a.getSortOrder(), b.getSortOrder() == null ? 0 : b.getSortOrder());
        });
        return result;
    }

    /** 全部模块(含停用),供管理端配置 */
    public List<HomeModule> listAll(Long familyId) {
        List<HomeModule> family = familyModules(familyId);
        List<HomeModule> result = new ArrayList<>(globalModules.size() + family.size());
        result.addAll(globalModules);
        result.addAll(family);
        result.sort((a, b) -> {
            String pa = a.getPosition() == null ? "" : a.getPosition();
            String pb = b.getPosition() == null ? "" : b.getPosition();
            int p = pa.compareTo(pb);
            if (p != 0) return p;
            return Integer.compare(a.getSortOrder() == null ? 0 : a.getSortOrder(), b.getSortOrder() == null ? 0 : b.getSortOrder());
        });
        return result;
    }

    /** 家庭模块(带缓存,首次访问懒加载) */
    private List<HomeModule> familyModules(Long familyId) {
        if (familyId == null) return List.of();
        return familyCache.computeIfAbsent(familyId, fid -> {
            LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
            qw.eq(HomeModule::getFamilyId, fid).orderByAsc(HomeModule::getPosition).orderByAsc(HomeModule::getSortOrder);
            return homeModuleMapper.selectList(qw);
        });
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
        // 清缓存,下次查询重新加载
        if (familyId != null) familyCache.remove(familyId);
        // 全局模块也可能被改(familyId=null 的情况),重新加载全局
        reloadGlobal();
    }

    /** 新增模块,未填分类时默认归入 content(内容创作) */
    @Transactional
    public HomeModule addModule(HomeModule module) {
        if (module.getCategory() == null || module.getCategory().isBlank()) {
            module.setCategory("content");
        }
        homeModuleMapper.insert(module);
        // 按归属清缓存
        if (module.getFamilyId() != null) {
            familyCache.remove(module.getFamilyId());
        } else {
            reloadGlobal();
        }
        return module;
    }

    private void reloadGlobal() {
        try {
            LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
            qw.isNull(HomeModule::getFamilyId).orderByAsc(HomeModule::getPosition).orderByAsc(HomeModule::getSortOrder);
            globalModules = homeModuleMapper.selectList(qw);
        } catch (Exception e) {
            log.warn("reload global modules failed", e);
        }
    }
}
