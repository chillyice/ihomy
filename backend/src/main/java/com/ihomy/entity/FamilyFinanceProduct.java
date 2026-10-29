package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家庭理财持仓实体(family_finance_product):存款/大额存单/国债/银行理财/基金/保险等。
 *
 * 本金不计支出(现金流与资产分开),只有收益在赎回时由用户勾选入账;
 * 预期的利息/进度/剩余天数由前端 utils/finance.js 按同一口径实时算,不落库。
 * invest/redeem/interest_record_id 指向 family_book_record,命中即视为已入账,防重复记账。
 */
@Data
@TableName("family_finance_product")
public class FamilyFinanceProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 产品名称(如 招行3年期大额存单) */
    private String name;

    /** 类型: DEPOSIT 定期存款 / LARGE_DEPOSIT 大额存单 / BOND 国债 / BANK_WMP 银行理财 / FUND 基金 / INSURANCE 保险理财 / OTHER 其他 */
    private String productType;

    /** 银行/平台(选填) */
    private String institution;

    /** 资金出自/回款账户(family_book_account.id,选填) */
    private Long accountId;

    /** 本金(元) */
    private BigDecimal principal;

    /** 年化利率百分数(2.9 表示 2.9%;空 = 浮动收益,如基金) */
    private BigDecimal annualRate;

    /** 计息方式: BY_MONTH 按月折算 / BY_DAY 按日折算 / COMPOUND 复利 */
    private String interestMode;

    /** 计息基准天数(按日折算用): 365 / 360 */
    private Integer dayCount;

    /** 期限(月;空 = 活期/不定期) */
    private Integer termMonths;

    /** 起息日/买入日 */
    private LocalDate startDate;

    /** 到期日(空 = 活期;填了期限时由服务端补算) */
    private LocalDate endDate;

    /** 状态: HOLDING 持有中 / MATURED 已到期 / REDEEMED 已赎回 / CANCELLED 已作废 */
    private String status;

    /** 赎回/了结日期 */
    private LocalDate redeemDate;

    /** 赎回到账本息合计(元) */
    private BigDecimal redeemAmount;

    /** 已实现收益(元,可负 = 亏损) */
    private BigDecimal actualInterest;

    /** 收益是否已记入记账本: 0 未记 / 1 已记 */
    private Integer interestBooked;

    /** 购买转出对应的记账明细ID(防重复记账) */
    private Long investRecordId;

    /** 赎回转入对应的记账明细ID(防重复记账) */
    private Long redeemRecordId;

    /** 收益收入对应的记账明细ID(防重复记账) */
    private Long interestRecordId;

    /** 风险等级 R1~R5(选填) */
    private String riskLevel;

    /** 备注 */
    private String note;

    /** 创建人ID */
    private Long createdBy;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
