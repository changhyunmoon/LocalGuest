// [파일] backend/module-domain/src/main/java/com/team6/domain/member/config/SignupJavaMailSenderConfig.java
// [역할] 회원가입 인증 메일용 JavaMailSender 빈 등록 (api-server 수정 없이 module-domain만으로 제공)
// [연결] spring.mail.* 프로퍼티(환경변수 SPRING_MAIL_* 로도 주입 가능) → JavaMailSenderImpl
// [조건] spring.mail.host 가 비어 있으면 빈을 만들지 않음 → SignupEmailVerificationService는 mailSender=null 로 동작(앱 기동은 유지)

package com.team6.domain.member.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class SignupJavaMailSenderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public JavaMailSender signupJavaMailSender(
            @Value("${spring.mail.host}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean startTls
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.debug", "false");
        return sender;
    }
}
