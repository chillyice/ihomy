package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家庭贷款记录(family_loan):登记家庭真实的贷款(房贷/车贷等),按事件时间轴重算实际还款流水。
 *
 * 金额单位为元;rate 为年利率百分数(3.1 表示 3.1%)。组合贷按渠道拆成多条记录(商贷/公积金各自一条)。
 * 是否已结清不落库,由还款流水推算(见前端 utils/loan.js 的 loanLedger)。
 */
@Data
@TableName("family_loan")
public class FamilyLoan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    /** 贷款名称(如:建行房贷) */
    private String name;

    /** 资金渠道: COMMERCIAL 商业贷款 / FUND 公积金贷款 / OTHER 其他 */
    private String channel;

    /** 还款方式: EQUAL_INSTALLMENT 等额本息 / EQUAL_PRINCIPAL 等额本金 */
    private String method;

    /** 贷款本金(元) */
    private BigDecimal amount;

    /** 还款期数(月) */
    private Integer months;

    /** 放款日(可空;填写后首期按放款日到首个还款日的实际天数计息) */
    private LocalDate loanDate;

    /** 首期还款日(可空;用于把期次换算成真实年月与推算已还期数) */
    private LocalDate firstPayDate;

    /** 首期还款额(元,可空;空 = 按整月口径推算) */
    private BigDecimal firstPayment;

    /** 初始年利率(%) */
    private BigDecimal rate;

    /** 贷款组名称(选填;同组贷款合并展示合计,如商贷+公积金同填「组合贷」) */
    private String groupName;

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
