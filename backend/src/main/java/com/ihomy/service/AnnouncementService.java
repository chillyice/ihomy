package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.AnnouncementDTO;
import com.ihomy.entity.Announcement;
import com.ihomy.mapper.AnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 家庭公告业务:家长维护图片横幅+链接,按排序值升序展示;访客仅见启用且未过期的。
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    /** 公告列表:家长(includeAll)看全部,其余只看到期内的启用项 */
    public List<Announcement> list(Long familyId, boolean includeAll) {
        if (familyId == null) return List.of();
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<>();
        qw.eq(Announcement::getFamilyId, familyId);
        if (!includeAll) {
            LocalDate today = LocalDate.now();
            qw.eq(Announcement::getEnabled, 1)
              .and(w -> w.isNull(Announcement::getStartDate).or().le(Announcement::getStartDate, today))
              .and(w -> w.isNull(Announcement::getEndDate).or().ge(Announcement::getEndDate, today));
        }
        qw.orderByAsc(Announcement::getSortOrder).orderByDesc(Announcement::getId);
        return announcementMapper.selectList(qw);
    }

    public Announcement create(Long userId, Long familyId, AnnouncementDTO dto) {
        Announcement a = new Announcement();
        apply(a, dto);
        a.setFamilyId(familyId);
        a.setCreatedBy(userId);
        announcementMapper.insert(a);
        return a;
    }

    public Announcement update(Long id, Long familyId, AnnouncementDTO dto) {
        Announcement a = requireOwn(id, familyId);
        apply(a, dto);
        announcementMapper.updateById(a);
        return a;
    }

    public void delete(Long id, Long familyId) {
        requireOwn(id, familyId);
        announcementMapper.deleteById(id);
    }

    private void apply(Announcement a, AnnouncementDTO dto) {
        if (!StringUtils.hasText(dto.getTitle())) throw new BizException(ResultCode.BAD_REQUEST);
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        a.setTitle(dto.getTitle().trim());
        a.setImageUrl(StringUtils.hasText(dto.getImageUrl()) ? dto.getImageUrl().trim() : null);
        a.setLinkUrl(StringUtils.hasText(dto.getLinkUrl()) ? dto.getLinkUrl().trim() : null);
        a.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        a.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        a.setStartDate(dto.getStartDate());
        a.setEndDate(dto.getEndDate());
    }

    private Announcement requireOwn(Long id, Long familyId) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null) throw new BizException(ResultCode.NOT_FOUND);
        if (familyId != null && !familyId.equals(a.getFamilyId())) throw new BizException(ResultCode.FORBIDDEN);
        return a;
    }
}
