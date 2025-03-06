package com.codingapi.agent.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ToolsContext {

    private final List<ToolsProvider> providers;

    public ToolsContext(@Autowired(required = false) List<ToolsProvider> providers) {
        this.providers = Objects.requireNonNullElseGet(providers, ArrayList::new);
    }

    public Object[] getTools() {
        return providers.toArray();
    }

}
