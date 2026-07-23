package com.ops.platform.service;

import com.ops.platform.entity.AlertRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${ops.notify.mail-from:ops-platform@local}")
    private String mailFrom;

    @Value("${ops.notify.mail-to:}")
    private String mailTo;

    @Value("${ops.notify.webhook-url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendAlert(AlertRecord record) {
        String content = String.format("[%s] 资产: %s\n指标: %s  触发值: %.2f  阈值: %.2f\n时间: %s\n%s",
                record.getLevel().toUpperCase(), record.getAssetName(), record.getMetricType(),
                record.getTriggerValue(), record.getThreshold(), record.getCreateTime(), record.getMessage());
        sendMail("【运维告警】" + record.getAssetName() + " " + record.getMetricType(), content);
        sendWebhook(content);
    }

    public void sendRecoverNotice(AlertRecord record) {
        String content = String.format("【告警恢复】资产: %s 指标: %s 已恢复正常，恢复时间: %s",
                record.getAssetName(), record.getMetricType(), record.getResolveTime());
        sendMail("【运维告警恢复】" + record.getAssetName() + " " + record.getMetricType(), content);
        sendWebhook(content);
    }

    private void sendMail(String subject, String content) {
        if (mailSender == null || mailTo == null || mailTo.isBlank()) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(mailTo.split(","));
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("邮件发送失败: {}", e.getMessage());
        }
    }

    private void sendWebhook(String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        try {
            // 兼容企业微信群机器人格式，其他平台可按需调整body结构
            Map<String, Object> body = new HashMap<>();
            Map<String, String> text = new HashMap<>();
            text.put("content", content);
            body.put("msgtype", "text");
            body.put("text", text);
            restTemplate.postForObject(webhookUrl, body, String.class);
        } catch (Exception e) {
            log.warn("Webhook通知发送失败: {}", e.getMessage());
        }
    }
}
