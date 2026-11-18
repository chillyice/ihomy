package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.SysHealthToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康数据上报令牌映射:按 SHA-256 查(唯一键),签发/吊销/更新最后使用时间用 MP。
 */
@Mapper
public interface SysHealthTokenMapper extends BaseMapper<SysHealthToken> {
}
