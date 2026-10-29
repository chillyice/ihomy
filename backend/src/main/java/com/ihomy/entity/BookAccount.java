package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家庭记账账户实体(family_book_account):现金/银行卡/支付宝/微信/信用卡等,一个家庭可有多本账户。
 *
 * 余额不落库,按「期初余额 + 收入 − 支出 − 转出 + 转入」实时计算(前端 utils/bookLedger.js 同一口径);
 * 记账明细通过 family_book_record 的 account_id / from_account_id / to_account_id 挂到账户。
 */
@Data
@TableName("family_book_account")
public class BookAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 账户名称(如 招行工资卡/支付宝/现金) */
    private String name;

    /** 账户类型: CASH 现金 / BANK 银行卡 / ALIPAY 支付宝 / WECHAT 微信 / CREDIT 信用卡 / OTHER 其他 */
    private String accountType;

    /** 开户行/平台(选填) */
    private String institution;

    /** 卡号后四位(只存尾号,选填) */
    private String cardTail;

    /** 期初余额(元):启用该账户时的余额,信用卡可为负 */
    private BigDecimal initialBalance;

    /** 排序(小的在前) */
    private Integer sortOrder;

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
