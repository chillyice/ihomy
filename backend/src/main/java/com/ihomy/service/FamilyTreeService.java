package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.TreeMemberDTO;
import com.ihomy.entity.FamilyTreeMember;
import com.ihomy.mapper.FamilyTreeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家谱业务:成员 CRUD + 世代计算。
 * 新增成员时若指定了父亲/母亲,世代取其父/母 generation+1(父母是祖先则新成员辈分再+1);
 * 删除成员时同步清空其他成员对它的父/母/配偶引用,避免悬空关联。
 */
@Service
@RequiredArgsConstructor
public class FamilyTreeService {

    private final FamilyTreeMapper treeMapper;

    /** 家庭全部成员(附 father/mother/spouse 姓名,前端组树用) */
    public List<Map<String, Object>> list(Long familyId) {
        List<FamilyTreeMember> all = treeMapper.selectList(new LambdaQueryWrapper<FamilyTreeMember>()
                .eq(FamilyTreeMember::getFamilyId, familyId)
                .orderByAsc(FamilyTreeMember::getGeneration)
                .orderByAsc(FamilyTreeMember::getId));
        Map<Long, String> names = all.stream()
                .collect(Collectors.toMap(FamilyTreeMember::getId, FamilyTreeMember::getName, (a, b) -> a));
        return all.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getName());
            map.put("gender", m.getGender());
            map.put("birthDate", m.getBirthDate());
            map.put("photo", m.getPhoto());
            map.put("fatherId", m.getFatherId());
            map.put("fatherName", m.getFatherId() == null ? null : names.get(m.getFatherId()));
            map.put("motherId", m.getMotherId());
            map.put("motherName", m.getMotherId() == null ? null : names.get(m.getMotherId()));
            map.put("spouseId", m.getSpouseId());
            map.put("spouseName", m.getSpouseId() == null ? null : names.get(m.getSpouseId()));
            map.put("generation", m.getGeneration());
            map.put("note", m.getNote());
            return map;
        }).collect(Collectors.toList());
    }

    /** 新增成员:世代按父母辈分+1自动推导;配偶双向绑定 */
    @Transactional
    public FamilyTreeMember create(Long familyId, TreeMemberDTO dto) {
        FamilyTreeMember m = new FamilyTreeMember();
        m.setFamilyId(familyId);
        apply(m, dto);
        m.setGeneration(deriveGeneration(dto));
        treeMapper.insert(m);
        linkSpouse(m.getId(), dto.getSpouseId(), familyId);
        return m;
    }

    /** 编辑成员(全量提交:null 字段即清空)。改父母时重算世代;配偶变更时同步双向 */
    @Transactional
    public void update(Long id, Long familyId, TreeMemberDTO dto) {
        FamilyTreeMember m = require(id, familyId);
        // 配偶被更换或解除时,先解除旧配偶的指向
        Long oldSpouse = m.getSpouseId();
        if (oldSpouse != null && !Objects.equals(oldSpouse, dto.getSpouseId())) {
            unlinkSpouse(oldSpouse, familyId);
        }
        LambdaUpdateWrapper<FamilyTreeMember> uw = new LambdaUpdateWrapper<FamilyTreeMember>()
                .eq(FamilyTreeMember::getId, id)
                .set(FamilyTreeMember::getGender, dto.getGender())
                .set(FamilyTreeMember::getBirthDate, dto.getBirthDate())
                .set(FamilyTreeMember::getPhoto, dto.getPhoto())
                .set(FamilyTreeMember::getFatherId, dto.getFatherId())
                .set(FamilyTreeMember::getMotherId, dto.getMotherId())
                .set(FamilyTreeMember::getSpouseId, dto.getSpouseId())
                .set(FamilyTreeMember::getNote, dto.getNote())
                .set(FamilyTreeMember::getGeneration, deriveGeneration(dto));
        if (dto.getName() != null && !dto.getName().isBlank()) {
            uw.set(FamilyTreeMember::getName, dto.getName());
        }
        treeMapper.update(null, uw);
        linkSpouse(id, dto.getSpouseId(), familyId);
    }

    /** 删除成员:清除其他成员的父/母/配偶对本人的引用 */
    @Transactional
    public void delete(Long id, Long familyId) {
        require(id, familyId);
        unlinkSpouse(id, familyId);
        treeMapper.update(null, new LambdaUpdateWrapper<FamilyTreeMember>()
                .eq(FamilyTreeMember::getFamilyId, familyId)
                .and(w -> w.eq(FamilyTreeMember::getFatherId, id)
                        .or().eq(FamilyTreeMember::getMotherId, id)
                        .or().eq(FamilyTreeMember::getSpouseId, id))
                .set(FamilyTreeMember::getFatherId, null)
                .set(FamilyTreeMember::getMotherId, null)
                .set(FamilyTreeMember::getSpouseId, null));
        treeMapper.deleteById(id);
    }

    private void apply(FamilyTreeMember m, TreeMemberDTO dto) {
        if (dto.getName() != null && !dto.getName().isBlank()) m.setName(dto.getName());
        if (dto.getGender() != null) m.setGender(dto.getGender());
        if (dto.getBirthDate() != null) m.setBirthDate(dto.getBirthDate());
        if (dto.getPhoto() != null) m.setPhoto(dto.getPhoto());
        if (dto.getFatherId() != null) m.setFatherId(dto.getFatherId());
        if (dto.getMotherId() != null) m.setMotherId(dto.getMotherId());
        if (dto.getSpouseId() != null) m.setSpouseId(dto.getSpouseId());
        if (dto.getNote() != null) m.setNote(dto.getNote());
    }

    /** 世代 = 父/母中较大 generation + 1;无父母则为 0(新祖先) */
    private int deriveGeneration(TreeMemberDTO dto) {
        int g = -1;
        if (dto.getFatherId() != null) g = Math.max(g, genOf(dto.getFatherId()));
        if (dto.getMotherId() != null) g = Math.max(g, genOf(dto.getMotherId()));
        return g + 1;
    }

    private int genOf(Long id) {
        FamilyTreeMember p = treeMapper.selectById(id);
        return p == null ? -1 : p.getGeneration();
    }

    /** 配偶双向绑定:把新配偶的 spouse_id 指向本人(自身不可为配偶) */
    private void linkSpouse(Long selfId, Long spouseId, Long familyId) {
        if (spouseId == null || spouseId.equals(selfId)) return;
        FamilyTreeMember s = require(spouseId, familyId);
        if (!Objects.equals(s.getSpouseId(), selfId)) {
            s.setSpouseId(selfId);
            treeMapper.updateById(s);
        }
    }

    /** 解除某人的配偶关系:只清其 spouse_id(本人侧由调用方处理) */
    private void unlinkSpouse(Long spouseId, Long familyId) {
        if (spouseId == null) return;
        FamilyTreeMember s = treeMapper.selectById(spouseId);
        if (s != null && s.getFamilyId().equals(familyId)) {
            treeMapper.update(null, new LambdaUpdateWrapper<FamilyTreeMember>()
                    .eq(FamilyTreeMember::getId, spouseId)
                    .set(FamilyTreeMember::getSpouseId, null));
        }
    }

    private FamilyTreeMember require(Long id, Long familyId) {
        FamilyTreeMember m = treeMapper.selectById(id);
        if (m == null || !m.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return m;
    }
}