package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.PasswordResetToken;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.PasswordResetTokenMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.security.SecurityHelper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 密码找回:邮箱自助重置。
 * - 请求重置:校验图形验证码 + 双维度限流(IP/邮箱),签发 30 分钟一次性 token 并邮件发送重置链接。
 * - 重置密码:校验 token(未使用/未过期)后更新密码哈希,并作废 token、失效用户缓存。
 * 邮件通道经 spring-boot-starter-mail;未配置 spring.mail.host 时按「未开通」处理,不装配发送器。
 * 请求重置**不区分邮箱是否注册**(统一成功返回),避免账号枚举。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final SysUserMapper sysUserMapper;
    private final PasswordResetTokenMapper resetTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final AuthGuardService authGuard;
    private final SecurityHelper securityHelper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${app.mail.from:}")
    private String mailFrom;
    @Value("${app.mail.reset-url-base:https://ihomy.top/reset-password}")
    private String resetUrlBase;

    /** token 有效期(分钟) */
    private static final int TOKEN_TTL_MINUTES = 30;

    /** 请求重置:校验验证码与限流后签发一次性 token,发邮件;邮箱未注册也静默成功 */
    public void requestReset(String email, String captchaId, String captchaCode, String ip) {
        JavaMailSender sender = requireMailSender();
        authGuard.checkRate("forgot", ip, 5, 3600);
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BizException(ResultCode.CAPTCHA_ERROR);
        }
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!StringUtils.hasText(normalized)) return;
        authGuard.checkRate("forgotmail", normalized, 3, 900);
        SysUser user = sysUserMapper.selectByEmail(normalized);
        if (user == null || (user.getIsFake() != null && user.getIsFake() == 1)) return;

        // 作废该用户此前的重置令牌,只保留本次签发的一枚
        resetTokenMapper.delete(new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getUserId, user.getId()));
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES));
        token.setUsed(0);
        token.setCreatedAt(LocalDateTime.now());
        resetTokenMapper.insert(token);

        sendResetMail(sender, normalized, token.getToken());
    }

    /** 重置密码:校验一次性 token(未用/未过期)后更新密码并作废 token */
    public void reset(String token, String newPassword, String ip) {
        authGuard.checkRate("resetpwd", ip, 20, 3600);
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.RESET_TOKEN_INVALID);
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 30) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码长度 6-30 位");
        }
        PasswordResetToken t = resetTokenMapper.selectOne(new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getToken, token.trim())
                .eq(PasswordResetToken::getUsed, 0));
        if (t == null || t.getExpiresAt() == null || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.RESET_TOKEN_INVALID);
        }
        SysUser user = sysUserMapper.selectById(t.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.RESET_TOKEN_INVALID);
        }
        // 只 SET 业务字段:sys_user.updated_at 走 ON UPDATE,不被 updateById 回写旧值
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword))
                .set(SysUser::getMustChangePassword, 0));

        PasswordResetToken used = new PasswordResetToken();
        used.setId(t.getId());
        used.setUsed(1);
        resetTokenMapper.updateById(used);
        securityHelper.invalidateUser(user.getId());
        log.info("密码已通过邮箱重置 uid={}", user.getId());
    }

    /** 邮件通道就绪校验:未配置 spring.mail.host 时明确提示走管理员重置 */
    private JavaMailSender requireMailSender() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || !StringUtils.hasText(mailHost)) {
            throw new BizException(ResultCode.MAIL_NOT_CONFIGURED);
        }
        return sender;
    }

    private void sendResetMail(JavaMailSender sender, String to, String token) {
        String link = resetUrlBase + (resetUrlBase.contains("?") ? "&" : "?") + "token=" + token;
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject("ihomy 密码重置");
            String from = StringUtils.hasText(mailFrom) ? mailFrom : mailUsername;
            if (StringUtils.hasText(from)) helper.setFrom(from);
            helper.setText("""
                    <div style="font-family:sans-serif;line-height:1.7;color:#3a2e22">
                      <p>您好，</p>
                      <p>我们收到了重置您 ihomy 账号密码的请求。点击下面的链接设置新密码（%d 分钟内有效，且仅能使用一次）：</p>
                      <p><a href="%s" style="color:#5a7d68">设置新密码</a></p>
                      <p>如果链接无法点击，请复制到浏览器打开：<br>%s</p>
                      <p>若不是您本人操作，请忽略本邮件，您的密码不会发生变化。</p>
                    </div>
                    """.formatted(TOKEN_TTL_MINUTES, link, link), true);
            sender.send(msg);
        } catch (Exception e) {
            log.error("密码重置邮件发送失败 to={}", to, e);
            throw new BizException(ResultCode.MAIL_SEND_FAILED);
        }
    }
}
