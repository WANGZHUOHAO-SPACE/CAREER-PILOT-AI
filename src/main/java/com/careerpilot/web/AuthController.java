package com.careerpilot.web;

import com.careerpilot.security.CurrentUser;
import com.careerpilot.security.JwtService;
import com.careerpilot.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final JwtService tokens;

    public AuthController(UserService users, JwtService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public UserService.UserView register(@Valid @RequestBody RegisterRequest request) {
        return users.register(request.username(), request.email(), request.password());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var user = users.login(request.email(), request.password());
        return new LoginResponse(tokens.generateToken(user.getId()), UserService.view(user));
    }

    @GetMapping("/me")
    public UserService.UserView me() { return users.get(CurrentUser.id()); }

    @PostMapping("/logout")
    public void logout() { /* Stateless JWT: client discards the token. */ }

    public record RegisterRequest(@NotBlank @Size(max = 50) String username,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password) { }
    public record LoginRequest(@NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 72) String password) { }
    public record LoginResponse(String token, UserService.UserView user) { }
}
