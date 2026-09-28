package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyLoanEvent;
import org.apache.ibatis.annotations.Param;

public interface FamilyLoanEventMapper extends BaseMapper<FamilyLoanEvent> {

    /** 物理删除某笔贷款的全部事件(编辑贷款时事件整体重写;逻辑删会留幽灵事件,故物理删) */
    int deleteByLoanId(@Param("loanId") Long loanId);
}
