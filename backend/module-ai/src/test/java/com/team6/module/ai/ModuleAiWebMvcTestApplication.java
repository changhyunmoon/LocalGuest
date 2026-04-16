package com.team6.module.ai;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import com.team6.module.ai.controller.AiController;
import com.team6.module.ai.support.AiRecommendExposureStore;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * MockMvc 통합 테스트용 최소 부트 설정.
 * 전체 패키지 스캔은 하지 않고 {@link AiController}만 등록한다.
 */
@SpringBootConfiguration
@Import({AiController.class, AiRecommendExposureStore.class})
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
public class ModuleAiWebMvcTestApplication {
}
