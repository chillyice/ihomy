package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.InvitationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InvitationCodeMapper extends BaseMapper<InvitationCode> {

    InvitationCode selectByCode(@Param("code") String code);
}
