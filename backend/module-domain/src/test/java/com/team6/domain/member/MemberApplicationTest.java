package com.team6.domain.member;

import com.team6.domain.auth.config.PasswordConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(PasswordConfig.class)
public class MemberApplicationTest {
}
