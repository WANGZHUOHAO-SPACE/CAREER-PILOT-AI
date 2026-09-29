package com.careerpilot.chat;

import java.util.List;

public record ResumeProfile(Long id, String name, String major, String jobTarget,
        List<String> skills, String introduction) {
}
