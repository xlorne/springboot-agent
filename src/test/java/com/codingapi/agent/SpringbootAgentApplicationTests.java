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
    void generationWithDeepseek() {
        String answer = chatService.generationWithDeepseek("123", "你好，现在是白天还是晚上？");
        System.out.println("answer:\n" + answer);
    }

    @Test
    void generation() {
        String answer = chatService.generation("123", "你好，现在是白天还是晚上？，我在新加坡",true);
        System.out.println("answer:\n" + answer);
    }

}
