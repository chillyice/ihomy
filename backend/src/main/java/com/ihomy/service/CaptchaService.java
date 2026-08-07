package com.ihomy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码:生成 4 位字符图片(base64 返回),验证码内容存 Redis 5 分钟。
 * 用于注册等公开接口防机器人刷接口。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;

    /** 测试环境固定验证码(application.yml 配置),留空则随机生成 */
    @Value("${app.captcha-fixed-code:}")
    private String fixedCode;

    private static final String PREFIX = "captcha:";
    private static final long TTL_MINUTES = 5;
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** 生成验证码:返回 captchaId(供校验)与 base64 图片 */
    public Map<String, String> generate() {
        SecureRandom random = new SecureRandom();
        // 测试环境使用固定验证码(图片同步绘制固定值,便于直接查看抄录)
        String code = (fixedCode != null && !fixedCode.isBlank()) ? fixedCode.trim().toUpperCase() : randomCode(random);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PREFIX + captchaId, code, TTL_MINUTES, TimeUnit.MINUTES);

        BufferedImage img = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // 浅色背景 + 随机干扰线/噪点,防简单 OCR
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, 120, 40);
        g.setFont(new Font("Arial", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(31 + random.nextInt(90), 60 + random.nextInt(120), 130 + random.nextInt(80)));
            int x = 16 + i * 24;
            int y = 28 + random.nextInt(6);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
        }
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(180 + random.nextInt(60), 180 + random.nextInt(60), 180 + random.nextInt(60)));
            g.drawLine(random.nextInt(120), random.nextInt(40), random.nextInt(120), random.nextInt(40));
        }
        for (int i = 0; i < 40; i++) {
            g.setColor(new Color(150 + random.nextInt(100), 150 + random.nextInt(100), 150 + random.nextInt(100)));
            g.fillRect(random.nextInt(120), random.nextInt(40), 1, 1);
        }
        g.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Map.of("captchaId", captchaId,
                    "image", "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
        } catch (Exception e) {
            return Map.of("captchaId", captchaId, "image", "");
        }
    }

    /** 随机 4 位验证码字符 */
    private String randomCode(SecureRandom random) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return code.toString();
    }

    /** 校验验证码:校验通过即删除(一次性),防止重放 */
    public boolean verify(String captchaId, String code) {
        if (captchaId == null || code == null) return false;
        String key = PREFIX + captchaId;
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null) return false;
        redisTemplate.delete(key);
        return expected.equalsIgnoreCase(code.trim());
    }
}
