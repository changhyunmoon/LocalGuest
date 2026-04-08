package com.team6.apiserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.team6") // 컨트롤러, 서비스 등 모든 컴포넌트 스캔
@EntityScan(basePackages = "com.team6")    // 모든 모듈의 JPA 엔티티 스캔
@EnableJpaRepositories(
        basePackages = {
                "com.team6.module.chat.repository.mysql", // 채팅용 JPA 레포지토리
                "com.team6.domain.matching.repository",   // 매칭 도메인 JPA 레포지토리 (MatchRequest 등)
                "com.team6.domain.member.repository",     // 멤버 도메인 JPA 레포지토리
                "com.team6.domain.guide.repository",      // 가이드 도메인 JPA 레포지토리
        }
)
@EnableMongoRepositories(
        basePackages = "com.team6.module.chat.repository.mongodb" // 채팅용 MongoDB 레포지토리

)
public class ApiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiServerApplication.class, args);
    }

}
