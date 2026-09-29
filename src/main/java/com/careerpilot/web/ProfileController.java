package com.careerpilot.web;

import com.careerpilot.chat.ResumeProfile;
import com.careerpilot.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final UserProfileService profiles;
    public ProfileController(UserProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public ResumeProfile get() { return profiles.getResume(); }

    @PutMapping
    public ResumeProfile update(@Valid @RequestBody ProfileRequest request) {
        return profiles.update(request.name(), request.major(), request.targetPosition(),
                request.skills(), request.introduction());
    }

    public record ProfileRequest(@NotBlank @Size(max = 100) String name,
            @Size(max = 100) String major,
            @Size(max = 150) String targetPosition,
            @Size(max = 4000) String skills,
            @Size(max = 8000) String introduction) { }
}
