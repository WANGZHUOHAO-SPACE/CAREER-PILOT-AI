package com.careerpilot.chat;

import java.util.List;

import com.careerpilot.service.ApplicationService;
import com.careerpilot.service.ApplicationSummary;
import com.careerpilot.observability.ObservationScope;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ApplicationTools {

    private final ApplicationService applicationService;

    public ApplicationTools(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Tool(description = "查询数据库中的求职申请记录及对应岗位；可按状态筛选，省略状态则返回全部。用户询问已保存岗位或申请进度时使用。")
    public List<ApplicationSummary> searchApplications(
            @ToolParam(description = "可选状态：SAVED、APPLIED、INTERVIEW、OFFER、REJECTED、WITHDRAWN", required = false) String status) {
        return ObservationScope.tool("searchApplications",
                () -> applicationService.searchApplications(status).stream().limit(50).toList());
    }

    @Tool(description = "仅在用户明确要求修改申请进度时，按申请记录ID更新状态；首次设为APPLIED时记录申请时间。")
    public ApplicationSummary updateApplicationStatus(
            @ToolParam(description = "申请记录ID，必填") Long applicationId,
            @ToolParam(description = "新状态：SAVED、APPLIED、INTERVIEW、OFFER、REJECTED、WITHDRAWN，必填") String status,
            @ToolParam(description = "可选备注，省略时保留原备注", required = false) String remark) {
        return ObservationScope.tool("updateApplicationStatus",
                () -> applicationService.updateApplicationStatus(applicationId, status, remark));
    }
}
