package com.careerpilot.service;

import java.util.Arrays;
import java.util.List;

import com.careerpilot.chat.ResumeProfile;
import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.entity.UserProfile;
import com.careerpilot.mapper.UserProfileMapper;
import com.careerpilot.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;
    public UserProfileService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    public ResumeProfile getResume() {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, CurrentUser.id()));
        if (profile == null) {
            throw new ResourceNotFoundException("未找到当前用户简历");
        }
        List<String> skills = profile.getSkills() == null || profile.getSkills().isBlank()
                ? List.of()
                : Arrays.stream(profile.getSkills().split("[,，]"))
                        .map(String::trim)
                        .filter(skill -> !skill.isEmpty())
                        .toList();
        return new ResumeProfile(profile.getId(), profile.getName(), profile.getMajor(),
                profile.getTargetPosition(), skills, profile.getIntroduction());
    }

    public ResumeProfile update(String name, String major, String targetPosition,
            String skills, String introduction) {
        long userId = CurrentUser.id();
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null) throw new ResourceNotFoundException("未找到当前用户简历");
        LambdaUpdateWrapper<UserProfile> update = new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getId, profile.getId()).eq(UserProfile::getUserId, userId)
                .set(UserProfile::getName, name.trim())
                .set(UserProfile::getMajor, major == null ? null : major.trim())
                .set(UserProfile::getTargetPosition, targetPosition == null ? null : targetPosition.trim())
                .set(UserProfile::getSkills, skills == null ? null : skills.trim())
                .set(UserProfile::getIntroduction, introduction == null ? null : introduction.trim());
        if (userProfileMapper.update(null, update) != 1) {
            throw new ResourceNotFoundException("未找到当前用户简历");
        }
        return getResume();
    }
}
