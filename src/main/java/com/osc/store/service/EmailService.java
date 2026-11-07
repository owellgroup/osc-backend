package com.osc.store.service;

import com.osc.store.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties props;

    public EmailService(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    public record Attachment(String fileName, byte[] content, String contentType) {
    }

    private String fromAddress() {
        String from = props.getMail().getFrom();
        if (from != null && from.contains("@") && from.indexOf('@') > 0 && from.indexOf('@') < from.length() - 1) {
            return from.trim();
        }
        log.warn("SPRING_MAIL_FROM '{}' is not a valid email. Using hello@notifications.wayaspot.com", from);
        return "hello@notifications.wayaspot.com";
    }

    /** Sends an email and throws if it cannot be delivered to the mail server. */
    public void send(String to, String subject, String html, Attachment... attachments) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress(), props.getMail().getFromName());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        for (Attachment a : attachments) {
            helper.addAttachment(a.fileName(), new ByteArrayResource(a.content()), a.contentType());
        }
        mailSender.send(message);
        log.info("Email '{}' sent to {}", subject, to);
    }

    /** Fire and forget. Failures are logged and never block the caller. */
    @Async
    public void sendAsync(String to, String subject, String html, Attachment... attachments) {
        try {
            send(to, subject, html, attachments);
        } catch (Exception ex) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }
}
