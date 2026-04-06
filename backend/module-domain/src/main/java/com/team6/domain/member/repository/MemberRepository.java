package com.team6.domain.member.repository;

import com.team6.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository <Member, Long>{
    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);
}
