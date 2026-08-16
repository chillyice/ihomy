package com.ihomy.common;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具:用于外挂配置文件中敏感凭证的加密存储。
 *
 * 密文格式:ENC(Base64( iv(12B) || ciphertext || tag(16B) ))
 * 密钥派生:PBKDF2WithHmacSHA256(password=盐值Base64, salt=盐值字节, iterations=100000, keyLen=256bit)
 *
 * 用法:
 *   加密:String cipher = AesUtil.encrypt(plaintext, saltBase64);
 *   解密:String plain   = AesUtil.decrypt(cipher, saltBase64);
 *   外挂文件中写:ENC(xxx),后端读到 ENC 前缀自动解密
 */
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100000;
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    /** 是否为加密包裹格式 ENC(...) */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }

    /** 提取 ENC(...) 内的密文 */
    private static String unwrap(String encValue) {
        return encValue.substring(ENC_PREFIX.length(), encValue.length() - ENC_SUFFIX.length());
    }

    /** 加密并包裹为 ENC(Base64) */
    public static String encrypt(String plaintext, String saltBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            SecretKey key = deriveKey(saltBase64, salt);
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(iv.length + cipherText.length);
            bb.put(iv);
            bb.put(cipherText);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(bb.array()) + ENC_SUFFIX;
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed: " + e.getMessage(), e);
        }
    }

    /** 解密 ENC(Base64) 格式的密文;若非加密格式则原样返回(明文兼容) */
    public static String decrypt(String value, String saltBase64) {
        if (!isEncrypted(value)) return value;
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            SecretKey key = deriveKey(saltBase64, salt);
            byte[] decoded = Base64.getDecoder().decode(unwrap(value));
            ByteBuffer bb = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_BYTES];
            bb.get(iv);
            byte[] cipherText = new byte[bb.remaining()];
            bb.get(cipherText);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed: " + e.getMessage(), e);
        }
    }

    /** 生成随机盐值(16 字节 Base64) */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** PBKDF2 派生密钥:password=盐值Base64, salt=盐值字节 */
    private static SecretKey deriveKey(String saltBase64, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(saltBase64.toCharArray(), salt, ITERATIONS, KEY_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KEY_DERIVATION);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
