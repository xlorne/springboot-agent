package com.codingapi.agent;

import com.codingapi.agent.properties.AgentProperties;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    @ConfigurationProperties(prefix = "codingapi.agent")
    public AgentProperties agentProperties() {
        return new AgentProperties();
    }

}
