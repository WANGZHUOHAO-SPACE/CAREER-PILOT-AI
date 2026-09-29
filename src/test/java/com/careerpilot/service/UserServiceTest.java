package com.careerpilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.entity.User;
import com.careerpilot.entity.UserProfile;
import com.careerpilot.mapper.UserMapper;
import com.careerpilot.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test void registrationHashesPasswordAndOmitsItFromResponse() {
        UserMapper users = mock(UserMapper.class);
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        when(users.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(users.insert(any(User.class))).thenAnswer(call -> {
            call.getArgument(0, User.class).setId(7L); return 1;
        });
        UserService service = new UserService(users, profiles, new BCryptPasswordEncoder());
        UserService.UserView result = service.register("demo", "DEMO@example.com", "Example123!");
        var user = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).insert(user.capture());
        assertNotEquals("Example123!", user.getValue().getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("Example123!", user.getValue().getPasswordHash()));
        assertEquals("demo@example.com", user.getValue().getEmail());
        assertEquals(7L, result.id());
        assertEquals(4, result.getClass().getRecordComponents().length);
        verify(profiles).insert(any(UserProfile.class));
    }

    @Test void duplicateAndWrongPasswordDoNotLeakAccountExistence() {
        UserMapper users = mock(UserMapper.class);
        UserService service = new UserService(users, mock(UserProfileMapper.class), new BCryptPasswordEncoder());
        when(users.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(ResponseStatusException.class, () -> service.register("demo", "demo@example.com", "Example123!"));
        assertEquals("Invalid email or password", assertThrows(BadCredentialsException.class,
                () -> service.login("nobody@example.com", "wrong")).getMessage());
        User user = new User();
        user.setPasswordHash(new BCryptPasswordEncoder().encode("correct-password"));
        when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        assertEquals("Invalid email or password", assertThrows(BadCredentialsException.class,
                () -> service.login("someone@example.com", "wrong")).getMessage());
    }
}
