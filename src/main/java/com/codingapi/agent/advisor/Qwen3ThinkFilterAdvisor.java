package com.codingapi.agent.advisor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Qwen3ThinkFilterAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private final boolean thinkEnabled;

    private boolean qwen3Model = false;

    public Qwen3ThinkFilterAdvisor(boolean thinkEnabled) {
        this.thinkEnabled = thinkEnabled;
    }

    private void checkModelName(AdvisedRequest advisedRequest) {
        String modelName = advisedRequest.chatModel().getDefaultOptions().getModel();
        this.qwen3Model = modelName != null && modelName.contains("qwen3");
    }

    private List<Generation> filterGenerations(List<Generation> generations) {
        if (thinkEnabled) {
            return generations;
        }
        List<Generation> generationList = new ArrayList<>();
        for (Generation generation : generations) {
            AssistantMessage assistantMessage = generation.getOutput();
            String text = assistantMessage.getText();
            if (text.contains("<think>")) {
                text = text.replaceAll("(?s)<think>.*?</think>", "");
                text = text.replaceAll("(?m)^[ \\t]*\\r?\\n", "");
            }
            AssistantMessage responseMessage = new AssistantMessage(text,
                    assistantMessage.getMetadata(),
                    assistantMessage.getToolCalls(),
                    assistantMessage.getMedia());
            generationList.add(new Generation(responseMessage));
        }
        return generationList;
    }


    private AdvisedRequest filterRequest(AdvisedRequest advisedRequest) {
        if (thinkEnabled) {
            return advisedRequest;
        }
        String question = advisedRequest.userText();
        question = question + "/no_think";
        return AdvisedRequest.from(advisedRequest)
                .userText(question)
                .build();
    }

    @Override
    @NonNull
    public AdvisedResponse aroundCall(@NonNull AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        this.checkModelName(advisedRequest);
        if (qwen3Model) {
            AdvisedRequest request = this.filterRequest(advisedRequest);
            AdvisedResponse response = chain.nextAroundCall(request);
            assert response.response() != null;
            List<Generation> generations = this.filterGenerations(response.response().getResults());
            return AdvisedResponse.builder()
                    .response(ChatResponse.builder()
                            .generations(generations)
                            .build())
                    .adviseContext(advisedRequest.adviseContext())
                    .build();
        }else {
            return chain.nextAroundCall(advisedRequest);
        }
    }

    @Override
    @NonNull
    public Flux<AdvisedResponse> aroundStream(@NonNull AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        this.checkModelName(advisedRequest);
        if (qwen3Model) {
            AdvisedRequest request = this.filterRequest(advisedRequest);
            return chain.nextAroundStream(request)
                    .map(advisedResponse -> {
                        assert advisedResponse.response() != null;
                        List<Generation> generations = this.filterGenerations(advisedResponse.response().getResults());
                        return AdvisedResponse.builder()
                                .response(ChatResponse.builder()
                                        .generations(generations)
                                        .build())
                                .adviseContext(advisedRequest.adviseContext())
                                .build();
                    });
        }else {
            return chain.nextAroundStream(advisedRequest);
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
