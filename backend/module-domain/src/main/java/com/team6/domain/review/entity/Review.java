package com.team6.domain.review.entity;

import com.team6.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "Text")
    private String content;

    //@ManyToOne(fetch = FetchType.Lazy)
    @JoinColumn(name = "guest_id")
    private Member guest;

    // @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id")
    private  Member guide;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @Builder
    public Review(Integer rating, String content, Member guest, Member guide) {
        this.rating = rating;
        this.content = content;
        this.guest = guest;
        this.guide = guide;
    }
}
