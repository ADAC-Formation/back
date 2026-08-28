package com.adac.portail.config;

import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * {@link JavaMailSender} bean, built explicitly from {@link MailProperties} (bound from the
 * {@code MAIL_*} env vars — see CLAUDE.md) rather than left to Spring Boot's mail
 * autoconfiguration — same result, but the wiring is visible here instead of implicit.
 *
 * <p>Binds {@link MailProperties} directly instead of re-declaring {@code spring.mail.*} via
 * {@code @Value}: defining a {@code JavaMailSender} bean makes Boot's own
 * {@code MailSenderAutoConfiguration} back off entirely (it's {@code @ConditionalOnMissingBean}),
 * which also disables its {@code @EnableConfigurationProperties(MailProperties.class)} — without
 * this class's own {@code @EnableConfigurationProperties}, the {@code spring.mail.properties.*}
 * block in the YAML profiles (STARTTLS, SMTP timeouts) would silently stop applying (see
 * TICKET-007 review).</p>
 *
 * <p>Host/port/credentials differ by profile: {@code application-dev.yml} defaults to Mailtrap,
 * {@code application-prod.yml} to Brevo (see docs/STACK.md — Services externes).</p>
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailProperties.getHost());
        if (mailProperties.getPort() != null) {
            mailSender.setPort(mailProperties.getPort());
        }
        mailSender.setUsername(mailProperties.getUsername());
        mailSender.setPassword(mailProperties.getPassword());
        mailSender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());
        mailSender.getJavaMailProperties().putAll(mailProperties.getProperties());

        return mailSender;
    }
}
