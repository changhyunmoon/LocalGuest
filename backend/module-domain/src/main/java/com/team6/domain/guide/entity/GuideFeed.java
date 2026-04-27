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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private GuideProfile guideProfile;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "image_url", columnDefinition = "LONGTEXT")
    private String imageUrl;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // 피드 내용 수정
    public void update(String content, String imageUrl) {
        this.content = content;
        if (imageUrl != null) {        // null이면 기존 이미지 유지
            this.imageUrl = imageUrl;
        }
    }

    // 피드 소프트 삭제
    public void delete() {
        this.isDeleted = true;
    }
}