package com.team6.domain.member.repository;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository <Member, Long>{
    Optional<Member> findByEmail(String email);
    Optional<Member> findByEmailAndRole(String email, Role role);
    boolean existsByEmailAndRole(String email, Role role);
}
