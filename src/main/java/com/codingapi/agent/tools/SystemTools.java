package com.codingapi.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

@Component
public class SystemTools implements ToolsProvider{

    @Tool(description = "获取当前时间的函数 返回的时间格式为yyyy-MM-dd HH:mm:ss")
    public String getCurrentDateTime(@ToolParam(description = "时区") String timeZone) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (timeZone != null && !timeZone.isEmpty()) {
            dateFormat.setTimeZone(java.util.TimeZone.getTimeZone(timeZone));
        }
        return dateFormat.format(System.currentTimeMillis());
    }
}
