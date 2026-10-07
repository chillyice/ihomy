package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 评论自定义 SQL(全部在 resources/mapper/CommentMapper.xml)。
 * 列表页的评论数走批量聚合,禁止逐条 selectCount 造成 N+1。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 按内容类型 + 一批内容 ID 聚合评论数,返回每项 { content_id, cnt }(无评论的内容不出现在结果里)。
     * 调用方需自行补 0。contentIds 为空时不要调用(IN 空集合会产生非法 SQL)。
     */
    List<Map<String, Object>> countByContentIds(@Param("contentType") String contentType, @Param("contentIds") List<Long> contentIds);
}
