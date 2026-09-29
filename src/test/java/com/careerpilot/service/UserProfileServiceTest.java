package com.careerpilot.service;

import java.util.List;
import com.careerpilot.TestSecurity;
import com.careerpilot.chat.ResumeTools;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.ArgumentMatchers.any;

import com.careerpilot.chat.ResumeProfile;
import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.entity.UserProfile;
import com.careerpilot.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {
    @BeforeEach void authenticate() { TestSecurity.as(1); }

    @Test
    void readsCurrentProfileAndSplitsSkills() {
        UserProfileMapper mapper = mock(UserProfileMapper.class);
        UserProfile profile = new UserProfile();
        profile.setId(1L);
        profile.setName("林同学");
        profile.setMajor("软件工程");
        profile.setTargetPosition("Java后端开发实习");
        profile.setSkills("Java, Spring Boot，MySQL");
        profile.setIntroduction("求职简介");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        ResumeProfile resume = new UserProfileService(mapper).getResume();

        assertEquals(List.of("Java", "Spring Boot", "MySQL"), resume.skills());
        assertEquals("求职简介", resume.introduction());
    }

    @Test
    void reportsMissingProfile() {
        UserProfileMapper mapper = mock(UserProfileMapper.class);
        assertThrows(ResourceNotFoundException.class, () -> new UserProfileService(mapper).getResume());
    }

    @Test
    void resumeToolUsesAuthenticatedUserNotModelSuppliedId() {
        UserProfileMapper mapper = mock(UserProfileMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<UserProfile> query = call.getArgument(0);
            query.getSqlSegment();
            UserProfile profile = new UserProfile();
            profile.setId(8L);
            profile.setName(query.getParamNameValuePairs().containsValue(2L) ? "User B" : "User A");
            return profile;
        });
        ResumeTools tool = new ResumeTools(new UserProfileService(mapper));
        assertEquals("User A", tool.getResume().name());
        TestSecurity.as(2);
        assertEquals("User B", tool.getResume().name());
    }
}
