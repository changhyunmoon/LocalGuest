package com.team6.domain.guide.entity;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 가이드 경력/자격 엔티티 (guide_careers 테이블)
 */
@Entity
@Table(name = "guide_careers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GuideCareer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 경력 고유 ID (PK)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "guide_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_guide_careers_guide")
    )
    private GuideProfile guideProfile; // 가이드 프로필 (guide_profiles FK)

    @Column(nullable = false, length = 100)
    private String title; // 자격/경력명

    @Column(columnDefinition = "TEXT")
    private String description; // 자격/경력 설명

    @Column(name = "acquired_at", nullable = false)
    private LocalDate acquiredAt; // 자격 취득일
}
