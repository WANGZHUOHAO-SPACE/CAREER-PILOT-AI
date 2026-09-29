package com.careerpilot.service;

import java.time.LocalDateTime;

import com.careerpilot.entity.Application;
import com.careerpilot.entity.Job;
import com.careerpilot.mapper.ApplicationMapper;
import com.careerpilot.mapper.JobMapper;
import com.careerpilot.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobPersistenceService {

    private final JobMapper jobMapper;
    private final ApplicationMapper applicationMapper;

    public JobPersistenceService(JobMapper jobMapper, ApplicationMapper applicationMapper) {
        this.jobMapper = jobMapper;
        this.applicationMapper = applicationMapper;
    }

    @Transactional
    public JobSaveResult saveJob(String jd, String company, String position, String location, String salary) {
        if (jd == null || jd.isBlank()) {
            throw new IllegalArgumentException("岗位JD不能为空");
        }
        if (jd.length() > 8000) {
            throw new IllegalArgumentException("岗位JD不能超过 8000 个字符");
        }
        checkLength(company, 200, "公司名称");
        checkLength(position, 200, "岗位名称");
        checkLength(location, 100, "工作地点");
        checkLength(salary, 100, "薪资描述");

        Job job = new Job();
        job.setUserId(CurrentUser.id());
        job.setJd(jd.trim());
        job.setCompany(blankToNull(company));
        job.setPosition(blankToNull(position));
        job.setLocation(blankToNull(location));
        job.setSalary(blankToNull(salary));
        job.setCreateTime(LocalDateTime.now());
        if (jobMapper.insert(job) != 1 || job.getId() == null) {
            throw new IllegalStateException("保存岗位失败");
        }

        Application application = new Application();
        application.setUserId(job.getUserId());
        application.setJobId(job.getId());
        application.setStatus("SAVED");
        if (applicationMapper.insert(application) != 1 || application.getId() == null) {
            throw new IllegalStateException("创建申请记录失败");
        }
        return new JobSaveResult(job.getId(), application.getId(), application.getStatus());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过 " + maxLength + " 个字符");
        }
    }
}
