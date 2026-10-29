package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 理财持仓表单:新建/编辑一笔存款、理财、基金等,以及赎回时的到账与收益入账。
 *
 * 长度上限按列宽、金额精度按 DECIMAL(14,2) 前置拦截;年化按 DECIMAL(6,4) 由 Service 校验
 * (99.9999 上限、最多 4 位小数)。三个 book* 布尔只在请求体里出现,不落库:
 * 勾选后才在同一事务里记一笔记账明细,并把明细 id 回写到对应的 *_record_id。
 * 编辑时字段为空表示「不修改」,想清空选填项请提交空串。
 */
@Data
public class FinanceDTO {

    /** 产品名称(如 招行3年期大额存单) */
    @NotBlank(message = "请填写产品名称")
    @Size(max = 100, message = "产品名称最多 100 字")
    private String name;

    /** 类型: DEPOSIT/LARGE_DEPOSIT/BOND/BANK_WMP/FUND/INSURANCE/OTHER,留空默认定期存款 */
    @Pattern(regexp = "(?i)^(DEPOSIT|LARGE_DEPOSIT|BOND|BANK_WMP|FUND|INSURANCE|OTHER)?$",
            message = "理财产品类型不支持,请从列表中选择")
    private String productType;

    /** 银行/平台(选填) */
    @Size(max = 60, message = "银行/平台最多 60 字")
    private String institution;

    /** 资金出自/回款账户(选填) */
    private Long accountId;

    /** 本金(元) */
    @Digits(integer = 12, fraction = 2, message = "本金超出可记录范围(最多两位小数)")
    private BigDecimal principal;

    /** 年化利率百分数(2.9 表示 2.9%;空 = 浮动收益) */
    @Digits(integer = 2, fraction = 4, message = "年化利率最多 4 位小数,且不超过 99.9999%")
    private BigDecimal annualRate;

    /** 计息方式: BY_MONTH/BY_DAY/COMPOUND,留空默认按月折算 */
    @Pattern(regexp = "(?i)^(BY_MONTH|BY_DAY|COMPOUND)?$", message = "计息方式不支持,请从列表中选择")
    private String interestMode;

    /** 计息基准天数(按日折算用): 365/360 */
    private Integer dayCount;

    /** 期限(月;空 = 活期) */
    private Integer termMonths;

    /** 起息日/买入日 */
    private LocalDate startDate;

    /** 到期日(空 = 按期限推算/活期) */
    private LocalDate endDate;

    /** 状态: HOLDING/MATURED/REDEEMED/CANCELLED,留空默认持有中 */
    @Pattern(regexp = "(?i)^(HOLDING|MATURED|REDEEMED|CANCELLED)?$", message = "理财状态不支持,请从列表中选择")
    private String status;

    /** 赎回/了结日期 */
    private LocalDate redeemDate;

    /** 赎回到账本息合计(元) */
    @Digits(integer = 12, fraction = 2, message = "到账金额超出可记录范围(最多两位小数)")
    private BigDecimal redeemAmount;

    /** 已实现收益(元,可负 = 亏损) */
    @Digits(integer = 12, fraction = 2, message = "实际收益超出可记录范围(最多两位小数)")
    private BigDecimal actualInterest;

    /** 风险等级 R1~R5(选填) */
    @Size(max = 10, message = "风险等级最多 10 字")
    private String riskLevel;

    /** 备注 */
    @Size(max = 500, message = "备注最多 500 字")
    private String note;

    /** 购买时从账户记一笔转出(仅请求体,不落库) */
    private Boolean bookInvestTransfer;

    /** 赎回时把本金转回账户(仅请求体,不落库) */
    private Boolean bookRedeemTransfer;

    /** 赎回时把收益记一笔收入(仅请求体,不落库) */
    private Boolean bookInterest;
}
