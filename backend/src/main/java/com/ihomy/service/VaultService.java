package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.VaultItemDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.VaultItem;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.VaultItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家庭保险箱业务:账号密码条目的增删改查。
 *
 * 密码以 AES-GCM 密文存储({@link ParameterService#encrypt},盐值 sys_parameter.aes-salt),
 * 列表与详情只回固定掩码,明文仅在 {@link #revealPassword} 由后端解密返回
 * (调用方接口带 @OperationLog,每次揭示落一条操作日志)。
 * 可见范围:PRIVATE 仅创建人本人可见(严格仅自己,家庭家长也不可见)/ FAMILY 家庭可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService {

    private final VaultItemMapper vaultItemMapper;
    private final SysUserMapper sysUserMapper;
    private final ParameterService parameterService;

    /** 固定掩码:与真实长度无关,避免从列表泄露密码位数 */
    private static final String MASK = "••••••••";

    private static final Set<String> CATEGORIES =
            Set.of("SITE", "APP", "BANK", "SOCIAL", "DEVICE", "WIFI", "OTHER");

    /** 家庭保险箱列表:可见范围过滤(自己的 + 家庭共享的),创建人昵称批量回填(不做 N+1) */
    public List<Map<String, Object>> list(Long familyId, Long userId) {
        LambdaQueryWrapper<VaultItem> qw = new LambdaQueryWrapper<VaultItem>()
                .eq(VaultItem::getFamilyId, familyId)
                .and(w -> w.eq(VaultItem::getOwnerId, userId)
                        .or().eq(VaultItem::getVisibility, DictConst.VIS_FAMILY));
        qw.orderByDesc(VaultItem::getCreatedAt);
        List<VaultItem> items = vaultItemMapper.selectList(qw);
        if (items.isEmpty()) return List.of();

        Map<Long, String> names = sysUserMapper.selectBatchIds(
                        items.stream().map(VaultItem::getOwnerId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getNickname() != null ? u.getNickname() : u.getUsername(), (a, b) -> a));

        return items.stream().map(i -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", i.getId());
            map.put("name", i.getName());
            map.put("category", i.getCategory());
            map.put("username", i.getUsername());
            map.put("passwordMasked", hasPassword(i) ? MASK : null);
            map.put("url", i.getUrl());
            map.put("tags", i.getTags());
            map.put("note", i.getNote());
            map.put("ownerId", i.getOwnerId());
            map.put("ownerName", names.getOrDefault(i.getOwnerId(), "未知成员"));
            map.put("visibility", i.getVisibility());
            map.put("createdAt", i.getCreatedAt());
            map.put("updatedAt", i.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    /** 新增条目:密码立即加密入库,创建人默认可编辑 */
    public Long create(Long userId, Long familyId, VaultItemDTO dto) {
        VaultItem item = new VaultItem();
        item.setFamilyId(familyId);
        item.setOwnerId(userId);
        item.setName(requireName(dto.getName()));
        item.setCategory(normalizeCategory(dto.getCategory()));
        item.setUsername(blankToNull(dto.getUsername()));
        item.setUrl(blankToNull(dto.getUrl()));
        item.setTags(blankToNull(dto.getTags()));
        item.setNote(blankToNull(dto.getNote()));
        item.setVisibility(normalizeVisibility(dto.getVisibility()));
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            item.setPasswordEnc(parameterService.encrypt(dto.getPassword()));
        }
        vaultItemMapper.insert(item);
        return item.getId();
    }

    /** 编辑条目:password 为空表示不改密码(只改其他字段) */
    public void update(Long id, Long familyId, Long userId, VaultItemDTO dto) {
        require(id, familyId, userId);
        // 只 SET 本次提交的业务字段:updateById 会把实体里读出的旧 updated_at 一起写回,
        // 抑制列上 ON UPDATE CURRENT_TIMESTAMP,前端「更新于」永远停在创建时间
        LambdaUpdateWrapper<VaultItem> uw = new LambdaUpdateWrapper<VaultItem>()
                .eq(VaultItem::getId, id);
        boolean changed = false;
        if (dto.getName() != null) {
            uw.set(VaultItem::getName, requireName(dto.getName()));
            changed = true;
        }
        if (dto.getCategory() != null) {
            uw.set(VaultItem::getCategory, normalizeCategory(dto.getCategory()));
            changed = true;
        }
        if (dto.getUsername() != null) {
            uw.set(VaultItem::getUsername, blankToNull(dto.getUsername()));
            changed = true;
        }
        if (dto.getUrl() != null) {
            uw.set(VaultItem::getUrl, blankToNull(dto.getUrl()));
            changed = true;
        }
        if (dto.getTags() != null) {
            uw.set(VaultItem::getTags, blankToNull(dto.getTags()));
            changed = true;
        }
        if (dto.getNote() != null) {
            uw.set(VaultItem::getNote, blankToNull(dto.getNote()));
            changed = true;
        }
        if (dto.getVisibility() != null) {
            uw.set(VaultItem::getVisibility, normalizeVisibility(dto.getVisibility()));
            changed = true;
        }
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            uw.set(VaultItem::getPasswordEnc, parameterService.encrypt(dto.getPassword()));
            changed = true;
        }
        if (!changed) return;
        vaultItemMapper.update(null, uw);
    }

    public void delete(Long id, Long familyId, Long userId) {
        vaultItemMapper.deleteById(require(id, familyId, userId).getId());
    }

    /**
     * 揭示密码明文:仅此路径解密返回,调用方接口必须带 @OperationLog 留痕。
     * 未设置密码返回空串(前端按空处理)。
     */
    public String revealPassword(Long id, Long familyId, Long userId) {
        VaultItem item = require(id, familyId, userId);
        if (!hasPassword(item)) return "";
        return parameterService.decrypt(item.getPasswordEnc());
    }

    /** 取条目并校验家庭归属与可见范围(非本人 PRIVATE 一律按不存在处理,不泄露存在性) */
    private VaultItem require(Long id, Long familyId, Long userId) {
        VaultItem item = vaultItemMapper.selectById(id);
        if (item == null || !item.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(item.getOwnerId()) && !DictConst.VIS_FAMILY.equals(item.getVisibility())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return item;
    }

    private boolean hasPassword(VaultItem item) {
        return item.getPasswordEnc() != null && !item.getPasswordEnc().isBlank();
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写条目名称");
        }
        return name.trim();
    }

    /** 分类白名单归一:未知值落 OTHER,避免脏值进库(枚举一律大写英文单词) */
    private String normalizeCategory(String category) {
        if (category == null) return "OTHER";
        String upper = category.trim().toUpperCase();
        return CATEGORIES.contains(upper) ? upper : "OTHER";
    }

    /** 仅支持 PRIVATE/FAMILY 两档(保险箱无公开口径) */
    private String normalizeVisibility(String visibility) {
        return DictConst.VIS_PRIVATE.equals(visibility) ? DictConst.VIS_PRIVATE : DictConst.VIS_FAMILY;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
