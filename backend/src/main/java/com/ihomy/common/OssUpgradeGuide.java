package com.ihomy.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 升级步骤文案(纯规则,无 AI):按组件类型给出可直接照做的升级动作。
 * AI 评估产出的迁移/验证步骤由 OssComponentService 单独下发,这里只负责确定性部分。
 */
public final class OssUpgradeGuide {

    private OssUpgradeGuide() {
    }

    /**
     * @param componentType NPM / MAVEN / SERVICE
     * @param packageRef    NPM 包名 / MAVEN group:artifact / SERVICE owner/repo
     * @param deployType    SERVICE 部署方式(CONTAINER / SYSTEMD / OTHER,可空)
     * @param autoProbe     该服务是否开启了当前版本探测(升级后无需手工确认)
     * @param updateType    MAJOR 时追加备份告警
     */
    public static List<String> steps(String componentType, String packageRef, String latestVersion,
                                     String deployType, boolean autoProbe, String updateType) {
        List<String> steps = new ArrayList<>();
        String target = nvl(latestVersion);
        String type = componentType == null ? "" : componentType;
        switch (type) {
            case DictConst.OSS_TYPE_NPM -> {
                steps.add("在 frontend 目录执行 npm install " + nvl(packageRef) + "@" + target
                        + " --save-exact,package-lock.json 的变更一起提交");
                steps.add("本地确认 npm run lint、npx vitest run、npm run build 全部通过后再合并");
                steps.add("升级完成后点「确认已升级」,把当前版本记为 " + target);
                steps.add("仓库已接入自动升级时,也可以先「AI 评估」判断影响,评估可行后「生成升级 PR」交给机器人开 PR");
            }
            case DictConst.OSS_TYPE_MAVEN -> {
                steps.add("在 backend/pom.xml 把 " + nvl(packageRef) + " 的版本改到 " + target
                        + ";若是 Spring Boot 组件,改 spring-boot-starter-parent 的版本");
                steps.add("在 backend 目录执行 .\\mvnw.cmd -B clean package,编译与测试通过后再合并");
                steps.add("升级完成后点「确认已升级」,把当前版本记为 " + target);
                steps.add("仓库已接入自动升级时,也可以先「AI 评估」判断影响,评估可行后「生成升级 PR」交给机器人开 PR");
            }
            case DictConst.OSS_TYPE_SERVICE -> {
                if (DictConst.OSS_DEPLOY_CONTAINER.equals(deployType)) {
                    steps.add("该服务以容器部署:把 compose/运行命令里的镜像 tag 换成 " + target + ",按现有部署流程重启");
                } else if (DictConst.OSS_DEPLOY_SYSTEMD.equals(deployType)) {
                    steps.add("该服务以 systemd 部署:按官方文档更新到 " + target + " 后重启对应服务");
                } else {
                    steps.add("该组件为独立部署服务,升级在其自身管理界面/服务器完成(参考官方文档)");
                }
                steps.add(autoProbe
                        ? "升级完成后点「探测当前版本」读回真实版本,无需手工确认"
                        : "升级完成后回到本页,点「确认已升级」把当前版本更新为 " + target);
            }
            default -> steps.add("未知组件类型,请参考官方文档手动升级");
        }
        if (DictConst.OSS_UPDATE_MAJOR.equals(updateType)) {
            steps.add("⚠ 大版本升级,可能含破坏性变更,升级前先查看变更日志并备份");
        }
        return steps;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
