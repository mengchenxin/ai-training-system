package com.aitraining.module.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aitraining.common.BizException;
import com.aitraining.common.PageResult;
import com.aitraining.common.Result;
import com.aitraining.module.skill.dto.GapResultVO;
import com.aitraining.module.skill.entity.EmpSkill;
import com.aitraining.module.skill.entity.PostSkill;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.EmpSkillMapper;
import com.aitraining.module.skill.mapper.PostSkillMapper;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "04-技能模型与差距分析")
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillMapper skillMapper;
    private final PostSkillMapper postSkillMapper;
    private final EmpSkillMapper empSkillMapper;
    private final SkillGapService gapService;

    // ---------- 技能点 ----------

    @Operation(summary = "技能点列表（下拉用）")
    @GetMapping("/list")
    public Result<List<Skill>> list(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            w.eq(Skill::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(Skill::getSkillName, keyword);
        }
        w.orderByAsc(Skill::getId);
        return Result.ok(skillMapper.selectList(w));
    }

    @Operation(summary = "技能点分页")
    @GetMapping("/page")
    public Result<PageResult<Skill>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            w.eq(Skill::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Skill::getSkillName, keyword).or().like(Skill::getSkillCode, keyword));
        }
        w.orderByAsc(Skill::getId);
        Page<Skill> p = skillMapper.selectPage(new Page<>(current, size), w);
        return Result.ok(PageResult.of(p));
    }

    @Operation(summary = "技能点新增或修改")
    @PostMapping
    public Result<Long> save(@RequestBody Skill skill) {
        LoginHelper.requireManager();
        if (skill.getId() == null) {
            skillMapper.insert(skill);
        } else {
            skillMapper.updateById(skill);
        }
        return Result.ok(skill.getId());
    }

    @Operation(summary = "删除技能点")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        LoginHelper.requireAdmin();
        skillMapper.deleteById(id);
        return Result.ok();
    }

    // ---------- 岗位技能要求 ----------

    @Operation(summary = "查看岗位技能模型")
    @GetMapping("/post/{postId}")
    public Result<List<Map<String, Object>>> postRequirement(@PathVariable Long postId) {
        return Result.ok(postSkillMapper.selectRequirementDetail(postId));
    }

    @Operation(summary = "保存岗位技能模型（整体覆盖）")
    @PostMapping("/post/{postId}")
    public Result<Void> savePostRequirement(@PathVariable Long postId,
                                            @RequestBody List<PostSkill> items) {
        LoginHelper.requireManager();
        postSkillMapper.delete(new LambdaQueryWrapper<PostSkill>().eq(PostSkill::getPostId, postId));
        for (PostSkill ps : items) {
            ps.setId(null);
            ps.setPostId(postId);
            if (ps.getWeight() == null) {
                ps.setWeight(BigDecimal.ONE);
            }
            postSkillMapper.insert(ps);
        }
        return Result.ok();
    }

    // ---------- 员工技能档案 ----------

    @Operation(summary = "查看员工技能档案")
    @GetMapping("/emp/{userId}")
    public Result<List<Map<String, Object>>> empSkill(@PathVariable Long userId) {
        LoginHelper.requireSelfOrManager(userId);
        return Result.ok(empSkillMapper.selectEmpSkillDetail(userId));
    }

    @Operation(summary = "保存员工技能档案（逐条 upsert）")
    @PostMapping("/emp/{userId}")
    public Result<Void> saveEmpSkill(@PathVariable Long userId,
                                     @RequestBody List<EmpSkill> items) {
        if (LoginHelper.isEmployee() && !LoginHelper.userId().equals(userId)) {
            throw new BizException(403, "只能维护自己的技能档案");
        }
        for (EmpSkill es : items) {
            EmpSkill exist = empSkillMapper.selectOne(new LambdaQueryWrapper<EmpSkill>()
                    .eq(EmpSkill::getUserId, userId).eq(EmpSkill::getSkillId, es.getSkillId()));
            es.setUserId(userId);
            es.setAssessTime(LocalDateTime.now());
            es.setDelFlag(null);
            if (exist == null) {
                empSkillMapper.insert(es);
            } else {
                es.setId(exist.getId());
                empSkillMapper.updateById(es);
            }
        }
        return Result.ok();
    }

    // ---------- 差距分析 ----------

    @Operation(summary = "个人技能差距分析")
    @GetMapping("/gap/{userId}")
    public Result<GapResultVO> gap(@PathVariable Long userId) {
        LoginHelper.requireSelfOrManager(userId);
        return Result.ok(gapService.analyze(userId));
    }

    @Operation(summary = "我的技能差距分析")
    @GetMapping("/gap/me")
    public Result<GapResultVO> myGap() {
        return Result.ok(gapService.analyze(LoginHelper.userId()));
    }

    @Operation(summary = "批量差距分析（部门/岗位维度）")
    @GetMapping("/gap/batch")
    public Result<List<GapResultVO>> batchGap(@RequestParam(required = false) Long deptId,
                                              @RequestParam(required = false) Long postId) {
        LoginHelper.requireManager();
        return Result.ok(gapService.batchAnalyze(deptId, postId));
    }

    @Operation(summary = "技能维度热力分析（识别群体性短板）")
    @GetMapping("/gap/heatmap")
    public Result<List<Map<String, Object>>> heatmap(@RequestParam(required = false) Long deptId,
                                                     @RequestParam(required = false) Long postId) {
        LoginHelper.requireManager();
        return Result.ok(gapService.skillHeatmap(deptId, postId));
    }
}
