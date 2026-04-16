package com.team6.domain.guide;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * {@link com.team6.domain.guide.repository.GuideScheduleRepository} Testcontainers 통합 테스트 전용.
 * Redis·Security 등 도메인 모듈의 불필요 자동설정은 제외한다.
 */
@SpringBootApplication(
        scanBasePackages = "com.team6.domain.guide.repository",
        exclude = {
                RedisAutoConfiguration.class,
                SecurityAutoConfiguration.class
        }
)
@EntityScan(basePackages = {
        "com.team6.domain.guide.entity",
        "com.team6.module.common.global.entity"
})
public class GuideScheduleJpaTestApplication {
}
