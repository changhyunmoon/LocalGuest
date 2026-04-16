package com.team6.domain.guide.repository;

import com.team6.domain.guide.GuideScheduleJpaTestApplication;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate + 실제 DB 드라이버로 BOOKED+isPaid 기간 JPQL을 검증한다.
 * <p>
 * CI·로컬에서 Docker 없이도 돌아가도록 H2({@code MODE=MySQL})를 사용한다.
 * 운영 MySQL과의 미세한 차이는 필요 시 실제 MySQL로 수동 검증한다.
 */
@SpringBootTest(classes = GuideScheduleJpaTestApplication.class)
@Transactional
class GuideScheduleRepositoryJpqlIT {

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:guide_schedule_jpql;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
    }

    @Autowired
    private GuideScheduleRepository guideScheduleRepository;

    @Autowired
    private GuideProfileRepository guideProfileRepository;

    private GuideProfile bookedPaidGuide;
    private GuideProfile availableGuide;

    @BeforeEach
    void setUp() {
        bookedPaidGuide = guideProfileRepository.save(GuideProfile.builder()
                .memberId(900_001L)
                .nickname("예약완료가이드")
                .region("제주")
                .isApproved(true)
                .isActive(true)
                .build());
        availableGuide = guideProfileRepository.save(GuideProfile.builder()
                .memberId(900_002L)
                .nickname("가용가이드")
                .region("제주")
                .isApproved(true)
                .isActive(true)
                .build());

        guideScheduleRepository.save(GuideSchedule.builder()
                .guideProfile(bookedPaidGuide)
                .availableDate(LocalDate.of(2026, 4, 29))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .status(GuideScheduleStatus.BOOKED)
                .isPaid(true)
                .build());

        guideScheduleRepository.save(GuideSchedule.builder()
                .guideProfile(availableGuide)
                .availableDate(LocalDate.of(2026, 4, 29))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(19, 0))
                .status(GuideScheduleStatus.AVAILABLE)
                .isPaid(false)
                .build());
    }

    @Test
    void findGuideProfileIdsBookedAndPaidBetween_returnsOnlyBookedPaidInRange() {
        LocalDate from = LocalDate.of(2026, 4, 28);
        LocalDate to = LocalDate.of(2026, 4, 30);
        List<Long> ids = guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(
                from, to, GuideScheduleStatus.BOOKED);
        assertThat(ids).containsExactly(bookedPaidGuide.getId());
    }

    @Test
    void findBookedPaidDatesByGuideIdBetween_listsPaidDatesInRange() {
        LocalDate from = LocalDate.of(2026, 4, 28);
        LocalDate to = LocalDate.of(2026, 4, 30);
        List<LocalDate> dates = guideScheduleRepository.findBookedPaidDatesByGuideIdBetween(
                bookedPaidGuide.getId(),
                from,
                to,
                GuideScheduleStatus.BOOKED
        );
        assertThat(dates).containsExactly(LocalDate.of(2026, 4, 29));
    }

    @Test
    void findGuideProfileIdsBookedAndPaidBetween_emptyWhenNoOverlap() {
        List<Long> ids = guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                GuideScheduleStatus.BOOKED
        );
        assertThat(ids).isEmpty();
    }
}
