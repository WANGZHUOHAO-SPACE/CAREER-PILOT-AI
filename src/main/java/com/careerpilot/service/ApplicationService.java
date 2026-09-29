package com.careerpilot.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.entity.Application;
import com.careerpilot.entity.Job;
import com.careerpilot.mapper.ApplicationMapper;
import com.careerpilot.mapper.JobMapper;
import com.careerpilot.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Set<String> STATUSES = Set.of("SAVED", "APPLIED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");

    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;

    public ApplicationService(ApplicationMapper applicationMapper, JobMapper jobMapper) {
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummary> searchApplications(String status) {
        LambdaQueryWrapper<Application> query = new LambdaQueryWrapper<>();
        long userId = CurrentUser.id();
        query.eq(Application::getUserId, userId);
        if (status != null && !status.isBlank()) {
            query.eq(Application::getStatus, normalizeStatus(status));
        }
        query.orderByDesc(Application::getId).last("LIMIT 200");
        List<Application> applications = applicationMapper.selectList(query);
        if (applications.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = applications.stream().map(Application::getJobId).distinct().toList();
        Map<Long, Job> jobs = jobMapper.selectList(new QueryWrapper<Job>()
                .eq("user_id", userId).in("id", jobIds)).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        return applications.stream()
                .map(application -> toSummary(application, jobs.get(application.getJobId())))
                .toList();
    }

    @Transactional
    public ApplicationSummary updateApplicationStatus(Long applicationId, String status, String remark) {
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalArgumentException("申请记录ID必须是正整数");
        }
        String normalizedStatus = normalizeStatus(status);
        if (remark != null && remark.length() > 2000) {
            throw new IllegalArgumentException("备注不能超过 2000 个字符");
        }
        long userId = CurrentUser.id();
        Application application = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getId, applicationId).eq(Application::getUserId, userId));
        if (application == null) {
            throw new ResourceNotFoundException("未找到申请记录，ID=" + applicationId);
        }
        Job ownedJob = jobMapper.selectOne(new LambdaQueryWrapper<Job>()
                .eq(Job::getId, application.getJobId()).eq(Job::getUserId, userId));
        if (ownedJob == null) throw new ResourceNotFoundException("未找到申请记录，ID=" + applicationId);
        application.setStatus(normalizedStatus);
        if ("APPLIED".equals(normalizedStatus) && application.getApplyTime() == null) {
            application.setApplyTime(LocalDateTime.now());
        }
        if (remark != null) {
            application.setRemark(remark.trim());
        }
        if (applicationMapper.update(application, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Application>()
                .eq(Application::getId, applicationId).eq(Application::getUserId, userId)) != 1) {
            throw new IllegalStateException("更新申请状态失败");
        }
        return toSummary(application, ownedJob);
    }

    private static String normalizeStatus(String status) {
        if (status == null || !STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("状态必须是 SAVED、APPLIED、INTERVIEW、OFFER、REJECTED 或 WITHDRAWN");
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static ApplicationSummary toSummary(Application application, Job job) {
        return new ApplicationSummary(application.getId(), application.getJobId(),
                job == null ? null : job.getCompany(), job == null ? null : job.getPosition(),
                application.getStatus(), application.getApplyTime(), application.getRemark());
    }
}
