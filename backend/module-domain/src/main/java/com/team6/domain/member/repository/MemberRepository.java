package com.team6.domain.member.repository;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.team6.domain.member.entity.Status;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository <Member, Long>{
    // 이메일로만 찾기
    Optional<Member> findByEmail(String email);

    // Role별 중복 가입을 위해 이메일 + Role 조합으로 찾기
    Optional<Member> findByEmailAndRole(String email, Role role);
    boolean existsByEmailAndRole(String email, Role role);
    Optional <Member> findByNicknameAndStatus(String nickname, Status status);

    //닉네임 중복 체크용
    boolean existsByNicknameAndStatus(String nickname, Status status);
    boolean existsByNickname(String nickname);



    Role role(Role role);
}
