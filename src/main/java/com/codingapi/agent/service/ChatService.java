package com.codingapi.agent.service;

import com.codingapi.agent.advisor.DeepSeekToolsAdvisor;
import com.codingapi.agent.advisor.Qwen3ThinkFilterAdvisor;
import com.codingapi.agent.properties.AgentProperties;
import com.codingapi.agent.tools.ToolsContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;

    public ChatService(ChatClient.Builder modelBuilder,
                       ToolCallingManager toolCallingManager,
                       ChatMemory chatMemory,
                       ToolsContext toolsContext,
                       AgentProperties agentProperties) {
        this.toolCallingManager = toolCallingManager;


        PromptChatMemoryAdvisor sessionChatMemoryAdvisor =
                PromptChatMemoryAdvisor
                        .builder(chatMemory)
                        .build();

        this.chatClient = modelBuilder
                .defaultAdvisors(sessionChatMemoryAdvisor)
                .defaultSystem(agentProperties.getDefaultSystemTemplateText())
                .defaultTools(toolsContext.getTools())
                .build();
    }

    public String generation(String chatId, String userMessage, boolean think) {
        try {
            ChatClient.CallResponseSpec responseSpec = chatClient
                    .prompt()
                    .user(userMessage)
                    .advisors(new Qwen3ThinkFilterAdvisor(think))
                    .advisors(a -> {
                        a.param(ChatMemory.CONVERSATION_ID, chatId);
                    })
                    .call();
            ChatResponse chatResponse = responseSpec.chatResponse();
            if (chatResponse != null) {
                Generation generation = chatResponse.getResult();
                return generation.getOutput().getText();
            }
            throw new RuntimeException("generation response was null");
        } catch (Exception e) {
            throw new RuntimeException("generation response was error", e);
        }
    }


    public String generationWithDeepseek(String chatId, String userMessage) {
        try {
            ChatClient.CallResponseSpec responseSpec = chatClient
                    .prompt()
                    .user(userMessage)
                    .advisors(new DeepSeekToolsAdvisor(toolCallingManager))
                    .advisors(a -> {
                        a.param(ChatMemory.CONVERSATION_ID, chatId);
                    })
                    .call();
            ChatResponse chatResponse = responseSpec.chatResponse();
            if (chatResponse != null) {
                Generation generation = chatResponse.getResult();
                return generation.getOutput().getText();
            }
            throw new RuntimeException("generation response was null");
        } catch (Exception e) {
            throw new RuntimeException("generation response was error", e);
        }
    }
}
