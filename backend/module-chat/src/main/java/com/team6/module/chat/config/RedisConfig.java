package com.team6.module.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // 1. 채팅 메시지용 토픽
    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("chat-messages");
    }

    // 2. 알림용 토픽
    @Bean
    public ChannelTopic notificationTopic() {
        return new ChannelTopic("user-notifications");
    }

    // 3. Redis 메시지 리스너 컨테이너 설정
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisSubscriber subscriber,
            ChannelTopic chatTopic,
            ChannelTopic notificationTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 채팅 토픽 구독 등록
        container.addMessageListener(new MessageListenerAdapter(subscriber), chatTopic);

        // 알림 토픽 구독 등록
        container.addMessageListener(new MessageListenerAdapter(subscriber), notificationTopic);

        return container;
    }

    // 4. RedisTemplate 설정 (객체 직렬화/역직렬화)
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        // 1. LocalDateTime을 지원하는 ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Java 8 날짜/시간 모듈 등록
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601 형식으로 저장

        // 2. 새로운 ObjectMapper를 사용하는 시리얼라이저 생성
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(serializer); // JSON 직렬화 시 날짜 지원

        return redisTemplate;
    }

}