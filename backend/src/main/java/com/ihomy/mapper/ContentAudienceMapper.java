package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.ContentAudience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 内容分享受众:常规读写走 MP,连表回填(接收家庭名/来源家庭名)与批量收回走 XML。
 */
@Mapper
public interface ContentAudienceMapper extends BaseMapper<ContentAudience> {

    /** 某条内容分享给了哪些家庭(含家庭名/封面/圈内称呼),详情与「给谁看」回显用 */
    List<Map<String, Object>> selectTargets(@Param("contentType") String contentType, @Param("contentId") Long contentId);

    /** 列表批量回填 sharedFrom:content_id → 来源家庭(只查当前家庭作为接收方的行,禁 N+1) */
    List<Map<String, Object>> selectSharedSources(@Param("contentType") String contentType,
                                                  @Param("targetId") Long targetId,
                                                  @Param("contentIds") List<Long> contentIds);

    /** 这条内容是否分享给了该家庭(详情放行判定) */
    int countSharedTo(@Param("contentType") String contentType, @Param("contentId") Long contentId,
                      @Param("targetId") Long targetId);

    /** 某家庭分享出去的条目(审计用,按时间倒序分页) */
    List<Map<String, Object>> selectAudit(@Param("ownerFamilyId") Long ownerFamilyId,
                                          @Param("offset") long offset, @Param("limit") int limit);

    long countAudit(@Param("ownerFamilyId") Long ownerFamilyId);

    /** 退出/解散圈时批量收回:circleId 必填,targetId 为空=收回该圈全部 */
    int deleteByCircle(@Param("circleId") Long circleId, @Param("targetId") Long targetId);

    /** 收回前取涉及的内容所属家庭(用于逐个失效首页缓存) */
    List<Long> selectOwnerFamilyIdsByCircle(@Param("circleId") Long circleId, @Param("targetId") Long targetId);

    /** 某家庭分享出去、仍然有效的条目数(家庭设置里的「本月分享」) */
    long countByOwner(@Param("ownerFamilyId") Long ownerFamilyId);
}
