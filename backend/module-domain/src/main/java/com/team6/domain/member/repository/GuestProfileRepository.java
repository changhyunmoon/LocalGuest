package com.team6.domain.member.repository;

import com.team6.domain.member.entity.GuestProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestProfileRepository extends JpaRepository<GuestProfile, Long> {

    Optional<GuestProfile> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);
}