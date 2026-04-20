package com.team6.domain.member.repository;

import com.team6.domain.member.entity.TravelPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TravelPreferenceRepository extends JpaRepository<TravelPreference, Long> {

    Optional<TravelPreference> findByGuestProfileId(Long guestProfileId);

    void deleteByGuestProfileId(Long guestProfileId);
}