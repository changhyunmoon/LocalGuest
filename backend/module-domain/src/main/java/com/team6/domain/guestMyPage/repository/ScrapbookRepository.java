package com.team6.domain.guestMyPage.repository;

import com.team6.domain.guestMyPage.entity.Scrapbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScrapbookRepository extends JpaRepository<Scrapbook, Long> {
    // 게스트의 스크랩북 목록을 최신순으로 조회
    List<Scrapbook> findByGuestIdOrderByCreatedAtDesc(Long guestId);

    // 특정 매칭건에 대해 이미 스크랩북을 썼는지 확인
    boolean existsByMatchRequestId(Long matchRequestId);

    // 매칭 ID로 스크랩북 단건 조회
    Optional<Scrapbook> findByMatchRequestId(Long matchRequestId);
}
