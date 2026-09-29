package com.careerpilot.chat;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import com.careerpilot.observability.ObservationScope;
import org.springframework.stereotype.Component;

@Component
public class GreetingTools {

    private final JobService jobService;

    public GreetingTools(JobService jobService) {
        this.jobService = jobService;
    }

    @Tool(description = "仅根据给定岗位JD和当前用户数据库简历，生成一段适合BOSS直聘私信的简短打招呼话术；不输出岗位匹配报告。用户要求打招呼或开场私信时使用。")
    public String generateGreeting(@ToolParam(description = "完整的岗位JD文本") String jd) {
        return ObservationScope.tool("generateGreeting", () -> jobService.generateGreeting(jd));
    }
}
