package com.ihomy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ihomy.entity.FamilyLoanRepay;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FamilyLoanRepayMapper extends BaseMapper<FamilyLoanRepay> {

    /** 某家庭各贷款已记账的期次(列表页一次取回,禁 N+1);只回 loanId + period 两列 */
    List<FamilyLoanRepay> selectBookedPeriods(@Param("familyId") Long familyId);
}
