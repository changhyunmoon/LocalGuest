package com.team6.module.chat.repository.mysql;

import com.team6.module.chat.entity.mysql.ChatRoom;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT DISTINCT r FROM ChatRoom r " +
            "JOIN r.participants p " +
            "WHERE p.userId = :userId " +
            "ORDER BY r.lastMessageAt DESC")
    List<ChatRoom> findAllByUserId(@Param("userId") Long userId);

}
