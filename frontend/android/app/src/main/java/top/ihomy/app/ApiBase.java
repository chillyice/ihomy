package top.ihomy.app;

/**
 * 原生侧 API 基址(桌面卡片 WorkManager 拉数据用)。
 * ⚠ 与 frontend/.env.app 的 VITE_API_BASE 保持一致(公开信息,非凭证);改基址时两处一起改。
 */
public final class ApiBase {

    public static final String ORIGIN = "https://ihomy.top";
    public static final String URL = ORIGIN + "/api";

    private ApiBase() {
    }
}
