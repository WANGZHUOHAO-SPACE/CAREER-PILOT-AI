package com.careerpilot.chat;

import com.careerpilot.service.UserProfileService;
import com.careerpilot.observability.ObservationScope;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ResumeTools {

    private final UserProfileService userProfileService;

    public ResumeTools(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Tool(description = "从数据库获取当前用户简历，包括姓名、专业、求职方向、技能和简介；不分析岗位，也不生成话术。需要个人资料时使用。")
    public ResumeProfile getResume() {
        return ObservationScope.tool("getResume", userProfileService::getResume);
    }
}
