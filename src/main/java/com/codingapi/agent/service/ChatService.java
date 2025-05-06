package com.codingapi.agent.service;

import com.codingapi.agent.advisor.Qwen3ThinkFilterAdvisor;
import com.codingapi.agent.properties.AgentProperties;
import com.codingapi.agent.tools.ToolsContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final AgentProperties agentProperties;

    public ChatService(ChatClient.Builder modelBuilder,
                       ChatMemory chatMemory,
                       ToolsContext toolsContext,
                       AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
        this.chatClient = modelBuilder
                .defaultSystem(agentProperties.getDefaultSystemTemplateText())
                .defaultTools(toolsContext.getTools())
                .defaultAdvisors(
                        new PromptChatMemoryAdvisor(chatMemory, agentProperties.getDefaultPromptMemoryTemplateText())
                )
                .build();

    }

    public String generation(String chatId, String userMessage, boolean think) {
        try {
            ChatClient.CallResponseSpec responseSpec = chatClient
                    .prompt()
                    .user(userMessage)
                    .advisors(new Qwen3ThinkFilterAdvisor(think))
                    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, agentProperties.getChatMemoryRetrieveSize()))
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
