package com.team6.domain.member;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TestPasswordConfig.class)
public class MemberApplicationTest {
}
