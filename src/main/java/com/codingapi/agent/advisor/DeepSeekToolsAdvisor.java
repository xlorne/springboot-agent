package com.codingapi.agent.advisor;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DeepSeekToolsAdvisor implements CallAroundAdvisor {

    private final ToolCallingManager toolCallingManager;

    private AdvisedRequest rebuildRequest(AdvisedRequest advisedRequest) {
        List<FunctionCallback> tools = advisedRequest.functionCallbacks();
        if (!tools.isEmpty()) {
            String question = advisedRequest.userText();
            String toolSchemas = tools.stream()
                    .map((function) -> {
                        StringBuilder schemaBuilder = new StringBuilder();
                        schemaBuilder.append("Tool Name: ").append(function.getName()).append("\n");
                        schemaBuilder.append("Description: ").append(function.getDescription()).append("\n");
                        schemaBuilder.append("Parameters (JSON Schema): ").append(function.getInputTypeSchema()).append("\n");
                        return schemaBuilder.toString();
                    })
                    .collect(Collectors.joining("\n\n"));

            String toolSchemasTemplate = "\n\n" +
                    "You are an assistant that can answer questions using tools.\n" +
                    "You MUST respond only with a JSON object in the following format:\n" +
                    "[\n" +
                    "{\n" +
                    "  \"tool\": \"tool_name\",\n" +
                    "  \"parameters\": { /* required parameters matching the JSON Schema */ }\n" +
                    "}\n" +
                    "]\n\n" +
                    "Do NOT explain your answer.\n" +
                    "Only choose from the tools listed below:\n\n" +
                    toolSchemas +
                    "\n\nBased on the user question, select the most appropriate tool and provide only the JSON response.";

            return AdvisedRequest.from(advisedRequest)
                    .userText(question + toolSchemasTemplate)
                    .messages(advisedRequest.messages())
                    .build();
        } else {
            return advisedRequest;
        }
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


    private AdvisedResponse response(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain, AdvisedResponse advisedResponse) {
        ChatResponse chatResponse = advisedResponse.response();
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
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(advisedRequest.toPrompt(), answerResponse);
                if (toolExecutionResult.returnDirect()) {
                    return AdvisedResponse.builder()
                            .response(ChatResponse.builder()
                                    .from(answerResponse)
                                    .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                                    .build())
                            .adviseContext(advisedResponse.adviseContext())
                            .build();
                } else {
                    return chain.nextAroundCall(
                            AdvisedRequest.from(advisedRequest)
                                    .messages(toolExecutionResult.conversationHistory())
                                    .advisors(advisedRequest.advisors())
                                    .build());
                }
            }

            return AdvisedResponse.builder()
                    .response(answerResponse)
                    .adviseContext(advisedResponse.adviseContext())
                    .build();
        }
        return advisedResponse;
    }

    private Generation buildAssistantMessage(Generation generation) {
        AssistantMessage assistantMessage = generation.getOutput();
        String rawAnswer = assistantMessage.getText();
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
            toolCalls.add(new AssistantMessage.ToolCall(UUID.randomUUID().toString().replaceAll("-", ""), "function", functionCallResponse.getTool(), functionCallResponse.getParameters()));
        }

        return new Generation(new AssistantMessage(
                extractedJson,
                assistantMessage.getMetadata(),
                toolCalls,
                assistantMessage.getMedia()
        ), generation.getMetadata());
    }


    @NonNull
    @Override
    public AdvisedResponse aroundCall(@NonNull AdvisedRequest advisedRequest,
                                      @NonNull CallAroundAdvisorChain chain) {
        AdvisedResponse response = chain.nextAroundCall(this.rebuildRequest(advisedRequest));
        return this.response(advisedRequest, chain, response);
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
    public static class FunctionCallResponse {

        private String tool;
        private String parameters;

    }
}
