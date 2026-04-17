package com.team6.module.chat.repository.mysql;

import com.team6.module.chat.entity.mysql.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    //유저 이메일을 통해 참여 중인 채팅방 목록을 조회
    @Query("SELECT DISTINCT r FROM ChatRoom r " +
            "JOIN FETCH r.participants p " +
            "WHERE r.id IN (SELECT r2.id FROM ChatRoom r2 JOIN r2.participants p2 WHERE p2.userEmail = :userEmail)")
    List<ChatRoom> findAllByUserEmail(@Param("userEmail") String userEmail);
  
    Optional<ChatRoom> findByRoomId(String roomId);

    /** 매칭 단위 채팅방 제목(LG-MATCH-{requestId}) 조회 — idempotent 생성용 */
    Optional<ChatRoom> findFirstByTitleOrderByIdAsc(String title);

    @Query("SELECT p.userId, COUNT(r.id) " +
            "FROM ChatRoom r JOIN r.participants p " +
            "WHERE p.userId IN :userIds " +
            "GROUP BY p.userId")
    List<Object[]> countRoomsGroupedByParticipantUserId(@Param("userIds") List<Long> userIds);

    @Query("select distinct cr from ChatRoom cr join fetch cr.participants where cr.roomId = :roomId")
    Optional<ChatRoom> findByRoomIdWithParticipants(@Param("roomId") String roomId);
}


