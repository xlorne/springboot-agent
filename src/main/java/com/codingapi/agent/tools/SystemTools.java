package com.codingapi.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

@Component
public class SystemTools implements ToolsProvider{

    @Tool(description = "获取当前时间的函数 返回的时间格式为yyyy-MM-dd HH:mm:ss")
    public String getCurrentDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateFormat.format(System.currentTimeMillis());
    }
}
