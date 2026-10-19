package com.aitraining.module.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.module.ai.AiJudgeService;
import com.aitraining.module.exam.dto.PaperGenForm;
import com.aitraining.module.exam.dto.SubmitForm;
import com.aitraining.module.exam.entity.*;
import com.aitraining.module.exam.mapper.*;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.security.LoginHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 考核：自动组卷 → 在线作答 → 客观题自动判分 → 主观题 AI 辅助评分 → 成绩归档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final QuestionMapper questionMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamAnswerMapper answerMapper;
    private final AnswerSheetMapper sheetMapper;
    private final SkillMapper skillMapper;
    private final AiJudgeService judgeService;
    private final com.aitraining.module.system.mapper.SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    // ---------------- 组卷 ----------------

    @Transactional
    public Long autoGenerate(PaperGenForm form) {
        LoginHelper.requireManager();
        if (form.getSkillIds() == null || form.getSkillIds().isEmpty()) {
            throw new BizException("请选择组卷的技能范围");
        }
        int easy = nz(form.getEasyCount());
        int medium = nz(form.getMediumCount());
        int hard = nz(form.getHardCount());
        int want = easy + medium + hard;
        if (want <= 0) {
            throw new BizException("请设置题目数量");
        }

        List<Question> pool = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getSkillId, form.getSkillIds())
                .eq(Question::getStatus, 1));
        if (!Boolean.FALSE.equals(form.getIncludeShort())) {
            // 简答题单独占 1 题（若有）
        } else {
            pool = pool.stream().filter(q -> !"SHORT".equals(q.getQuestionType())).toList();
        }
        if (pool.isEmpty()) {
            throw new BizException("所选技能范围内没有可用题目");
        }

        Random random = new Random();
        List<Question> picked = new ArrayList<>();
        picked.addAll(pickByDifficulty(pool, 1, easy, random));
        picked.addAll(pickByDifficulty(pool, 2, medium, random));
        picked.addAll(pickByDifficulty(pool, 3, hard, random));
        if (picked.isEmpty()) {
            throw new BizException("可用题目不足，请调整难度分布或先补充题库");
        }

        // 分值分配：总分均分，尾差补到最后一题
        BigDecimal total = form.getTotalScore() == null ? BigDecimal.valueOf(100) : form.getTotalScore();
        BigDecimal each = total.divide(BigDecimal.valueOf(picked.size()), 1, RoundingMode.DOWN);

        ExamPaper paper = new ExamPaper();
        paper.setPaperName(Optional.ofNullable(form.getPaperName()).filter(s -> !s.isBlank())
                .orElse("自动组卷·" + LocalDateTime.now().toLocalDate()));
        paper.setPlanId(form.getPlanId());
        paper.setTotalScore(total);
        paper.setPassScore(form.getPassScore() == null ? BigDecimal.valueOf(60) : form.getPassScore());
        paper.setGenType("AI_BY_SKILL");
        try {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("skillIds", form.getSkillIds());
            rule.put("easyCount", easy);
            rule.put("mediumCount", medium);
            rule.put("hardCount", hard);
            paper.setRuleJson(objectMapper.writeValueAsString(rule));
        } catch (Exception ignore) {
        }
        paperMapper.insert(paper);

        BigDecimal assigned = BigDecimal.ZERO;
        int seq = 1;
        for (Question q : picked) {
            ExamPaperQuestion pq = new ExamPaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(q.getId());
            pq.setSeqNo(seq++);
            BigDecimal score = (seq == picked.size() + 1)
                    ? total.subtract(assigned) : each;
            if (score.compareTo(BigDecimal.ZERO) <= 0) {
                score = BigDecimal.ONE;
            }
            pq.setScore(score);
            assigned = assigned.add(score);
            paperQuestionMapper.insert(pq);
        }
        return paper.getId();
    }

    private List<Question> pickByDifficulty(List<Question> pool, int difficulty, int count, Random random) {
        if (count <= 0) {
            return List.of();
        }
        List<Question> candidates = new ArrayList<>(pool.stream()
                .filter(q -> q.getDifficulty() != null && q.getDifficulty() == difficulty)
                .toList());
        Collections.shuffle(candidates, random);
        return candidates.stream().limit(count).toList();
    }

    private int nz(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    private Map<Long, String> skillNames(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        skillMapper.selectBatchIds(ids).forEach(s -> map.put(s.getId(), s.getSkillName()));
        return map;
    }

    // ---------------- 试卷 ----------------

    public PageResult<Map<String, Object>> paperPage(long current, long size, String keyword, Long planId) {
        LoginHelper.requireManager();
        LambdaQueryWrapper<ExamPaper> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(ExamPaper::getPaperName, keyword);
        }
        if (planId != null) {
            w.eq(ExamPaper::getPlanId, planId);
        }
        w.orderByDesc(ExamPaper::getId);
        Page<ExamPaper> p = paperMapper.selectPage(new Page<>(current, size), w);
        List<Map<String, Object>> rows = p.getRecords().stream().map(paper -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", paper.getId());
            m.put("paperName", paper.getPaperName());
            m.put("planId", paper.getPlanId());
            m.put("totalScore", paper.getTotalScore());
            m.put("passScore", paper.getPassScore());
            m.put("genType", paper.getGenType());
            m.put("createTime", paper.getCreateTime());
            m.put("questionCount", paperQuestionMapper.selectCount(
                    new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paper.getId())));
            return m;
        }).toList();
        return PageResult.of(rows, p.getTotal(), current, size);
    }

    /** 考生取卷：不返回答案与解析 */
    public Map<String, Object> paperForExam(Long paperId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        List<ExamPaperQuestion> pqs = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paperId)
                        .orderByAsc(ExamPaperQuestion::getSeqNo));
        Map<Long, Question> qm = pqs.isEmpty() ? Map.of() : questionMapper.selectBatchIds(
                        pqs.stream().map(ExamPaperQuestion::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, String> skillNames = skillNames(qm.values().stream()
                .map(Question::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<Map<String, Object>> items = new ArrayList<>();
        for (ExamPaperQuestion pq : pqs) {
            Question q = qm.get(pq.getQuestionId());
            if (q == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("questionId", q.getId());
            m.put("seqNo", pq.getSeqNo());
            m.put("score", pq.getScore());
            m.put("questionType", q.getQuestionType());
            m.put("content", q.getContent());
            m.put("options", q.getOptions());
            m.put("difficulty", q.getDifficulty());
            m.put("skillName", skillNames.get(q.getSkillId()));
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paperId", paper.getId());
        out.put("paperName", paper.getPaperName());
        out.put("totalScore", paper.getTotalScore());
        out.put("passScore", paper.getPassScore());
        out.put("questions", items);
        AnswerSheet sheet = sheetMapper.selectOne(new LambdaQueryWrapper<AnswerSheet>()
                .eq(AnswerSheet::getPaperId, paperId).eq(AnswerSheet::getUserId, LoginHelper.userId()));
        out.put("sheetStatus", sheet == null ? "NOT_STARTED" : sheet.getStatus());
        return out;
    }

    public void startExam(Long paperId) {
        Long uid = LoginHelper.userId();
        AnswerSheet exist = sheetMapper.selectOne(new LambdaQueryWrapper<AnswerSheet>()
                .eq(AnswerSheet::getPaperId, paperId).eq(AnswerSheet::getUserId, uid));
        if (exist != null) {
            if ("SUBMITTED".equals(exist.getStatus())) {
                throw new BizException("该试卷已提交，不能重复作答");
            }
            return;
        }
        AnswerSheet sheet = new AnswerSheet();
        sheet.setPaperId(paperId);
        sheet.setUserId(uid);
        sheet.setStatus("ONGOING");
        sheet.setStartTime(LocalDateTime.now());
        sheetMapper.insert(sheet);
    }

    // ---------------- 判分 ----------------

    @Transactional
    public Map<String, Object> submit(Long paperId, SubmitForm form) {
        Long uid = LoginHelper.userId();
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BizException("试卷不存在");
        }
        List<ExamPaperQuestion> pqs = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paperId));
        if (pqs.isEmpty()) {
            throw new BizException("试卷没有题目");
        }
        Map<Long, ExamPaperQuestion> scoreMap = pqs.stream()
                .collect(Collectors.toMap(ExamPaperQuestion::getQuestionId, x -> x));
        Map<Long, Question> questions = questionMapper.selectBatchIds(scoreMap.keySet()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        Map<Long, String> answers = new HashMap<>();
        if (form.getAnswers() != null) {
            form.getAnswers().forEach(a -> answers.put(a.getQuestionId(),
                    a.getUserAnswer() == null ? "" : a.getUserAnswer().trim()));
        }

        ExamRecord record = new ExamRecord();
        record.setPaperId(paperId);
        record.setUserId(uid);
        record.setPlanId(form.getPlanId() != null ? form.getPlanId() : paper.getPlanId());
        record.setExamStage(form.getExamStage() == null ? "POST" : form.getExamStage());
        record.setSubmitTime(LocalDateTime.now());
        recordMapper.insert(record);

        BigDecimal objective = BigDecimal.ZERO;
        BigDecimal subjective = BigDecimal.ZERO;
        int correctCount = 0;
        List<String> aiReasons = new ArrayList<>();

        for (ExamPaperQuestion pq : pqs) {
            Question q = questions.get(pq.getQuestionId());
            if (q == null) {
                continue;
            }
            String ua = answers.getOrDefault(q.getId(), "");
            ExamAnswer ea = new ExamAnswer();
            ea.setRecordId(record.getId());
            ea.setQuestionId(q.getId());
            ea.setUserAnswer(ua);

            if ("SHORT".equals(q.getQuestionType())) {
                // 满分必须用卷面分配的分值，而非题库默认分值
                AiJudgeService.JudgeResult jr = judgeService.judge(q, ua, pq.getScore());
                ea.setIsCorrect(null);
                ea.setAiScore(jr.score);
                ea.setScore(jr.score);
                ea.setAiReason(jr.reason);
                subjective = subjective.add(jr.score == null ? BigDecimal.ZERO : jr.score);
                // 大模型评分与规则评分都记录理由，便于学员与教师复核
                if (jr.reason != null && !jr.reason.isBlank()) {
                    aiReasons.add("第" + pq.getSeqNo() + "题（简答）：" + jr.reason);
                }
            } else {
                boolean ok = isCorrect(q, ua);
                ea.setIsCorrect(ok ? 1 : 0);
                BigDecimal got = ok ? pq.getScore() : BigDecimal.ZERO;
                ea.setScore(got);
                objective = objective.add(got);
                if (ok) {
                    correctCount++;
                }
            }
            answerMapper.insert(ea);
        }

        BigDecimal total = objective.add(subjective).setScale(1, RoundingMode.HALF_UP);
        record.setObjectiveScore(objective);
        record.setSubjectiveScore(subjective);
        record.setTotalScore(total);
        record.setPassed(total.compareTo(paper.getPassScore()) >= 0 ? 1 : 0);
        if (!aiReasons.isEmpty()) {
            record.setAiComment(String.join("\n", aiReasons));
        }
        recordMapper.updateById(record);

        AnswerSheet sheet = sheetMapper.selectOne(new LambdaQueryWrapper<AnswerSheet>()
                .eq(AnswerSheet::getPaperId, paperId).eq(AnswerSheet::getUserId, uid));
        if (sheet == null) {
            sheet = new AnswerSheet();
            sheet.setPaperId(paperId);
            sheet.setUserId(uid);
            sheet.setStartTime(LocalDateTime.now());
        }
        sheet.setStatus("SUBMITTED");
        sheet.setSubmitTime(LocalDateTime.now());
        if (sheet.getId() == null) {
            sheetMapper.insert(sheet);
        } else {
            sheetMapper.updateById(sheet);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recordId", record.getId());
        out.put("objectiveScore", objective);
        out.put("subjectiveScore", subjective);
        out.put("totalScore", total);
        out.put("passScore", paper.getPassScore());
        out.put("passed", total.compareTo(paper.getPassScore()) >= 0);
        out.put("correctCount", correctCount);
        out.put("questionCount", pqs.size());
        out.put("aiComment", record.getAiComment());
        return out;
    }

    /** 客观题判分：忽略大小写与常见分隔符，多选按集合比较 */
    private boolean isCorrect(Question q, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) {
            return false;
        }
        String std = normalize(q.getAnswer());
        String ua = normalize(userAnswer);
        return std.equals(ua);
    }

    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.toUpperCase().replaceAll("[\\s,，、;；|]", "");
        char[] arr = t.toCharArray();
        Arrays.sort(arr);
        return new String(arr);
    }

    // ---------------- 成绩查询 ----------------

    public List<Map<String, Object>> myRecords() {
        Long uid = LoginHelper.userId();
        List<ExamRecord> list = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getUserId, uid).orderByDesc(ExamRecord::getId));
        return toRecordRows(list);
    }

    public PageResult<Map<String, Object>> recordPage(long current, long size, Long planId, Long userId) {
        LoginHelper.requireManager();
        LambdaQueryWrapper<ExamRecord> w = new LambdaQueryWrapper<>();
        if (planId != null) {
            w.eq(ExamRecord::getPlanId, planId);
        }
        if (userId != null) {
            w.eq(ExamRecord::getUserId, userId);
        }
        w.orderByDesc(ExamRecord::getId);
        Page<ExamRecord> p = recordMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(toRecordRows(p.getRecords()), p.getTotal(), current, size);
    }

    private List<Map<String, Object>> toRecordRows(List<ExamRecord> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> paperIds = list.stream().map(ExamRecord::getPaperId).collect(Collectors.toSet());
        Map<Long, String> papers = paperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(ExamPaper::getId, ExamPaper::getPaperName));
        Set<Long> userIds = list.stream().map(ExamRecord::getUserId).collect(Collectors.toSet());
        Map<Long, String> userNames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(com.aitraining.module.system.entity.SysUser::getId,
                        com.aitraining.module.system.entity.SysUser::getRealName));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExamRecord r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("paperId", r.getPaperId());
            m.put("paperName", papers.getOrDefault(r.getPaperId(), ""));
            m.put("userId", r.getUserId());
            m.put("realName", userNames.getOrDefault(r.getUserId(), ""));
            m.put("planId", r.getPlanId());
            m.put("examStage", r.getExamStage());
            m.put("objectiveScore", r.getObjectiveScore());
            m.put("subjectiveScore", r.getSubjectiveScore());
            m.put("totalScore", r.getTotalScore());
            m.put("passed", r.getPassed());
            m.put("submitTime", r.getSubmitTime());
            m.put("aiComment", r.getAiComment());
            out.add(m);
        }
        return out;
    }
}
