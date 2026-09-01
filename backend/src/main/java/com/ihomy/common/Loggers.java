package com.ihomy.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用日志工具:三类日志文件的 logger 入口。
 *
 * - access():     客户端调接口(AccessLogFilter 已自动覆盖全部 HTTP 请求;
 *                 WebSocket 消息、其他自定义入口需手动补一条)
 * - thirdParty(): 出站调用第三方服务 API(统一走 common/ThirdPartyHttp,
 *                 特殊流式调用手动补日志)
 * - 其余业务日志: 直接用 @Slf4j 的 log,自动进 server 日志文件
 *
 * 六要素:时间 级别 线程 [tid] 位置(logger) 内容。
 * 级别标准:ERROR=需人工介入(带堆栈)/WARN=可自动恢复但需关注/INFO=关键业务节点/DEBUG=调试细节。
 */
public final class Loggers {

    public static final String ACCESS_NAME = "ACCESS";
    public static final String THIRD_PARTY_PREFIX = "thirdparty.";

    private Loggers() {
    }

    /** 客户端接口调用日志(access 文件) */
    public static Logger access() {
        return LoggerFactory.getLogger(ACCESS_NAME);
    }

    /** 第三方服务出站调用日志(thirdparty 文件);service 如 weather/baidu/ipapi/bing */
    public static Logger thirdParty(String service) {
        return LoggerFactory.getLogger(THIRD_PARTY_PREFIX + service);
    }
}
