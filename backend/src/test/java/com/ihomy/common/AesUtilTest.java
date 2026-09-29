package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AesUtil 纯逻辑单测:加解密往返、明文兼容、随机 IV(骨架,不依赖 Spring/DB)。
 */
class AesUtilTest {

    @Test
    void encryptDecryptRoundTrip() {
        String salt = AesUtil.generateSalt();
        String cipher = AesUtil.encrypt("p@ssw0rd-测试", salt);
        assertThat(cipher).startsWith("ENC(").endsWith(")");
        assertThat(AesUtil.decrypt(cipher, salt)).isEqualTo("p@ssw0rd-测试");
    }

    @Test
    void decryptPlaintextReturnsAsIs() {
        String salt = AesUtil.generateSalt();
        assertThat(AesUtil.decrypt("plain-value", salt)).isEqualTo("plain-value");
    }

    @Test
    void sameInputProducesDifferentCiphertext() {
        String salt = AesUtil.generateSalt();
        assertThat(AesUtil.encrypt("same", salt)).isNotEqualTo(AesUtil.encrypt("same", salt));
    }

    @Test
    void generatedSaltIs16Bytes() {
        String salt = AesUtil.generateSalt();
        assertThat(salt).isNotBlank();
        assertThat(java.util.Base64.getDecoder().decode(salt)).hasSize(16);
    }

    @Test
    void isEncryptedDetection() {
        assertThat(AesUtil.isEncrypted("ENC(abc)")).isTrue();
        assertThat(AesUtil.isEncrypted("ENC(abc")).isFalse();
        assertThat(AesUtil.isEncrypted(null)).isFalse();
    }
}
