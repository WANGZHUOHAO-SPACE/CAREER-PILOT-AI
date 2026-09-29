package com.careerpilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerpilot.common.ResourceNotFoundException;
import com.careerpilot.entity.User;
import com.careerpilot.entity.UserProfile;
import com.careerpilot.mapper.UserMapper;
import com.careerpilot.mapper.UserProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final UserMapper users;
    private final UserProfileMapper profiles;
    private final PasswordEncoder passwords;

    public UserService(UserMapper users, UserProfileMapper profiles, PasswordEncoder passwords) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
    }

    @Transactional
    public UserView register(String username, String email, String password) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (users.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, normalizedEmail)) > 0
                || users.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, normalizedUsername)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or username is already registered");
        }
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(normalizedUsername);
        user.setPasswordHash(passwords.encode(password));
        try {
            users.insert(user);
        }
        catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or username is already registered");
        }
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setName(normalizedUsername);
        profiles.insert(profile);
        return view(user);
    }

    public User login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        User user = users.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, normalizedEmail));
        if (user == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid email or password");
        }
        return user;
    }

    public UserView get(long userId) {
        User user = users.selectById(userId);
        if (user == null) throw new ResourceNotFoundException("User not found");
        return view(user);
    }

    public static UserView view(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
    }

    public record UserView(Long id, String username, String email, String displayName) { }
}
