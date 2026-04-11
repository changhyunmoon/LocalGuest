package com.team6.module.chat.repository.mysql;

import com.team6.module.chat.entity.mysql.ChatRoom;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 특정 유저가 참여 중인 채팅방 목록 조회
     * @param userId 조회할 유저의 PK
     * @return 유저가 속한 ChatRoom 리스트
     */
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN r.participants p " +
            "WHERE p.userId = :userId")
    List<ChatRoom> findAllByUserId(@Param("userId") Long userId);

    /**
     * AI 추천 보정용 집계.
     * 채팅 도메인에는 가이드 프로필 ID가 직접 없어서, 가이드의 memberId(userId) 기준으로
     * 실제 대화 시작까지 이어진 채팅방 수를 세어 행동 신호로 사용한다.
     */
    @Query("""
            SELECT p.userId, COUNT(DISTINCT r.id)
            FROM ChatRoom r
            JOIN r.participants p
            WHERE p.userId IN :userIds
            GROUP BY p.userId
            """)
    List<Object[]> countRoomsGroupedByParticipantUserId(@Param("userIds") List<Long> userIds);
}
