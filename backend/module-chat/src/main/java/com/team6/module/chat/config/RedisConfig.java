package com.team6.module.chat.config;

import com.team6.module.chat.service.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * 채팅용 Redis Topic을 Bean으로 등록하여 서비스들에서 공유
     */
    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("chat");
    }

    /**
     * Redis 메시지 리스너 어댑터
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber) {
        // RedisSubscriber의 onMessage 메서드를 호출하도록 설정
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    /**
     * Redis 메시지 리스너 컨테이너
     * 단일 토픽("chat")을 구독하며 메시지가 오면 어댑터를 통해 리스너로 전달
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter,
            ChannelTopic chatTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, chatTopic);
        return container;
    }

    /**
     * RedisTemplate 설정
     * 현재 Subscriber에서 ObjectMapper로 수동 파싱하므로 StringSerializer 유지
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        // Key와 Value 모두 문자열로 처리 (JSON 문자열이 오고 감)
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        return redisTemplate;
    }
}