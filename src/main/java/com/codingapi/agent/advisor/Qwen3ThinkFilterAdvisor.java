package com.codingapi.agent.advisor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Qwen3ThinkFilterAdvisor implements CallAdvisor, StreamAdvisor {

    private final boolean thinkEnabled;

    private boolean qwen3Model = false;

    public Qwen3ThinkFilterAdvisor(boolean thinkEnabled) {
        this.thinkEnabled = thinkEnabled;
    }

    private void checkModelName(ChatClientRequest advisedRequest) {
        ChatOptions chatOptions = advisedRequest.prompt().getOptions();
        if (chatOptions != null) {
            String modelName = chatOptions.getModel();
            this.qwen3Model = modelName != null && modelName.contains("qwen3");
        }
    }

    private List<Generation> filterGenerations(List<Generation> generations) {
        if (thinkEnabled) {
            return generations;
        }
        List<Generation> generationList = new ArrayList<>();
        for (Generation generation : generations) {
            AssistantMessage assistantMessage = generation.getOutput();
            String text = assistantMessage.getText();
            if (text != null) {
                if (text.contains("<think>")) {
                    text = text.replaceAll("(?s)<think>.*?</think>", "");
                    text = text.replaceAll("(?m)^[ \\t]*\\r?\\n", "");
                }
                AssistantMessage responseMessage = new AssistantMessage(text,
                        assistantMessage.getMetadata(),
                        assistantMessage.getToolCalls(),
                        assistantMessage.getMedia());
                generationList.add(new Generation(responseMessage));
            } else {
                generationList.add(generation);
            }
        }
        return generationList;
    }


    private ChatClientRequest filterRequest(ChatClientRequest advisedRequest) {
        if (thinkEnabled) {
            return advisedRequest;
        }
        Prompt prompt = advisedRequest.prompt();
        UserMessage userMessage = prompt.getUserMessage();
        String question = userMessage.getText();
        question = question + "/no_think";
        prompt = prompt.augmentUserMessage(question);
        return ChatClientRequest.builder()
                .prompt(prompt)
                .context(advisedRequest.context())
                .build();
    }

    @NonNull
    @Override
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest,
                                         @NonNull CallAdvisorChain callAdvisorChain) {
        this.checkModelName(chatClientRequest);
        if (qwen3Model) {
            ChatClientRequest request = this.filterRequest(chatClientRequest);
            ChatClientResponse response = callAdvisorChain.nextCall(request);
            assert response.chatResponse() != null;
            List<Generation> generations = response.chatResponse().getResults();
            return ChatClientResponse.builder()
                    .chatResponse(ChatResponse.builder()
                            .generations(this.filterGenerations(generations))
                            .build())
                    .context(request.context())
                    .build();
        } else {
            return callAdvisorChain.nextCall(chatClientRequest);
        }
    }

    @NonNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest,
                                                 @NonNull StreamAdvisorChain streamAdvisorChain) {
        this.checkModelName(chatClientRequest);
        if (qwen3Model) {
            ChatClientRequest request = this.filterRequest(chatClientRequest);
            return streamAdvisorChain.nextStream(request)
                    .map(advisedResponse -> {
                        assert advisedResponse.chatResponse() != null;
                        List<Generation> generations = this.filterGenerations(advisedResponse.chatResponse().getResults());
                        return ChatClientResponse.builder()
                                .chatResponse(ChatResponse.builder()
                                        .generations(this.filterGenerations(generations))
                                        .build())
                                .context(request.context())
                                .build();
                    });
        } else {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return "qwen3ThinkFilter";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
