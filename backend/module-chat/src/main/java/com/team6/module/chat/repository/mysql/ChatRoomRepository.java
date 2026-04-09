package com.team6.module.chat.repository.mysql;

import com.team6.module.chat.entity.mysql.ChatRoom;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

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

}
