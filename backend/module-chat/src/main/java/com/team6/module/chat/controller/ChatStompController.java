package com.team6.module.chat.controller;

import com.team6.module.chat.service.RedisPublisher;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Controller;



@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {


}