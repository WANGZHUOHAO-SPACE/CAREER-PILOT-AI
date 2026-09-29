package com.careerpilot.chat;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import com.careerpilot.observability.ObservationScope;
import org.springframework.stereotype.Component;

@Component
public class JobAnalysisTools {

    private final JobService jobService;

    public JobAnalysisTools(JobService jobService) {
        this.jobService = jobService;
    }

    @Tool(description = "仅分析给定岗位JD与当前用户数据库简历的技能匹配情况，返回JD技能、已匹配技能和简历中未列出的技能；不生成打招呼话术。用户要求岗位匹配分析时使用。")
    public JobAnalysis analyzeJob(@ToolParam(description = "完整的岗位JD文本") String jd) {
        return ObservationScope.tool("analyzeJob", () -> jobService.analyzeJob(jd));
    }
}
