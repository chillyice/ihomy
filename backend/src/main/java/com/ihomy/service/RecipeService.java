package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.RecipeDTO;
import com.ihomy.entity.Recipe;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.RecipeMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 厨房菜谱业务:菜单页列表(按类别分组)+ 菜谱 CRUD + 今日推荐。
 * 素材/设备/步骤以 JSON 字符串原样存取,前端解析。
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeMapper recipeMapper;
    private final SysUserMapper sysUserMapper;

    /** 菜单页:按类别分组的菜谱列表(只取基础字段,不含 steps 等大字段) */
    public Map<String, Object> menu(Long familyId) {
        Map<String, Object> result = new HashMap<>();
        if (familyId == null) {
            result.put("groups", new ArrayList<>());
            result.put("todayRecommend", new ArrayList<>());
            return result;
        }
        LambdaQueryWrapper<Recipe> qw = new LambdaQueryWrapper<>();
        qw.eq(Recipe::getFamilyId, familyId).orderByDesc(Recipe::getCreatedAt);
        List<Recipe> all = recipeMapper.selectList(qw);

        // 批量取作者昵称
        Set<Long> authorIds = new java.util.HashSet<>();
        for (Recipe r : all) {
            if (r.getAuthorId() != null) authorIds.add(r.getAuthorId());
        }
        Map<Long, SysUser> userMap = batchUsers(authorIds);

        // 按 category 分组(顺序固定:热菜/硬菜/凉菜/主食/粥饮/甜点)
        String[] order = {DictConst.RECIPE_HOT, DictConst.RECIPE_HARD, DictConst.RECIPE_COLD,
                DictConst.RECIPE_STAPLE, DictConst.RECIPE_PORRIDGE, DictConst.RECIPE_DESSERT};
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        for (String c : order) grouped.put(c, new ArrayList<>());
        for (Recipe r : all) {
            String c = r.getCategory();
            if (c == null || !grouped.containsKey(c)) c = DictConst.RECIPE_HOT;
            grouped.get(c).add(brief(r, userMap));
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (String c : order) {
            List<Map<String, Object>> items = grouped.get(c);
            if (!items.isEmpty()) {
                Map<String, Object> g = new HashMap<>();
                g.put("category", c);
                g.put("items", items);
                groups.add(g);
            }
        }
        result.put("groups", groups);
        result.put("todayRecommend", todayRecommend(all));
        return result;
    }

    /** 今日推荐:根据当前时间从家庭菜谱中抽 2-3 道合适组合 */
    private List<Map<String, Object>> todayRecommend(List<Recipe> all) {
        if (all.isEmpty()) return new ArrayList<>();
        int hour = java.time.LocalTime.now().getHour();
        java.util.List<String> wantCategories;
        if (hour >= 6 && hour < 10) {
            // 早餐:粥饮 + 主食
            wantCategories = List.of(DictConst.RECIPE_PORRIDGE, DictConst.RECIPE_STAPLE);
        } else if (hour >= 11 && hour < 14) {
            // 午餐:主食 + 热菜 + 硬菜
            wantCategories = List.of(DictConst.RECIPE_STAPLE, DictConst.RECIPE_HOT, DictConst.RECIPE_HARD);
        } else if (hour >= 17 && hour < 20) {
            // 晚餐:热菜 + 凉菜 + 硬菜
            wantCategories = List.of(DictConst.RECIPE_HOT, DictConst.RECIPE_COLD, DictConst.RECIPE_HARD);
        } else {
            // 其他时段:甜点/粥饮
            wantCategories = List.of(DictConst.RECIPE_DESSERT, DictConst.RECIPE_PORRIDGE);
        }
        java.util.Random rnd = new java.util.Random();
        List<Map<String, Object>> picked = new ArrayList<>();
        for (String cat : wantCategories) {
            List<Recipe> pool = new java.util.ArrayList<>();
            for (Recipe r : all) {
                if (cat.equals(r.getCategory())) pool.add(r);
            }
            if (!pool.isEmpty()) {
                Recipe r = pool.get(rnd.nextInt(pool.size()));
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("name", r.getName());
                m.put("coverImage", r.getCoverImage());
                m.put("category", r.getCategory());
                m.put("reason", cat);
                picked.add(m);
            }
        }
        return picked.size() > 3 ? picked.subList(0, 3) : picked;
    }

    /** 菜谱详情(含全部字段) */
    public Map<String, Object> detail(Long id, Long familyId) {
        Recipe r = requireOwn(id, familyId);
        Map<String, Object> m = full(r);
        SysUser author = r.getAuthorId() == null ? null : sysUserMapper.selectById(r.getAuthorId());
        m.put("authorName", UserNames.of(author));
        return m;
    }

    /** 新增菜谱 */
    public Recipe create(Long userId, Long familyId, RecipeDTO dto) {
        Recipe r = new Recipe();
        apply(r, dto);
        r.setFamilyId(familyId);
        r.setAuthorId(userId);
        recipeMapper.insert(r);
        return r;
    }

    /** 更新菜谱(仅作者或家长) */
    public Recipe update(Long id, Long familyId, Long currentUserId, boolean isOwner, RecipeDTO dto) {
        Recipe r = requireOwn(id, familyId);
        if (!isOwner && !r.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        apply(r, dto);
        recipeMapper.updateById(r);
        return r;
    }

    /** 删除菜谱(仅作者或家长) */
    public void delete(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        Recipe r = requireOwn(id, familyId);
        if (!isOwner && !r.getAuthorId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        recipeMapper.deleteById(id);
    }

    private Recipe requireOwn(Long id, Long familyId) {
        Recipe r = recipeMapper.selectById(id);
        if (r == null) throw new BizException(ResultCode.NOT_FOUND);
        if (familyId != null && !familyId.equals(r.getFamilyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return r;
    }

    private void apply(Recipe r, RecipeDTO dto) {
        r.setName(dto.getName());
        r.setCoverImage(dto.getCoverImage());
        r.setCuisine(dto.getCuisine() == null ? "OTHER" : dto.getCuisine());
        r.setCategory(dto.getCategory() == null ? DictConst.RECIPE_HOT : dto.getCategory());
        r.setFlavor(dto.getFlavor());
        r.setDescription(dto.getDescription());
        r.setIngredients(dto.getIngredients());
        r.setEquipment(dto.getEquipment());
        r.setSteps(dto.getSteps());
    }

    /** 菜单页摘要(不含 ingredients/equipment/steps 大字段) */
    private Map<String, Object> brief(Recipe r, Map<Long, SysUser> userMap) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("coverImage", r.getCoverImage());
        m.put("cuisine", r.getCuisine());
        m.put("category", r.getCategory());
        m.put("flavor", r.getFlavor());
        m.put("authorId", r.getAuthorId());
        SysUser u = r.getAuthorId() == null ? null : userMap.get(r.getAuthorId());
        m.put("authorName", UserNames.of(u));
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    /** 详情全字段 */
    private Map<String, Object> full(Recipe r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("coverImage", r.getCoverImage());
        m.put("cuisine", r.getCuisine());
        m.put("category", r.getCategory());
        m.put("flavor", r.getFlavor());
        m.put("description", r.getDescription());
        m.put("ingredients", r.getIngredients());
        m.put("equipment", r.getEquipment());
        m.put("steps", r.getSteps());
        m.put("authorId", r.getAuthorId());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private Map<Long, SysUser> batchUsers(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, SysUser> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) map.put(u.getId(), u);
        return map;
    }
}
