package com.ihomy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ihomy.common.LogAlertFingerprint;
import com.ihomy.entity.ReportAlert;
import com.ihomy.mapper.ReportAlertMapper;
import com.ihomy.mapper.SysRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 聚合预警的窗口/阈值/冷却逻辑单测(纯逻辑:Mapper 用 Mockito 桩,不起 Spring/DB)。
 * 采集端(appender)只负责把事件交进来,这里直接调 onEvent 覆盖判定规则。
 */
class LogAlertServiceTest {

    private final List<ReportAlert> inserted = Collections.synchronizedList(new ArrayList<>());
    private ReportAlertMapper alertMapper;
    private LogAlertService service;

    /** 纯单测没有 MyBatis-Plus 启动扫描,先手动注册实体表信息,否则 LambdaWrapper 解析列名会报错 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReportAlert.class);
    }

    @BeforeEach
    void setUp() {
        alertMapper = mock(ReportAlertMapper.class);
        when(alertMapper.insert(any(ReportAlert.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0));
            return 1;
        });
        when(alertMapper.selectCount(any())).thenReturn(0L);
        when(alertMapper.selectPage(any(), any())).thenReturn(new Page<>());
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        when(roleMapper.selectOpsEmails()).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);

        service = new LogAlertService(alertMapper, roleMapper, mailProvider);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "levelsCsv", "ERROR");
        ReflectionTestUtils.setField(service, "windowSeconds", 600);
        ReflectionTestUtils.setField(service, "threshold", 2);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 1800);
        ReflectionTestUtils.setField(service, "maxFingerprints", 200);
        ReflectionTestUtils.setField(service, "excludeLoggersCsv", "com.ihomy.service.LogAlertService");
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        ReflectionTestUtils.setField(service, "mailEnabled", false);
        ReflectionTestUtils.setField(service, "mailHost", "");
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    /** 生产实况:同一异常不同 URL 的第三方探测超时,应累加为一条,而不是各报一次 */
    private static LogAlertFingerprint.Event mavenTimeout(String url) {
        return new LogAlertFingerprint.Event("ERROR", "thirdparty.maven",
                "!!! GET " + url + " failed costMs=5731",
                "java.net.SocketTimeoutException", "Read timed out",
                "com.ihomy.common.ThirdPartyHttp#request", null);
    }

    private void waitInserted(int expect, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (inserted.size() < expect && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void firesOnceWhenThresholdReachedAndCountsSameClassTogether() throws Exception {
        service.start();
        service.onEvent(mavenTimeout("https://search.maven.org/a"));   // 1 次,未到阈值
        assertThat(inserted).isEmpty();
        service.onEvent(mavenTimeout("https://search.maven.org/b"));   // 2 次,达阈值
        waitInserted(1, 3000);

        assertThat(inserted).hasSize(1);
        ReportAlert a = inserted.get(0);
        assertThat(a.getLevel()).isEqualTo("ERROR");
        assertThat(a.getLogger()).isEqualTo("thirdparty.maven");
        assertThat(a.getOccurrenceCount()).isEqualTo(2);
        assertThat(a.getWindowSeconds()).isEqualTo(600);
        assertThat(a.getThreshold()).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo("OPEN");
        assertThat(a.getNotified()).isZero();
        assertThat(a.getTitle()).contains("SocketTimeoutException");
        assertThat(a.getFingerprint()).hasSize(64);
    }

    @Test
    void cooldownSuppressesRepeatAlertWhileSameProblemKeepsFlooding() throws Exception {
        service.start();
        for (int i = 0; i < 8; i++) {
            service.onEvent(mavenTimeout("https://search.maven.org/" + i));
        }
        waitInserted(1, 3000);
        Thread.sleep(200);   // 给消费线程机会再插入(若有)
        assertThat(inserted).hasSize(1);
    }

    @Test
    void firesAgainOnceCooldownElapsed() throws Exception {
        ReflectionTestUtils.setField(service, "cooldownSeconds", 0);
        ReflectionTestUtils.setField(service, "threshold", 1);
        service.start();
        service.onEvent(mavenTimeout("https://search.maven.org/a"));
        service.onEvent(mavenTimeout("https://search.maven.org/b"));
        waitInserted(2, 3000);
        assertThat(inserted).hasSize(2);
    }

    @Test
    void levelOutsideConfiguredSetIsIgnored() {
        service.start();
        service.onEvent(new LogAlertFingerprint.Event("WARN", "L", "同一句话", null, null, null, null));
        service.onEvent(new LogAlertFingerprint.Event("WARN", "L", "同一句话", null, null, null, null));
        assertThat(inserted).isEmpty();
    }

    @Test
    void excludedLoggerIsIgnored() {
        service.start();
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "com.ihomy.service.LogAlertService", "同类话", null, null, null, null));
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "com.ihomy.service.LogAlertService", "同类话", null, null, null, null));
        assertThat(inserted).isEmpty();
    }

    @Test
    void fingerprintTrackingIsCappedToProtectMemory() {
        ReflectionTestUtils.setField(service, "maxFingerprints", 2);
        ReflectionTestUtils.setField(service, "threshold", 10);
        service.start();
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "L", "问题甲", null, null, null, null));
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "L", "问题乙", null, null, null, null));
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "L", "问题丙", null, null, null, null));

        @SuppressWarnings("unchecked")
        Map<String, ?> windows = (Map<String, ?>) ReflectionTestUtils.getField(service, "windows");
        assertThat(windows).isNotNull();
        assertThat(windows.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void unknownMessageWithUniqueIdsStaysOneProblem() throws Exception {
        service.start();
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "com.ihomy.service.XService",
                "同步失败 id=1001", null, null, null, "t1"));
        service.onEvent(new LogAlertFingerprint.Event("ERROR", "com.ihomy.service.XService",
                "同步失败 id=2002", null, null, null, "t2"));
        waitInserted(1, 3000);
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getTitle()).isEqualTo("同步失败 id=#");
    }

    @Test
    void disabledServiceDropsEverything() {
        ReflectionTestUtils.setField(service, "enabled", false);
        service.start();
        service.onEvent(mavenTimeout("https://search.maven.org/a"));
        service.onEvent(mavenTimeout("https://search.maven.org/b"));
        assertThat(inserted).isEmpty();
    }

    @Test
    void ackAndSummaryUseStatusFilter() {
        service.start();
        // 桩实现只验证「能正常调用、不抛异常」,SQL 语义由接口 + DB 手工冒烟覆盖
        assertThat(service.summary()).containsKeys("open", "openError", "today", "total");
        assertThat(service.page(1, 20, "OPEN", null).getTotal()).isZero();
        assertThat(service.ack(1L, 9L)).isFalse();
    }
}
