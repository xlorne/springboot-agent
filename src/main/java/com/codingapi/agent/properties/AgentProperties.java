package com.codingapi.agent.properties;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;

@Setter
@Getter
public class AgentProperties {

    /**
     * chat memory retrieve size
     */
    private int chatMemoryRetrieveSize = 1000;

    /**
     * default System prompt template file
     */
    private String defaultSystemTemplateFile = "classpath:texts/system.txt";

    /**
     * default Prompt Memory template file
     */
    private String defaultPromptMemoryTemplateFile = "classpath:texts/memory.txt";

    public String getDefaultSystemTemplateText() {
        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource(defaultSystemTemplateFile);
            return IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("load defaultSystemTemplateFile:" + defaultSystemTemplateFile + ",error:", e);
        }
    }

    public String getDefaultPromptMemoryTemplateText() {
        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource(defaultPromptMemoryTemplateFile);
            return IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("load defaultPromptMemoryTemplateFile:" + defaultPromptMemoryTemplateFile + ",error:", e);
        }
    }

}
