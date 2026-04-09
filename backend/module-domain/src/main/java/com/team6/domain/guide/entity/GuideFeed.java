package com.team6.domain.guide.entity;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 가이드 피드 엔티티 (guide_feeds 테이블)
 */
@Entity
@Table(name = "guide_feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GuideFeed extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id; // 피드 고유 ID (PK)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private GuideProfile guideProfile; // 가이드 프로필 (guide_profiles FK)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // 피드 본문 내용

    @Column(name = "image_url", length = 500)
    private String imageUrl; // 피드 이미지 URL (선택)

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false; // 소프트 삭제 여부 (true = 삭제됨)

    // 피드 내용 수정
    public void update(String content, String imageUrl) {
        this.content = content;
        this.imageUrl = imageUrl;
    }

    // 피드 소프트 삭제 (실제 DB 삭제 없이 플래그만 변경)
    public void delete() {
        this.isDeleted = true;
    }
}
