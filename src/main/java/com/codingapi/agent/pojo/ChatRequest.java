package com.codingapi.agent.pojo;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChatRequest {
    private String chatId;
    private String message;

    private boolean think = true;
}
