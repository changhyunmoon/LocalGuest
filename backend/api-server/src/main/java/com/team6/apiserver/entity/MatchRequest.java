package com.team6.apiserver.entity;

import com.team6.apiserver.constant.MatchStatus;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MatchRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_request_id")
    private Long id;

    @Column(name = "guest_id", nullable = false)
    private Long guestId;

    @Column(name = "guide_id", nullable = false)
    private Long guideId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MatchStatus status = MatchStatus.REQUESTED;

    public void guideAccept() {
        this.status = MatchStatus.GUIDE_ACCEPTED;
    }

    public void guideReject() {
        this.status = MatchStatus.GUIDE_REJECTED;
    }

    public void guestConfirm() {
        this.status = MatchStatus.GUEST_CONFIRMED;
    }

    public void guestReject() {
        this.status = MatchStatus.GUEST_REJECTED;
    }
}