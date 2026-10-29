package com.ihomy.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 记账账户表单:新建/编辑现金、银行卡、支付宝等账户。
 *
 * 长度上限按列宽、金额精度按 DECIMAL(14,2) 前置拦截;accountType 留空由服务端归一为「银行卡」,
 * 未知取值直接拒绝,避免脏枚举落库。familyId / createdBy / 删除与时间字段由服务端填,不走表单。
 */
@Data
public class BookAccountDTO {

    /** 账户名称(如 招行工资卡) */
    @NotBlank(message = "请填写账户名称")
    @Size(max = 50, message = "账户名称最多 50 字")
    private String name;

    /** 账户类型: CASH/BANK/ALIPAY/WECHAT/CREDIT/OTHER,留空默认银行卡 */
    @Pattern(regexp = "(?i)^(CASH|BANK|ALIPAY|WECHAT|CREDIT|OTHER)?$", message = "账户类型不支持,请从列表中选择")
    private String accountType;

    /** 开户行/平台(选填) */
    @Size(max = 60, message = "开户行/平台最多 60 字")
    private String institution;

    /** 卡号后四位(选填,只存尾号) */
    @Size(max = 10, message = "卡号尾号最多 10 位")
    private String cardTail;

    /** 期初余额(元,信用卡可为负) */
    @Digits(integer = 12, fraction = 2, message = "期初余额超出可记录范围(最多两位小数)")
    private BigDecimal initialBalance;

    /** 排序(小的在前,留空按 0 处理) */
    private Integer sortOrder;

    /** 备注 */
    @Size(max = 200, message = "备注最多 200 字")
    private String note;
}
