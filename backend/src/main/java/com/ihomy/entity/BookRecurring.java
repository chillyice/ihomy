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
 * 记账周期模板实体(family_book_recurring):按周期自动生成普通记账流水。
 *
 * 生成的记录写进 family_book_record,统计/总账/预算对比全部沿用既有口径;
 * 模板自己只用 last_generated_date 记「已生成到哪天」,每日定时任务按它做条件更新抢占,
 * 同日重复跑不会重复插入。周期推算的唯一来源是 common/BookRecurringSchedule。
 */
@Data
@TableName("family_book_recurring")
public class BookRecurring {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 类型: EXPENSE/INCOME(周期模板只做收支,转账请直接在记账本里记) */
    private String type;

    /** 每次金额(元) */
    private BigDecimal amount;

    /** 分类(与记账明细同名) */
    private String category;

    /** 账户ID(可空=不挂账户) */
    private Long accountId;

    /** 备注 */
    private String remark;

    /** 周期: DAILY/WEEKLY/MONTHLY/YEARLY(列名 period_type,避免与 MySQL 关键字混淆) */
    @TableField("period_type")
    private String period;

    /** 起始日期,同时是周期锚点(星期/日号/月日) */
    private LocalDate startDate;

    /** 结束日期(含当天,可空=长期有效) */
    private LocalDate endDate;

    /** 是否启用: 1 启用 / 0 暂停 */
    private Integer enabled;

    /** 已生成到哪一天(空=从未生成) */
    private LocalDate lastGeneratedDate;

    /** 创建人ID */
    private Long createdBy;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
