package com.careerpilot.chat;

import com.careerpilot.service.JobPersistenceService;
import com.careerpilot.service.JobSaveResult;
import com.careerpilot.observability.ObservationScope;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class JobPersistenceTools {

    private final JobPersistenceService jobPersistenceService;

    public JobPersistenceTools(JobPersistenceService jobPersistenceService) {
        this.jobPersistenceService = jobPersistenceService;
    }

    @Tool(description = "将用户明确要求保存的岗位JD写入数据库，并创建一条状态为SAVED的申请记录。只保存用户提供的信息，不推测缺失的公司、职位、地点或薪资。")
    public JobSaveResult saveJob(
            @ToolParam(description = "岗位JD原文，必填") String jd,
            @ToolParam(description = "公司名称，未知可省略", required = false) String company,
            @ToolParam(description = "岗位名称，未知可省略", required = false) String position,
            @ToolParam(description = "工作地点，未知可省略", required = false) String location,
            @ToolParam(description = "薪资描述，未知可省略", required = false) String salary) {
        return ObservationScope.tool("saveJob",
                () -> jobPersistenceService.saveJob(jd, company, position, location, salary));
    }
}
