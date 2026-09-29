package com.careerpilot.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.entity.Job;
import com.careerpilot.mapper.JobMapper;
import com.careerpilot.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobQueryService {

    private final JobMapper jobMapper;

    public JobQueryService(JobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    @Transactional(readOnly = true)
    public List<JobSummary> listJobs() {
        return jobMapper.selectList(new LambdaQueryWrapper<Job>()
                        .eq(Job::getUserId, CurrentUser.id()).orderByDesc(Job::getId).last("LIMIT 200"))
                .stream()
                .map(job -> new JobSummary(job.getId(), job.getCompany(), job.getPosition(),
                        job.getJd(), job.getLocation(), job.getSalary(), job.getCreateTime()))
                .toList();
    }
}
