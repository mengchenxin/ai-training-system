package com.aitraining.module.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.exam.entity.Question;
import com.aitraining.module.exam.mapper.QuestionMapper;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "08-题库管理")
@RestController
@RequestMapping("/exam/question")
@RequiredArgsConstructor
@Slf4j
public class QuestionController {

    private final QuestionMapper questionMapper;
    private final SkillMapper skillMapper;

    @Operation(summary = "题目分页")
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) Long skillId,
                                                        @RequestParam(required = false) String questionType,
                                                        @RequestParam(required = false) Integer difficulty,
                                                        @RequestParam(required = false) String keyword) {
        LoginHelper.requireManager();
        LambdaQueryWrapper<Question> w = new LambdaQueryWrapper<>();
        if (skillId != null) {
            w.eq(Question::getSkillId, skillId);
        }
        if (StringUtils.hasText(questionType)) {
            w.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null) {
            w.eq(Question::getDifficulty, difficulty);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(Question::getContent, keyword);
        }
        w.orderByDesc(Question::getId);
        Page<Question> p = questionMapper.selectPage(new Page<>(current, size), w);
        Map<Long, String> skillNames = skillNameMap(p.getRecords().stream()
                .map(Question::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Map<String, Object>> rows = p.getRecords().stream().map(q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("skillId", q.getSkillId());
            m.put("skillName", skillNames.get(q.getSkillId()));
            m.put("knowledgePoint", q.getKnowledgePoint());
            m.put("questionType", q.getQuestionType());
            m.put("content", q.getContent());
            m.put("options", q.getOptions());
            m.put("answer", q.getAnswer());
            m.put("analysis", q.getAnalysis());
            m.put("score", q.getScore());
            m.put("difficulty", q.getDifficulty());
            m.put("genType", q.getGenType());
            m.put("status", q.getStatus());
            return m;
        }).toList();
        return Result.ok(PageResult.of(rows, p.getTotal(), current, size));
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{id}")
    public Result<Question> detail(@PathVariable Long id) {
        LoginHelper.requireManager();
        return Result.ok(questionMapper.selectById(id));
    }

    @Operation(summary = "新增或修改题目")
    @PostMapping
    public Result<Long> save(@RequestBody Question q) {
        LoginHelper.requireManager();
        if (q.getId() == null) {
            questionMapper.insert(q);
        } else {
            questionMapper.updateById(q);
        }
        return Result.ok(q.getId());
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        LoginHelper.requireManager();
        questionMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "按技能统计题量（组卷时展示可用题量）")
    @GetMapping("/stat")
    public Result<List<Map<String, Object>>> stat() {
        LoginHelper.requireManager();
        List<Question> all = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 1));
        Map<Long, String> skillNames = skillNameMap(all.stream()
                .map(Question::getSkillId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, List<Question>> bySkill = all.stream()
                .filter(q -> q.getSkillId() != null)
                .collect(Collectors.groupingBy(Question::getSkillId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<Question>> e : bySkill.entrySet()) {
            List<Question> list = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skillId", e.getKey());
            m.put("skillName", skillNames.get(e.getKey()));
            m.put("total", list.size());
            m.put("easy", list.stream().filter(q -> q.getDifficulty() == 1).count());
            m.put("medium", list.stream().filter(q -> q.getDifficulty() == 2).count());
            m.put("hard", list.stream().filter(q -> q.getDifficulty() == 3).count());
            out.add(m);
        }
        out.sort((a, b) -> ((String) a.get("skillName")).compareTo((String) b.get("skillName")));
        return Result.ok(out);
    }

    private Map<Long, String> skillNameMap(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getSkillName));
    }
}
