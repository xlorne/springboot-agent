package com.codingapi.agent;

import com.codingapi.agent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringbootAgentApplicationTests {

    @Autowired
    private ChatService chatService;

    @Test
    void contextLoads() {
        String answer = chatService.generationWithDeepseek("123", "你好，现在几点了,Asia/Shanghai时区");
        System.out.println(answer);
    }

}
