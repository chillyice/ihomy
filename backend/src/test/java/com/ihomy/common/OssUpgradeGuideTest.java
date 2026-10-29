package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 升级步骤文案(纯规则,不碰 Spring/DB):三类组件各自给出可执行动作,
 * 大版本追加备份告警,开启探测的独立服务不必手工确认版本。
 */
class OssUpgradeGuideTest {

    @Test
    void npmStepsCarryInstallAndVerifyCommands() {
        List<String> steps = OssUpgradeGuide.steps(DictConst.OSS_TYPE_NPM, "vite", "8.3.5",
                null, false, "MINOR");

        assertThat(steps).hasSize(4);
        assertThat(steps.get(0)).contains("npm install vite@8.3.5").contains("package-lock.json");
        assertThat(steps.get(1)).contains("npm run lint").contains("npm run build");
        assertThat(steps.get(2)).contains("确认已升级").contains("8.3.5");
    }

    @Test
    void mavenStepsPointToPomAndWrapper() {
        List<String> steps = OssUpgradeGuide.steps(DictConst.OSS_TYPE_MAVEN, "org.springframework.boot:spring-boot-starter-web",
                "4.2.0", null, false, "PATCH");

        assertThat(steps.get(0)).contains("backend/pom.xml").contains("4.2.0").contains("spring-boot-starter-parent");
        assertThat(steps.get(1)).contains("mvnw.cmd -B clean package");
    }

    @Test
    void majorUpgradeAppendsBackupWarning() {
        List<String> steps = OssUpgradeGuide.steps(DictConst.OSS_TYPE_NPM, "pinia", "4.1.0",
                null, false, DictConst.OSS_UPDATE_MAJOR);

        assertThat(steps.get(steps.size() - 1)).contains("大版本升级").contains("备份");
    }

    @Test
    void serviceWithProbeConfirmByProbeOrManual() {
        List<String> container = OssUpgradeGuide.steps(DictConst.OSS_TYPE_SERVICE, "redis/redis", "8.10.2",
                DictConst.OSS_DEPLOY_CONTAINER, true, "MINOR");
        assertThat(container.get(0)).contains("镜像 tag").contains("8.10.2");
        assertThat(container.get(1)).contains("探测当前版本");

        List<String> systemd = OssUpgradeGuide.steps(DictConst.OSS_TYPE_SERVICE, "mysql/mysql-server", "8.6.0",
                DictConst.OSS_DEPLOY_SYSTEMD, false, "MINOR");
        assertThat(systemd.get(0)).contains("systemd");
        assertThat(systemd.get(1)).contains("确认已升级").contains("8.6.0");
    }

    @Test
    void unknownTypeFallsBackToManualGuide() {
        List<String> steps = OssUpgradeGuide.steps(null, "x", "1.0", null, false, null);

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0)).contains("官方文档");
    }
}
