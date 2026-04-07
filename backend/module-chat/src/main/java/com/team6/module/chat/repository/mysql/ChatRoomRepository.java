package com.team6.module.chat.repository.mysql;

import com.team6.module.chat.entity.mysql.ChatRoom;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByRoomId(String roomId);

    /**
     * 사용자가 참여 중인 채팅방 목록을 조회하며,
     * 각 채팅방의 참여자(participants) 목록을 FETCH JOIN으로 한 번에 가져옵니다.
     */
    @Query("SELECT DISTINCT r FROM ChatRoom r " +
            "JOIN FETCH r.participants p " +
            "WHERE r.id IN (" +
            "  SELECT r2.id FROM ChatRoom r2 " +
            "  JOIN r2.participants p2 " +
            "  WHERE p2.userId = :userId" +
            ")")
    List<ChatRoom> findAllWithParticipantsByUserId(@Param("userId") Long userId);
}
