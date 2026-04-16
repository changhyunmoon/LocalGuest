package com.team6.domain.guestMyPage.entity;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scrapbooks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_scrapbook_match", columnNames = {"match_request_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Scrapbook extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false)
    private Long guestId; // 작성자 (게스트)

    @Column(name = "match_request_id", nullable = false)
    private Long matchRequestId; // 어떤 여행에 대한 기록인지

    @Column(nullable = false, length = 100)
    private String title; // 여행 제목

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // 피드 내용

    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl; // 썸네일용 대표 이미지

    @Column(length = 200)
    private String tags; // 예: "#로컬맛집,#사진맛집" (콤마로 구분하여 저장)

    // 수정 메서드
    public void update(String title, String content, String mainImageUrl, String tags) {
        this.title = title;
        this.content = content;
        this.mainImageUrl = mainImageUrl;
        this.tags = tags;
    }
}
