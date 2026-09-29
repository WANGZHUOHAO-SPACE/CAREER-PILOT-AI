package com.careerpilot.mcp;

import java.util.List;
import java.util.function.Supplier;

import com.careerpilot.chat.JobAnalysis;
import com.careerpilot.chat.JobService;
import com.careerpilot.chat.ResumeProfile;
import com.careerpilot.rag.KnowledgeService;
import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.ApplicationSummary;
import com.careerpilot.service.UserProfileService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "app.mcp.private-tools-enabled", havingValue = "true")
public class CareerPilotMcpTools {

    private static final Logger log = LoggerFactory.getLogger(CareerPilotMcpTools.class);

    private final UserProfileService userProfileService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final KnowledgeService knowledgeService;

    public CareerPilotMcpTools(UserProfileService userProfileService, JobService jobService,
            ApplicationService applicationService, KnowledgeService knowledgeService) {
        this.userProfileService = userProfileService;
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.knowledgeService = knowledgeService;
    }

    @McpTool(name = "getResume", description = "读取当前用户在 MySQL 中保存的简历资料；不修改数据。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public ResumeProfile getResume() {
        return safeCall(userProfileService::getResume);
    }

    @McpTool(name = "analyzeJob", description = "比较岗位 JD 与当前用户简历中的技能，返回已匹配和未列出的技能；不保存岗位。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public JobAnalysis analyzeJob(@McpToolParam(description = "完整的岗位 JD 文本") String jd) {
        return safeCall(() -> jobService.analyzeJob(jd));
    }

    @McpTool(name = "searchApplications", description = "查询已保存岗位与求职申请记录，可选按申请状态筛选；不修改记录。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<ApplicationSummary> searchApplications(
            @McpToolParam(description = "可选状态：SAVED、APPLIED、INTERVIEW、OFFER、REJECTED、WITHDRAWN", required = false)
            String status) {
        return safeCall(() -> applicationService.searchApplications(status).stream().limit(50).toList());
    }

    @McpTool(name = "searchKnowledge", description = "对已上传的个人求职知识库做向量相似度检索，最多返回四个相关片段及来源；不修改知识库。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<KnowledgeService.KnowledgeHit> searchKnowledge(
            @McpToolParam(description = "要检索的用户经历、项目或求职资料问题") String query) {
        return safeCall(() -> knowledgeService.search(query));
    }

    private static <T> T safeCall(Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (IllegalArgumentException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            log.warn("MCP tool failed: {}", exception.getClass().getSimpleName());
            throw new IllegalStateException("MCP 工具暂不可用");
        }
    }
}
