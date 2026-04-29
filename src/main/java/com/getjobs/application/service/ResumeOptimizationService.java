package com.getjobs.application.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.getjobs.worker.manager.PlaywrightManager;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeOptimizationService {
    private final AiService aiService;
    private final PlaywrightManager playwrightManager;

    public ResumeOptimizationResponse optimize(ResumeOptimizationRequest request) {
        validate(request);
        if (Boolean.TRUE.equals(request.getUseBossResume())) {
            request.setResumeText(fetchBossResumeText());
        }
        String raw = aiService.sendRequest(buildPrompt(request));
        ResumeOptimizationResponse response = parseResponse(raw);
        response.setSourceResumeText(request.getResumeText());
        response.setResumeSource(Boolean.TRUE.equals(request.getUseBossResume()) ? "boss" : "manual");
        return response;
    }

    public String fetchBossResumeText() {
        return playwrightManager.fetchBossOnlineResumeText();
    }

    private void validate(ResumeOptimizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (!Boolean.TRUE.equals(request.getUseBossResume()) && isBlank(request.getResumeText())) {
            throw new IllegalArgumentException("简历内容不能为空");
        }
        // 目标岗位JD允许为空：为空时执行通用简历优化。
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildPrompt(ResumeOptimizationRequest request) {
        String targetRole = defaultText(request.getTargetRole(), "未指定");
        String platform = defaultText(request.getPlatform(), "Boss直聘");
        String language = defaultText(request.getLanguage(), "中文");
        String extraRequirements = defaultText(request.getExtraRequirements(), "无");
        String jobDescription = defaultText(request.getJobDescription(), "未提供目标岗位JD；请执行通用求职简历优化，重点提升表达清晰度、结构完整性、ATS可读性和Boss直聘投递转化率。");

        return """
                你是资深招聘顾问、ATS简历优化专家和中文技术简历编辑。请基于候选人的真实简历与目标岗位JD做简历润色；如果未提供目标岗位JD，则执行通用简历优化。

                必须遵守：
                1. 不得编造候选人未提供的公司、项目、年限、学历、证书、数字成果或技术经验。
                2. 可以重写表达、调整顺序、提取关键词、建议补充待确认信息，但必须标记为待补充，不能写进最终简历正文。
                3. 输出必须是严格 JSON，不要使用 Markdown 代码块，不要添加 JSON 以外的说明。
                4. final_resume 要适合 %s 投递，语言为 %s，重点面向目标岗位：%s；如果目标岗位未指定或JD为空，则输出通用投递版本。
                5. bullet 要优先使用“动作 + 技术/方法 + 结果/影响”的表达；没有真实数字时不要硬编数字。

                请返回以下 JSON 结构：
                {
                  "overall_score": 0-100,
                  "ats_score": 0-100,
                  "match_score": 0-100,
                  "summary": "一句话总结匹配情况",
                  "strengths": ["优势1"],
                  "risks": ["风险1"],
                  "missing_keywords": ["JD中重要但简历缺失的关键词"],
                  "recommended_keywords": ["建议自然补充的关键词"],
                  "rewrite_suggestions": [
                    {"section":"模块名", "before":"原句或问题", "after":"建议改写", "reason":"原因"}
                  ],
                  "integrity_warnings": ["可能涉及事实补充或需确认的内容"],
                  "final_resume": "润色后的完整简历文本",
                  "boss_greeting": "适合Boss直聘的简短打招呼语"
                }

                候选人简历：
                %s

                目标岗位JD：
                %s

                额外要求：
                %s
                """.formatted(platform, language, targetRole, request.getResumeText(), jobDescription, extraRequirements);
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private ResumeOptimizationResponse parseResponse(String raw) {
        String jsonText = extractJson(raw);
        JSONObject json = new JSONObject(jsonText);
        ResumeOptimizationResponse response = new ResumeOptimizationResponse();
        response.setOverallScore(json.optInt("overall_score", 0));
        response.setAtsScore(json.optInt("ats_score", 0));
        response.setMatchScore(json.optInt("match_score", 0));
        response.setSummary(json.optString("summary", ""));
        response.setStrengths(toStringList(json.optJSONArray("strengths")));
        response.setRisks(toStringList(json.optJSONArray("risks")));
        response.setMissingKeywords(toStringList(json.optJSONArray("missing_keywords")));
        response.setRecommendedKeywords(toStringList(json.optJSONArray("recommended_keywords")));
        response.setRewriteSuggestions(toSuggestionList(json.optJSONArray("rewrite_suggestions")));
        response.setIntegrityWarnings(toStringList(json.optJSONArray("integrity_warnings")));
        response.setFinalResume(json.optString("final_resume", ""));
        response.setBossGreeting(json.optString("boss_greeting", ""));
        response.setRawResponse(raw);
        return response;
    }

    private String extractJson(String raw) {
        if (isBlank(raw)) {
            throw new IllegalArgumentException("AI返回内容为空");
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private List<String> toStringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private List<RewriteSuggestion> toSuggestionList(JSONArray array) {
        List<RewriteSuggestion> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            RewriteSuggestion suggestion = new RewriteSuggestion();
            suggestion.setSection(item.optString("section", ""));
            suggestion.setBefore(item.optString("before", ""));
            suggestion.setAfter(item.optString("after", ""));
            suggestion.setReason(item.optString("reason", ""));
            values.add(suggestion);
        }
        return values;
    }

    @Data
    public static class ResumeOptimizationRequest {
        private String resumeText;
        private Boolean useBossResume;
        private String jobDescription;
        private String targetRole;
        private String platform;
        private String language;
        private String extraRequirements;
    }

    @Data
    public static class ResumeOptimizationResponse {
        private int overallScore;
        private int atsScore;
        private int matchScore;
        private String summary;
        private List<String> strengths;
        private List<String> risks;
        private List<String> missingKeywords;
        private List<String> recommendedKeywords;
        private List<RewriteSuggestion> rewriteSuggestions;
        private List<String> integrityWarnings;
        private String finalResume;
        private String bossGreeting;
        private String sourceResumeText;
        private String resumeSource;
        private String rawResponse;
    }

    @Data
    public static class RewriteSuggestion {
        private String section;
        private String before;
        private String after;
        private String reason;
    }
}
