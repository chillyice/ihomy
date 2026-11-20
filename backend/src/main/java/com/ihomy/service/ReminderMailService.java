package com.ihomy.service;

import com.ihomy.entity.Reminder;
import com.ihomy.entity.SysUser;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日程提醒邮件(V13.86):发件人默认 reminder@ihomy.top(可配置),
 * 收件人=提醒创建人的账号邮箱,邮件头按「高重要性」标注。
 * SMTP 未配置(spring.mail.host 为空)时静默跳过——应用内提醒照常,
 * 不因邮件通道缺失让整条到点通知失败;配置缺失只在首次告警一次,避免每 5 分钟刷日志。
 */
@Slf4j
@Service
public class ReminderMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String mailUsername;
    private final String reminderFrom;
    private final String reminderUrlBase;

    /** 未配置 SMTP 只告警一次 */
    private final AtomicBoolean unconfiguredWarned = new AtomicBoolean(false);

    public ReminderMailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                               @Value("${spring.mail.host:}") String mailHost,
                               @Value("${spring.mail.username:}") String mailUsername,
                               @Value("${app.mail.reminder-from:reminder@ihomy.top}") String reminderFrom,
                               @Value("${app.mail.reminder-url-base:https://ihomy.top/plan}") String reminderUrlBase) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.reminderFrom = reminderFrom;
        this.reminderUrlBase = reminderUrlBase;
    }

    /**
     * 发送一条日程提醒邮件。
     *
     * @return 是否真正投递给 SMTP(未配置/无收件人/异常返回 false,调用方不因此阻断应用内提醒)
     */
    public boolean send(SysUser to, Reminder reminder, LocalDate date) {
        if (to == null || !StringUtils.hasText(to.getEmail())) return false;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || !StringUtils.hasText(mailHost)) {
            if (unconfiguredWarned.compareAndSet(false, true)) {
                log.warn("日程提醒邮件未发送:未配置 spring.mail.host(应用内提醒不受影响)");
            }
            return false;
        }
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            String from = StringUtils.hasText(reminderFrom) ? reminderFrom : mailUsername;
            if (StringUtils.hasText(from)) {
                helper.setFrom(new InternetAddress(from, "ihomy 日程提醒", "UTF-8"));
            }
            helper.setTo(to.getEmail());
            helper.setSubject("日程提醒：" + reminder.getTitle());
            // 高重要性:Outlook 认 Importance/X-Priority,国内客户端认 X-MSMail-Priority,Priority 作通用兜底
            msg.setHeader("Importance", "High");
            msg.setHeader("X-Priority", "1 (Highest)");
            msg.setHeader("X-MSMail-Priority", "High");
            msg.setHeader("Priority", "urgent");
            helper.setText(html(reminder, date), true);
            sender.send(msg);
            log.info("日程提醒邮件已发送 reminderId={} to={}", reminder.getId(), to.getEmail());
            return true;
        } catch (Exception e) {
            log.error("日程提醒邮件发送失败 reminderId={} to={}", reminder.getId(), to.getEmail(), e);
            return false;
        }
    }

    /** 邮件正文:标题、日期时刻、内容与日程深链;用户输入一律转义 */
    private String html(Reminder r, LocalDate date) {
        StringBuilder body = new StringBuilder();
        body.append("<div style=\"font-family:sans-serif;line-height:1.7;color:#3a2e22\">");
        body.append("<p><strong style=\"font-size:16px\">").append(esc(r.getTitle())).append("</strong></p>");
        body.append("<p>时间：").append(date).append(' ').append(timeText(r)).append("</p>");
        if (StringUtils.hasText(r.getContent())) {
            body.append("<p>").append(esc(r.getContent()).replace("\n", "<br>")).append("</p>");
        }
        String link = reminderUrlBase + (reminderUrlBase.contains("?") ? "&" : "?") + "date=" + date;
        body.append("<p><a href=\"").append(esc(link)).append("\" style=\"color:#5a7d68\">打开日程提醒</a></p>");
        body.append("<p style=\"color:#8a8a8a;font-size:12px\">此邮件由 ihomy 日程提醒发送。"
                + "可在「日程提醒」中调整提醒方式。</p>");
        body.append("</div>");
        return body.toString();
    }

    /** 触发时刻 HH:mm;空时刻按全天处理 */
    private String timeText(Reminder r) {
        if (r.getRemindTime() == null) return "全天";
        return String.format("%02d:%02d", r.getRemindTime().getHour(), r.getRemindTime().getMinute());
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
