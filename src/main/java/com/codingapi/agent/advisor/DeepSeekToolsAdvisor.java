package com.codingapi.agent.advisor;

import com.alibaba.fastjson.JSON;
import lombok.*;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.DefaultAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DeepSeekToolsAdvisor implements CallAdvisor {

    private final ToolCallingManager toolCallingManager;

    private final static String TOOLS_REQUEST_TEMPLATE =
            """
                    question:{question}
                    You are an assistant that can answer questions using tools.
                    If You are asked a question that requires a tool, you must respond with a JSON array of tool calls.
                    [
                        {
                          "tool": "tool_name",
                          "parameters": { /* required parameters matching the JSON Schema */ }
                        }
                    ]
                    Do NOT explain your answer.
                    Only choose from the tools listed below:
                    {toolSchemas}
                    Based on the user question, select the most appropriate tool and provide only the JSON response.
                    """;

    private ChatClientRequest rebuildRequest(ChatClientRequest advisedRequest) {
        Prompt prompt = advisedRequest.prompt();
        if (prompt.getOptions() instanceof OpenAiChatOptions openAiChatOptions) {
            List<ToolCallback> toolCallbacks = openAiChatOptions.getToolCallbacks();
            if (!toolCallbacks.isEmpty()) {
                String question = advisedRequest.prompt().getUserMessage().getText();
                String toolSchemas = toolCallbacks.stream()
                        .map((function) -> {
                            ToolDefinition toolDefinition = function.getToolDefinition();
                            StringBuilder schemaBuilder = new StringBuilder();
                            schemaBuilder.append("Tool Name: ").append(toolDefinition.name()).append("\n");
                            schemaBuilder.append("Description: ").append(toolDefinition.description()).append("\n");
                            schemaBuilder.append("Parameters (JSON Schema): ").append(toolDefinition.inputSchema()).append("\n");
                            return schemaBuilder.toString();
                        })
                        .collect(Collectors.joining("\n\n"));

                String content = TOOLS_REQUEST_TEMPLATE.replace("{question}", question).replace("{toolSchemas}", toolSchemas);
                Prompt resetPrompt = prompt.augmentUserMessage(content);
                return ChatClientRequest.builder()
                        .context(advisedRequest.context())
                        .prompt(Prompt.builder()
                                .chatOptions(prompt.getOptions())
                                .messages(resetPrompt.getInstructions())
                                .build())
                        .build();
            }
        }
        return advisedRequest;

    }

    private String extractJsonFromAnswer(String text) {
        if (text == null) return "";
        int jsonStart = text.indexOf("{");
        int jsonEnd = text.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1).trim();
        }
        return text.trim();
    }


    private ChatClientResponse response(ChatClientRequest advisedRequest, CallAdvisorChain chain, ChatClientResponse advisedResponse) {
        ChatResponse chatResponse = advisedResponse.chatResponse();
        if (chatResponse != null) {
            List<Generation> deepseekGenerations = new ArrayList<>();
            List<Generation> generations = chatResponse.getResults();
            for (Generation generation : generations) {
                deepseekGenerations.add(this.buildAssistantMessage(generation));
            }

            ChatResponse answerResponse = ChatResponse.builder()
                    .from(chatResponse)
                    .generations(deepseekGenerations)
                    .metadata(chatResponse.getMetadata())
                    .build();

            if (answerResponse.hasToolCalls()) {
                Prompt prompt = advisedRequest.prompt();
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, answerResponse);
                if (toolExecutionResult.returnDirect()) {
                    return ChatClientResponse.builder()
                            .chatResponse(
                                    ChatResponse.builder()
                                            .from(answerResponse)
                                            .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                            .build()
                            )
                            .build();
                } else {
                    List<CallAdvisor> callAdvisors = chain.getCallAdvisors().stream()
                            .filter(callAdvisor -> !callAdvisor.getName().equals(getName()))
                            .toList();

                    CallAdvisorChain advisorChain = new DefaultAroundAdvisorChain.Builder(chain.getObservationRegistry())
                            .pushAll(callAdvisors)
                            .build();

                    Prompt newPrompt = Prompt.builder()
                            .messages(toolExecutionResult.conversationHistory())
                            .chatOptions(prompt.getOptions())
                            .build();

                    ChatClientResponse response = advisorChain.nextCall(
                            ChatClientRequest.builder()
                                    .prompt(newPrompt)
                                    .context(advisedRequest.context())
                                    .build());

                    return this.response(advisedRequest, advisorChain, response);

                }
            }

            return ChatClientResponse.builder()
                    .chatResponse(answerResponse)
                    .context(advisedResponse.context())
                    .build();
        }
        return advisedResponse;
    }

    private Generation buildAssistantMessage(Generation generation) {
        AssistantMessage assistantMessage = generation.getOutput();
        if (assistantMessage.getMessageType() != MessageType.ASSISTANT) {
            return generation;
        }
        String rawAnswer = assistantMessage.getText();
        if (!StringUtils.hasText(rawAnswer)) {
            return generation;
        }
        String extractedJson = extractJsonFromAnswer(rawAnswer);
        if (extractedJson.isEmpty()) {
            return generation;
        }
        List<FunctionCallResponse> functionCallResponses = null;
        try {
            functionCallResponses = JSON.parseArray(extractedJson, FunctionCallResponse.class);
            if (functionCallResponses == null || functionCallResponses.isEmpty()) {
                return generation;
            }
        } catch (Exception e) {
            return generation;
        }

        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (FunctionCallResponse functionCallResponse : functionCallResponses) {
            if (functionCallResponse.isValid()) {
                toolCalls.add(new AssistantMessage.ToolCall(
                        UUID.randomUUID().toString().replaceAll("-", ""),
                        "function",
                        functionCallResponse.getTool(),
                        functionCallResponse.getParameters())
                );
            } else {
                return generation;
            }
        }

        return new Generation(new AssistantMessage(
                assistantMessage.getText(),
                assistantMessage.getMetadata(),
                toolCalls,
                assistantMessage.getMedia()
        ), generation.getMetadata());
    }


    @Override
    @NonNull
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(this.rebuildRequest(chatClientRequest));
        return this.response(chatClientRequest, callAdvisorChain, response);
    }

    @NonNull
    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    @Setter
    @Getter
    @ToString
    public static class FunctionCallResponse {

        private String tool;
        private String parameters;

        public boolean isValid() {
            return tool != null && !tool.isEmpty() && parameters != null && !parameters.isEmpty();
        }
    }
}
