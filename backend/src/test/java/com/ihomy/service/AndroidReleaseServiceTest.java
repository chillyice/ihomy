package com.ihomy.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 安装包版本挑选:更新接口以 uploads/app 里实际存在的包为准,挑错就会「客户端拿旧版本」。
 */
class AndroidReleaseServiceTest {

    @Test
    void picksHighestMinorOfSameMajor() {
        assertEquals("V12.12", AndroidReleaseService.pickLatest(
                List.of("ihomy-V12.10.apk", "ihomy-V12.12.apk", "ihomy-V12.2.apk")));
    }

    @Test
    void picksHigherMajorEvenWhenItsMinorIsSmaller() {
        assertEquals("V12.2", AndroidReleaseService.pickLatest(
                List.of("ihomy-V11.89.apk", "ihomy-V11.84.apk", "ihomy-V12.2.apk")));
    }

    @Test
    void comparesMinorNumericallyNotAlphabetically() {
        assertEquals("V9.110", AndroidReleaseService.pickLatest(
                List.of("ihomy-V9.99.apk", "ihomy-V9.110.apk")));
    }

    @Test
    void ignoresUnrelatedFiles() {
        assertEquals("", AndroidReleaseService.pickLatest(
                List.of("ihomy-V12.12.apk.bak", "ihomy-V12.10-arm64.apk", "README.md", "")));
    }

    @Test
    void ignoresThreePartNames() {
        assertEquals("", AndroidReleaseService.pickLatest(List.of("ihomy-V12.12.1.apk")));
    }

    @Test
    void emptyWhenNoPackage() {
        assertEquals("", AndroidReleaseService.pickLatest(List.of()));
    }
}
