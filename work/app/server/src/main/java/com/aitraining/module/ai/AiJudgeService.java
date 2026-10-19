package com.aitraining.module.ai;

import com.aitraining.module.ai.entity.AiPromptTemplate;
import com.aitraining.module.ai.llm.LlmClient;
import com.aitraining.module.ai.llm.LlmResponse;
import com.aitraining.module.exam.entity.Question;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 主观题大模型辅助评分。
 * 模型只给建议分与理由，人工复核分记录在 exam_answer.manual_score，
 * 二者可用于计算评分一致性（论文实验：AI 评分与人工评分的相关系数 / 加权 Kappa）。
 *
 * <p>与本项目其他 AI 环节一致，采用「规则兜底 + 大模型增强」的混合策略：
 * 未配置大模型 API Key 时，退化为「参考答案二元组覆盖率」规则评分，
 * 而不是直接记 0 分 —— 否则未配置 Key 的环境下学员简答题必然零分，整卷总分失真。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiJudgeService {

    private final LlmClient llmClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    /** 规则评分的覆盖率低于此值视为不得分，避免只言片语蒙分 */
    private static final double MIN_COVERAGE = 0.05;

    public static class JudgeResult {
        public BigDecimal score = BigDecimal.ZERO;
        public String reason = "未启用大模型评分，待人工评分";
        public boolean aiScored = false;
    }

    /** 兼容旧调用：满分取题库默认分值 */
    public JudgeResult judge(Question q, String userAnswer) {
        return judge(q, userAnswer, q.getScore() == null ? BigDecimal.TEN : q.getScore());
    }

    /**
     * @param fullScore 卷面为该题分配的分值（可能不同于题库默认分值），
     *                  必须以它封顶，否则整卷得分凑不满总分
     */
    public JudgeResult judge(Question q, String userAnswer, BigDecimal fullScore) {
        BigDecimal full = fullScore == null
                ? (q.getScore() == null ? BigDecimal.TEN : q.getScore()) : fullScore;
        if (!llmClient.available()) {
            return ruleJudge(q, userAnswer, full);
        }
        JudgeResult r = new JudgeResult();
        AiPromptTemplate tpl = promptService.getByScene("JUDGE");
        Map<String, Object> vars = new HashMap<>();
        vars.put("question", q.getContent());
        vars.put("answer", q.getAnswer());
        vars.put("analysis", q.getAnalysis());
        vars.put("fullScore", full);
        vars.put("userAnswer", userAnswer);
        String prompt = tpl == null
                ? "题目：" + q.getContent() + "\n参考答案：" + q.getAnswer()
                + "\n学员作答：" + userAnswer + "\n满分：" + full
                + "\n请输出 JSON：{\"score\":数字,\"reason\":\"\"}"
                : PromptService.render(tpl.getContent(), vars);

        LlmResponse resp = llmClient.chat("你是严谨的阅卷老师，只输出 JSON。", prompt, true);
        if (!resp.isSuccess()) {
            r.reason = "AI 评分失败：" + resp.getError() + "，已按规则评分";
            JudgeResult rule = ruleJudge(q, userAnswer, full);
            rule.reason = r.reason + "；" + rule.reason;
            return rule;
        }
        try {
            String content = resp.getContent().trim();
            int l = content.indexOf('{');
            int rr = content.lastIndexOf('}');
            if (l >= 0 && rr > l) {
                content = content.substring(l, rr + 1);
            }
            JsonNode node = objectMapper.readTree(content);
            double score = node.path("score").asDouble(0);
            score = Math.max(0, Math.min(full.doubleValue(), score));
            r.score = BigDecimal.valueOf(score).setScale(1, java.math.RoundingMode.HALF_UP);
            r.reason = node.path("reason").asText("");
            r.aiScored = true;
        } catch (Exception e) {
            r.reason = "AI 评分结果解析失败，已按规则评分";
            JudgeResult rule = ruleJudge(q, userAnswer, full);
            rule.reason = r.reason + "；" + rule.reason;
            return rule;
        }
        return r;
    }

    /**
     * 规则评分：以参考答案的字符二元组为关键词集合，计算学员作答的覆盖率。
     * 纯文本包含匹配，不依赖分词器，对中文简答题稳定可解释。
     */
    private JudgeResult ruleJudge(Question q, String userAnswer, BigDecimal full) {
        JudgeResult r = new JudgeResult();
        if (userAnswer == null || userAnswer.isBlank()) {
            r.reason = "未作答";
            return r;
        }
        Set<String> refGrams = bigrams(q.getAnswer());
        if (refGrams.isEmpty()) {
            r.reason = "参考答案为空，待人工评分";
            return r;
        }
        String ua = clean(userAnswer);
        long hit = refGrams.stream().filter(ua::contains).count();
        double coverage = hit * 1.0 / refGrams.size();
        if (coverage < MIN_COVERAGE) {
            r.reason = String.format("规则评分：与参考答案关键表述几乎无重合（%d/%d），得 0 分", hit, refGrams.size());
            return r;
        }
        BigDecimal score = full.multiply(BigDecimal.valueOf(coverage))
                .setScale(1, java.math.RoundingMode.HALF_UP).min(full);
        r.score = score;
        r.reason = String.format("规则评分：命中参考答案关键表述 %d/%d（覆盖率 %.0f%%），得分 %s/%s",
                hit, refGrams.size(), coverage * 100, score, full);
        r.aiScored = false;
        return r;
    }

    /** 参考答案切分为字符二元组，已去掉标点与空白 */
    private Set<String> bigrams(String text) {
        String t = clean(text);
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            set.add(t.substring(i, i + 2));
        }
        return set;
    }

    private String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s，。、；：？！,.;:?!\"'（）()《》\\[\\]【】…—\\-]", "");
    }
}

