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

    /**
     * 1:1 DM 용 방 조회(참여자 포함).
     * title 은 비식별 키(예: LG-DM-GUIDE-{guideId})로 고정하고, ownerEmail(요청자 이메일)로 게스트별 중복 생성을 막는다.
     */
    @Query("select distinct cr from ChatRoom cr join fetch cr.participants where cr.title = :title and cr.ownerEmail = :ownerEmail")
    Optional<ChatRoom> findByTitleAndOwnerEmailWithParticipants(@Param("title") String title, @Param("ownerEmail") String ownerEmail);

    @Query("SELECT p.userId, COUNT(r.id) " +
            "FROM ChatRoom r JOIN r.participants p " +
            "WHERE p.userId IN :userIds " +
            "GROUP BY p.userId")
    List<Object[]> countRoomsGroupedByParticipantUserId(@Param("userIds") List<Long> userIds);

    @Query("select distinct cr from ChatRoom cr join fetch cr.participants where cr.roomId = :roomId")
    Optional<ChatRoom> findByRoomIdWithParticipants(@Param("roomId") String roomId);
}


