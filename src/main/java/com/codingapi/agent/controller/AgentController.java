package com.codingapi.agent.controller;

import com.codingapi.agent.pojo.ChatRequest;
import com.codingapi.agent.service.ChatService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
@AllArgsConstructor
public class AgentController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return chatService.generation(request.getChatId(), request.getMessage(),request.isThink());
    }

}
