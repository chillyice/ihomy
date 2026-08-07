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

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
