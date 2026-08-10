package com.ihomy.common;

import lombok.Getter;

/**
 * 统一错误码枚举:0 为成功;4xx/5xx 沿用 HTTP 语义;
 * 1001 起为业务自定义错误码(如注册类错误)。
 */
@Getter
public enum ResultCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    EMAIL_EXISTS(1005, "邮箱已被注册"),
    CAPTCHA_ERROR(1006, "验证码错误或已过期"),
    ALREADY_CHECKIN(1007, "今日已签到"),
    INSUFFICIENT_POINTS(1008, "积分不足"),
    PRODUCT_SOLD_OUT(1009, "该商品已兑完或已达限兑次数");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
