# SpringBoot-Agent with LLM Tools

## Introduction

This example demonstrates how to integrate a SpringBoot-Agent with an LLM (Large Language Model) using the Ollama server. The SpringBoot-Agent acts as a bridge between your application and the LLM, enabling you to leverage the power of language models for various tasks.

## Prerequisites

- **Ollama Server**: Ensure you have the Ollama server running with at least 6GB of GPU memory.
- **LLM Model**: The example uses the `qwen3:4b` model. Make sure this model is available on your Ollama server.

## How to Run

1. **Start the Ollama Server**: Ensure the Ollama server is up and running.
2. **Start the SpringBoot-Agent**: Run the SpringBoot-Agent application.

## Developing the Agent

1. **Configure Properties**: Modify the default properties in `src/main/resources/application.properties` to match your environment:

    ```properties
    spring.application.name=springboot-agent

    spring.ai.openai.api-key=Ollama
    spring.ai.openai.chat.options.model=qwen2.5:32b
    spring.ai.openai.chat.base-url=http://10.95.90.95:18000/
    spring.ai.openai.chat.completions-path=/v1/chat/completions

    codingapi.agent.chat-memory-size=10
    codingapi.agent.default-prompt-memory-template-file=classpath:texts/memory.txt
    codingapi.agent.default-system-template-file=classpath:texts/system.txt
    ```

2. **Build the Agent**: Run the following command to build the agent:

    ```bash
    mvn clean package
    ```

## How to Use

You can interact with the SpringBoot-Agent by sending a POST request to the `/agent/chat` endpoint. Here's an example using `curl`:

```bash
curl --location 'http://localhost:8080/agent/chat' \
--header 'Content-Type: application/json' \
--data '{
    "message":"I am lorne, please help me check the time",
    "chatId":"1"
}'
```

## References

- [SpringBoot-Ai Documentation](https://docs.spring.io/spring-ai/reference/1.0/api/tools.html)
- [Ollama Official Website](https://ollama.com/)

## Requirements

- **Ollama Server**: Requires at least 6GB of GPU memory.
- **LLM Model**: The example uses the `qwen3:4b` model.

