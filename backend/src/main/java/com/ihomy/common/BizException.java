package com.ihomy.common;

import lombok.Getter;

/**
 * 业务异常:service 层主动抛出,携带错误码与提示语,
 * 由 GlobalExceptionHandler 统一转换为 Result 失败响应。
 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    /** 带自定义提示语的业务异常(默认码仍由 ResultCode 提供) */
    public BizException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }
}
