package com.careerpilot.chat;

import java.util.List;

public record JobAnalysis(
        String jobTarget,
        List<String> jdSkills,
        List<String> matchedSkills,
        List<String> missingSkills,
        String note
) {
}
