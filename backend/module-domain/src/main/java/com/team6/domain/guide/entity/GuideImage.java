package com.team6.domain.guide.entity;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 가이드 이미지 엔티티 (guide_images 테이블)
 */
@Entity
@Table(name = "guide_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GuideImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 이미지 고유 ID (PK)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private GuideProfile guideProfile; // 가이드 프로필 (guide_profiles FK)

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl; // 이미지 저장 URL

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0; // 이미지 정렬 순서
}
