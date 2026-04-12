package com.team6.module.chat.repository.mysql;

import com.team6.module.chat.entity.mysql.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; // 라이브러리 수정

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 특정 유저(이메일 기준)가 참여 중인 채팅방 목록 조회
     * @param userEmail 조회할 유저의 이메일 (JWT에서 추출한 값)
     * @return 유저가 속한 ChatRoom 리스트
     */
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN r.participants p " +
            "WHERE p.userEmail = :userEmail") // 필드명 변경 적용
    List<ChatRoom> findAllByUserEmail(@Param("userEmail") String userEmail);

    // roomId(UUID)를 통해 채팅방 정보를 조회
    Optional<ChatRoom> findByRoomId(String roomId);
}