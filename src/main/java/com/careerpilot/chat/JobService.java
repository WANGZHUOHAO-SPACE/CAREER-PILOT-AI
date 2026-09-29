package com.careerpilot.chat;

import com.careerpilot.service.UserProfileService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class JobService {

    private static final Map<String, Pattern> SKILL_PATTERNS = skillPatterns();

    private final UserProfileService userProfileService;

    public JobService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public JobAnalysis analyzeJob(String jd) {
        checkJdLength(jd);
        return analyzeJob(jd, userProfileService.getResume());
    }

    private JobAnalysis analyzeJob(String jd, ResumeProfile resume) {
        if (jd == null || jd.isBlank()) {
            return new JobAnalysis(resume.jobTarget(), List.of(), List.of(), List.of(), "请先提供岗位JD，才能进行匹配分析。");
        }

        List<String> jdSkills = SKILL_PATTERNS.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(jd).find())
                .map(Map.Entry::getKey)
                .toList();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : jdSkills) {
            if (resume.skills().contains(skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        String note = jdSkills.isEmpty()
                ? "JD中未识别出预设技术关键词；请结合岗位职责和项目经历进一步判断。"
                : "仅比较JD中的预设技术关键词与数据库简历技能；未评估项目经历、学历等其他要求。";
        return new JobAnalysis(resume.jobTarget(), jdSkills, matched, missing, note);
    }

    public String generateGreeting(String jd) {
        checkJdLength(jd);
        if (jd == null || jd.isBlank()) {
            return "请先提供岗位JD，我再为你生成有针对性的打招呼话术。";
        }

        ResumeProfile resume = userProfileService.getResume();
        JobAnalysis analysis = analyzeJob(jd, resume);
        String opening = "您好，我是" + resume.name() + "，" + resume.major() + "专业，正在寻找" + resume.jobTarget() + "机会。";
        String fit = analysis.matchedSkills().isEmpty()
                ? "我关注到贵公司的岗位，对这次机会很感兴趣。"
                : "看到岗位提及" + String.join("、", analysis.matchedSkills().stream().limit(3).toList()) + "，与我的技能方向契合。";
        return opening + fit + "希望有机会进一步沟通，谢谢！";
    }

    private static void checkJdLength(String jd) {
        if (jd != null && jd.length() > 8000) {
            throw new IllegalArgumentException("岗位JD不能超过 8000 个字符");
        }
    }

    private static Map<String, Pattern> skillPatterns() {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        patterns.put("Java", Pattern.compile("(?i)(?<![a-z])java(?![a-z])"));
        patterns.put("Spring Boot", Pattern.compile("(?i)(?<![a-z])spring[\\s-]*boot(?![a-z])"));
        patterns.put("MySQL", Pattern.compile("(?i)(?<![a-z])mysql(?![a-z])"));
        patterns.put("Git", Pattern.compile("(?i)(?<![a-z])git(?![a-z])"));
        patterns.put("AI辅助开发", Pattern.compile("(?i)AI辅助开发|AI编程|AI coding"));
        patterns.put("Redis", Pattern.compile("(?i)(?<![a-z])redis(?![a-z])"));
        patterns.put("Docker", Pattern.compile("(?i)(?<![a-z])docker(?![a-z])"));
        patterns.put("Linux", Pattern.compile("(?i)(?<![a-z])linux(?![a-z])"));
        patterns.put("MyBatis", Pattern.compile("(?i)(?<![a-z])mybatis(?:-plus)?(?![a-z])"));
        patterns.put("Kafka", Pattern.compile("(?i)(?<![a-z])kafka(?![a-z])"));
        return Collections.unmodifiableMap(patterns);
    }
}
