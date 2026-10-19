package com.aitraining.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.common.BizException;
import com.aitraining.module.ai.entity.AiGenRecord;
import com.aitraining.module.ai.entity.AiPromptTemplate;
import com.aitraining.module.ai.entity.EffectEval;
import com.aitraining.module.ai.llm.LlmClient;
import com.aitraining.module.ai.llm.LlmResponse;
import com.aitraining.module.ai.mapper.AiGenRecordMapper;
import com.aitraining.module.ai.mapper.EffectEvalMapper;
import com.aitraining.module.exam.entity.ExamRecord;
import com.aitraining.module.exam.mapper.ExamRecordMapper;
import com.aitraining.module.skill.SkillGapService;
import com.aitraining.module.skill.dto.GapResultVO;
import com.aitraining.module.skill.entity.EmpSkill;
import com.aitraining.module.skill.entity.PostSkill;
import com.aitraining.module.skill.mapper.EmpSkillMapper;
import com.aitraining.module.skill.mapper.PostSkillMapper;
import com.aitraining.module.system.entity.SysUser;
import com.aitraining.module.system.mapper.SysUserMapper;
import com.aitraining.module.train.TrainPlanService;
import com.aitraining.module.train.entity.PlanCourse;
import com.aitraining.module.train.entity.TrainPlan;
import com.aitraining.module.train.mapper.PlanCourseMapper;
import com.aitraining.module.train.mapper.TrainPlanMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 培训效果诊断与补训方案生成 —— 培训闭环的最后一环。
 *
 * <p>闭环设计：
 * <pre>
 *   前测/后测成绩 ──▶ 考核结果回写技能档案（等级提升）
 *                          │
 *                          ▼
 *              重新量化技能差距 ──▶ 差距缩小率
 *                          │
 *         规则判定结论 + 大模型转写诊断说明 ──▶ 未达标自动生成补训方案
 * </pre>
 *
 * 规则负责确定性判定（不依赖大模型也能出结论），模型负责可读性与建议质量，
 * 与方案生成环节保持一致的「规则约束 + 模型润色」混合策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectService {

    private final EffectEvalMapper evalMapper;
    private final ExamRecordMapper examRecordMapper;
    private final TrainPlanMapper planMapper;
    private final PlanCourseMapper planCourseMapper;
    private final SysUserMapper userMapper;
    private final PostSkillMapper postSkillMapper;
    private final EmpSkillMapper empSkillMapper;
    private final SkillGapService gapService;
    private final TrainPlanService planService;
    private final LlmClient llmClient;
    private final PromptService promptService;
    private final AiGenRecordMapper genRecordMapper;
    private final ObjectMapper objectMapper;

    /** 85 分以上提升 2 级，60 分以上提升 1 级，低于及格不提升 */
    private static final BigDecimal HIGH = BigDecimal.valueOf(85);
    private static final BigDecimal PASS = BigDecimal.valueOf(60);

    @Transactional
    public EffectEval evaluate(Long planId, Long userId) {
        TrainPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("培训计划不存在");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("员工不存在");
        }
        List<ExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getPlanId, planId).eq(ExamRecord::getUserId, userId)
                .orderByAsc(ExamRecord::getId));
        BigDecimal pre = records.stream().filter(r -> "PRE".equals(r.getExamStage()))
                .map(ExamRecord::getTotalScore).filter(Objects::nonNull)
                .reduce((a, b) -> b).orElse(null);
        BigDecimal post = records.stream().filter(r -> "POST".equals(r.getExamStage()))
                .map(ExamRecord::getTotalScore).filter(Objects::nonNull)
                .reduce((a, b) -> b).orElse(null);
        if (post == null) {
            throw new BizException("该员工还没有培训后测成绩，无法进行效果诊断");
        }

        // 1) 培训前差距：计划课程记录中登记的差距 × 岗位权重（按技能去重）
        BigDecimal beforeGap = calcPlanGap(planId, user);
        // 2) 考核结果回写技能等级
        applyExamToSkill(user, planId, post);
        // 3) 重新量化差距
        GapResultVO gapNow = gapService.analyze(userId);
        BigDecimal afterGap = gapNow.getTotalWeightedGap();
        BigDecimal finishRate = planService.finishRate(planId);

        BigDecimal improve = pre == null ? BigDecimal.ZERO : post.subtract(pre);
        BigDecimal reduce = beforeGap.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                : beforeGap.subtract(afterGap).divide(beforeGap, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        if (reduce.compareTo(BigDecimal.ZERO) < 0) {
            reduce = BigDecimal.ZERO;
        }
        if (reduce.compareTo(BigDecimal.valueOf(100)) > 0) {
            reduce = BigDecimal.valueOf(100);
        }

        String resultLevel;
        if (post.compareTo(HIGH) >= 0 && improve.compareTo(BigDecimal.TEN) >= 0) {
            resultLevel = "EXCELLENT";
        } else if (post.compareTo(PASS) >= 0) {
            resultLevel = "PASS";
        } else {
            resultLevel = "FAIL";
        }

        EffectEval eval = evalMapper.selectOne(new LambdaQueryWrapper<EffectEval>()
                .eq(EffectEval::getPlanId, planId).eq(EffectEval::getUserId, userId));
        boolean isNew = eval == null;
        if (isNew) {
            eval = new EffectEval();
            eval.setPlanId(planId);
            eval.setUserId(userId);
        }
        eval.setPreScore(pre);
        eval.setPostScore(post);
        eval.setScoreImprove(improve);
        eval.setFinishRate(finishRate);
        eval.setGapReduce(reduce);
        eval.setEvalResult(resultLevel);
        try {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("totalWeightedGap", beforeGap);
            before.put("items", gapNow.getItems().stream()
                    .filter(i -> i.getGap() != null && i.getGap() > 0)
                    .map(i -> Map.of("skillName", i.getSkillName(), "gap", i.getGap()))
                    .toList());
            eval.setGapBefore(objectMapper.writeValueAsString(before));
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("totalWeightedGap", afterGap);
            after.put("matchRate", gapNow.getMatchRate());
            after.put("items", gapNow.getItems().stream()
                    .map(i -> Map.of("skillName", i.getSkillName(),
                            "currentLevel", i.getCurrentLevel(), "gap", i.getGap()))
                    .toList());
            eval.setGapAfter(objectMapper.writeValueAsString(after));
        } catch (Exception ignore) {
        }

        eval.setDiagnosis(buildDiagnosis(pre, post, improve, finishRate, reduce, resultLevel, gapNow));
        if ("FAIL".equals(resultLevel) || reduce.compareTo(BigDecimal.valueOf(30)) < 0) {
            eval.setRetrainPlan(buildRetrain(gapNow));
        } else {
            eval.setRetrainPlan(null);
        }
        if (isNew) {
            evalMapper.insert(eval);
        } else {
            evalMapper.updateById(eval);
        }
        return eval;
    }

    /**
     * 把考核成绩回写为技能等级提升（幂等：只升不降，不超过岗位目标等级）
     */
    @Transactional
    public int applyExamToSkill(SysUser user, Long planId, BigDecimal postScore) {
        if (user.getPostId() == null) {
            return 0;
        }
        int delta;
        if (postScore.compareTo(HIGH) >= 0) {
            delta = 2;
        } else if (postScore.compareTo(PASS) >= 0) {
            delta = 1;
        } else {
            return 0;
        }
        // 本计划覆盖的技能点
        List<PlanCourse> pcs = planCourseMapper.selectList(new LambdaQueryWrapper<PlanCourse>()
                .eq(PlanCourse::getPlanId, planId));
        Set<Long> skillIds = new HashSet<>();
        pcs.forEach(pc -> {
            if (pc.getCoverSkillId() != null) {
                skillIds.add(pc.getCoverSkillId());
            }
        });
        if (skillIds.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> targetMap = new HashMap<>();
        postSkillMapper.selectList(new LambdaQueryWrapper<PostSkill>()
                        .eq(PostSkill::getPostId, user.getPostId())
                        .in(PostSkill::getSkillId, skillIds))
                .forEach(ps -> targetMap.put(ps.getSkillId(), ps.getTargetLevel()));

        int updated = 0;
        for (Long skillId : skillIds) {
            EmpSkill es = empSkillMapper.selectOne(new LambdaQueryWrapper<EmpSkill>()
                    .eq(EmpSkill::getUserId, user.getId()).eq(EmpSkill::getSkillId, skillId));
            if (es == null) {
                continue;
            }
            int current = es.getCurrentLevel() == null ? 0 : es.getCurrentLevel();
            int cap = targetMap.getOrDefault(skillId, 5);
            int next = Math.min(cap, current + delta);
            if (next > current) {
                es.setCurrentLevel(next);
                es.setAssessSource("EXAM");
                es.setAssessTime(LocalDateTime.now());
                empSkillMapper.updateById(es);
                updated++;
            }
        }
        return updated;
    }

    /** 培训前加权差距：按技能去重后 Σ(gap × weight) */
    private BigDecimal calcPlanGap(Long planId, SysUser user) {
        if (user.getPostId() == null) {
            return BigDecimal.ZERO;
        }
        List<PlanCourse> pcs = planCourseMapper.selectList(new LambdaQueryWrapper<PlanCourse>()
                .eq(PlanCourse::getPlanId, planId));
        Map<Long, Integer> gapBySkill = new LinkedHashMap<>();
        for (PlanCourse pc : pcs) {
            if (pc.getCoverSkillId() != null && pc.getGapBefore() != null) {
                gapBySkill.merge(pc.getCoverSkillId(), pc.getGapBefore(), Math::max);
            }
        }
        if (gapBySkill.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> weightMap = new HashMap<>();
        postSkillMapper.selectList(new LambdaQueryWrapper<PostSkill>()
                        .eq(PostSkill::getPostId, user.getPostId())
                        .in(PostSkill::getSkillId, gapBySkill.keySet()))
                .forEach(ps -> weightMap.put(ps.getSkillId(), ps.getWeight()));
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : gapBySkill.entrySet()) {
            BigDecimal w = weightMap.getOrDefault(e.getKey(), BigDecimal.ONE);
            sum = sum.add(w.multiply(BigDecimal.valueOf(e.getValue())));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildDiagnosis(BigDecimal pre, BigDecimal post, BigDecimal improve,
                                  BigDecimal finishRate, BigDecimal reduce, String level,
                                  GapResultVO gap) {
        StringBuilder sb = new StringBuilder();
        if (pre != null) {
            sb.append(String.format("培训前测 %.1f 分，后测 %.1f 分，提升 %.1f 分；", pre, post, improve));
        } else {
            sb.append(String.format("后测成绩 %.1f 分（无前测基准，按绝对水平评估）；", post));
        }
        sb.append(String.format("计划完成率 %.1f%%，技能差距缩小 %.1f%%。", finishRate, reduce));
        switch (level) {
            case "EXCELLENT" -> sb.append("整体培训效果优秀，已达到预期目标。");
            case "PASS" -> sb.append("培训达到及格要求，仍有优化空间。");
            default -> sb.append("培训效果未达标，需要安排补训。");
        }
        List<String> weak = gap.getItems().stream()
                .filter(i -> i.getGap() != null && i.getGap() > 0).limit(3)
                .map(i -> i.getSkillName() + "（还差 " + i.getGap() + " 级）").toList();
        if (!weak.isEmpty()) {
            sb.append("当前主要短板：").append(String.join("、", weak)).append("。");
        } else {
            sb.append("岗位技能要求已全部达标。");
        }
        return sb.toString();
    }

    private String buildRetrain(GapResultVO gap) {
        List<String> items = new ArrayList<>();
        gap.getItems().stream().filter(i -> i.getGap() != null && i.getGap() > 0).limit(3)
                .forEach(i -> items.add(String.format("%s（目标 %d 级，当前 %d 级，差 %d 级）",
                        i.getSkillName(), i.getTargetLevel(), i.getCurrentLevel(), i.getGap())));
        if (items.isEmpty()) {
            return "技能差距已基本消除，建议进入下一轮岗位技能升级培训。";
        }
        return String.format("补训建议：针对%s安排专项补训。方式采用「线上课程 + 现场实操 + 一对一带教」，"
                        + "周期约 2 周；结束后重新组织后测，后测达到 60 分及以上且对应技能等级提升 1 级视为验收通过。",
                String.join("、", items));
    }

    /** 大模型增强版诊断（模型不可用时保留规则结论） */
    @Transactional
    public EffectEval evaluateWithLlm(Long planId, Long userId) {
        EffectEval eval = evaluate(planId, userId);
        if (!llmClient.available() || eval == null) {
            return eval;
        }
        SysUser user = userMapper.selectById(userId);
        TrainPlan plan = planMapper.selectById(planId);
        AiPromptTemplate tpl = promptService.getByScene("EFFECT_DIAG");
        Map<String, Object> vars = new HashMap<>();
        vars.put("realName", user == null ? "" : user.getRealName());
        vars.put("postName", "");
        vars.put("planName", plan == null ? "" : plan.getPlanName());
        vars.put("preScore", eval.getPreScore());
        vars.put("postScore", eval.getPostScore());
        vars.put("improve", eval.getScoreImprove());
        vars.put("finishRate", eval.getFinishRate());
        vars.put("gapBefore", eval.getGapBefore());
        vars.put("gapAfter", eval.getGapAfter());
        vars.put("gapReduce", eval.getGapReduce());
        String prompt = tpl == null ? eval.getDiagnosis() : PromptService.render(tpl.getContent(), vars);

        LlmResponse resp = llmClient.chat("你是培训效果评估专家，只输出 JSON。", prompt, true);
        if (resp.isSuccess()) {
            try {
                JsonNode node = objectMapper.readTree(cleanJson(resp.getContent()));
                StringBuilder sb = new StringBuilder(node.path("diagnosis").asText(eval.getDiagnosis()));
                JsonNode sug = node.path("suggestions");
                if (sug.isArray() && !sug.isEmpty()) {
                    List<String> list = new ArrayList<>();
                    sug.forEach(s -> list.add(s.asText()));
                    sb.append(" 改进建议：").append(String.join("；", list)).append("。");
                }
                eval.setDiagnosis(sb.toString());
                evalMapper.updateById(eval);
                saveAiRecord(planId, userId, resp, "EFFECT_DIAG");
            } catch (Exception e) {
                log.warn("效果诊断结果解析失败：{}", e.getMessage());
            }
        } else {
            saveAiRecord(planId, userId, resp, "EFFECT_DIAG");
        }
        return eval;
    }

    private void saveAiRecord(Long planId, Long userId, LlmResponse resp, String bizType) {
        AiGenRecord rec = new AiGenRecord();
        rec.setBizType(bizType);
        rec.setBizId(planId);
        rec.setUserId(userId);
        rec.setOutputContent(resp.getContent());
        rec.setModel(resp.getModel());
        rec.setPromptTokens(resp.getPromptTokens());
        rec.setCompletionTokens(resp.getCompletionTokens());
        rec.setCostMs(resp.getCostMs());
        rec.setAttemptCount(Math.max(1, resp.getAttempts()));
        rec.setStatus(resp.isSuccess() ? "SUCCESS" : "API_ERROR");
        rec.setAdoptStatus("PENDING");
        genRecordMapper.insert(rec);
    }

    private String cleanJson(String s) {
        if (s == null) {
            return "{}";
        }
        String t = s.trim();
        int l = t.indexOf('{');
        int r = t.lastIndexOf('}');
        if (l >= 0 && r > l) {
            t = t.substring(l, r + 1);
        }
        return t;
    }

    public List<Map<String, Object>> listByPlan(Long planId) {
        List<EffectEval> list = evalMapper.selectList(new LambdaQueryWrapper<EffectEval>()
                .eq(EffectEval::getPlanId, planId).orderByDesc(EffectEval::getId));
        Map<Long, String> names = new HashMap<>();
        if (!list.isEmpty()) {
            userMapper.selectBatchIds(list.stream().map(EffectEval::getUserId).toList())
                    .forEach(u -> names.put(u.getId(), u.getRealName()));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (EffectEval e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("planId", e.getPlanId());
            m.put("userId", e.getUserId());
            m.put("realName", names.get(e.getUserId()));
            m.put("preScore", e.getPreScore());
            m.put("postScore", e.getPostScore());
            m.put("scoreImprove", e.getScoreImprove());
            m.put("gapReduce", e.getGapReduce());
            m.put("finishRate", e.getFinishRate());
            m.put("evalResult", e.getEvalResult());
            m.put("diagnosis", e.getDiagnosis());
            m.put("retrainPlan", e.getRetrainPlan());
            m.put("createTime", e.getCreateTime());
            out.add(m);
        }
        return out;
    }

    public EffectEval detail(Long planId, Long userId) {
        return evalMapper.selectOne(new LambdaQueryWrapper<EffectEval>()
                .eq(EffectEval::getPlanId, planId).eq(EffectEval::getUserId, userId));
    }
}
