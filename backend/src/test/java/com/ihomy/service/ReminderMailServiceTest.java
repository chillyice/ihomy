package com.ihomy.service;

import com.ihomy.entity.Reminder;
import com.ihomy.entity.SysUser;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日程提醒邮件口径:发件人默认 reminder@ihomy.top、收件人=创建人账号邮箱、重要性=高;
 * SMTP 未配置或无收件人时静默返回 false(不抛出,不影响应用内提醒)。
 */
class ReminderMailServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private Reminder reminder() {
        Reminder r = new Reminder();
        r.setId(31L);
        r.setTitle("交物业费");
        r.setContent("别忘带<门禁卡>");
        r.setRemindTime(LocalTime.of(9, 30));
        return r;
    }

    private SysUser user(String email) {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setEmail(email);
        return u;
    }

    @Test
    void sendsHighImportanceMailFromReminderAddress() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(msg);
        ReminderMailService service = new ReminderMailService(provider(sender), "smtp.example.com",
                "no-reply@example.com", "reminder@ihomy.top", "https://ihomy.top/plan");

        boolean sent = service.send(user("me@example.com"), reminder(), DATE);

        assertThat(sent).isTrue();
        verify(sender).send(msg);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("me@example.com");
        assertThat(msg.getSubject()).isEqualTo("日程提醒：交物业费");
        assertThat(msg.getHeader("Importance")[0]).isEqualTo("High");
        assertThat(msg.getHeader("X-Priority")[0]).isEqualTo("1 (Highest)");
        assertThat(msg.getHeader("X-MSMail-Priority")[0]).isEqualTo("High");
        assertThat(msg.getFrom()[0].toString()).contains("reminder@ihomy.top");
    }

    @Test
    void escapesUserContentAndLinksToScheduleDate() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(msg);
        ReminderMailService service = new ReminderMailService(provider(sender), "smtp.example.com",
                "no-reply@example.com", "reminder@ihomy.top", "https://ihomy.top/plan");

        service.send(user("me@example.com"), reminder(), DATE);

        String html = (String) msg.getContent();
        assertThat(html).contains("别忘带&lt;门禁卡&gt;");
        assertThat(html).doesNotContain("<门禁卡>");
        assertThat(html).contains("https://ihomy.top/plan?date=2026-11-20");
    }

    @Test
    void skipsSilentlyWhenSmtpNotConfigured() {
        JavaMailSender sender = mock(JavaMailSender.class);
        ReminderMailService service = new ReminderMailService(provider(sender), "",
                "", "reminder@ihomy.top", "https://ihomy.top/plan");

        assertThat(service.send(user("me@example.com"), reminder(), DATE)).isFalse();
        verify(sender, never()).createMimeMessage();
    }

    @Test
    void skipsWhenRecipientHasNoEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        ReminderMailService service = new ReminderMailService(provider(sender), "smtp.example.com",
                "no-reply@example.com", "reminder@ihomy.top", "https://ihomy.top/plan");

        assertThat(service.send(user(null), reminder(), DATE)).isFalse();
        verify(sender, never()).createMimeMessage();
    }
}
