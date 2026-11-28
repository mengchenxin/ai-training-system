package com.aitraining.module.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.common.BizException;
import com.aitraining.module.skill.dto.GapItemVO;
import com.aitraining.module.skill.dto.GapResultVO;
import com.aitraining.module.skill.entity.EmpSkill;
import com.aitraining.module.skill.entity.PostSkill;
import com.aitraining.module.skill.entity.Skill;
import com.aitraining.module.skill.mapper.EmpSkillMapper;
import com.aitraining.module.skill.mapper.PostSkillMapper;
import com.aitraining.module.skill.mapper.SkillMapper;
import com.aitraining.module.system.entity.Post;
import com.aitraining.module.system.entity.SysDept;
import com.aitraining.module.system.entity.SysUser;
import com.aitraining.module.system.mapper.PostMapper;
import com.aitraining.module.system.mapper.SysDeptMapper;
import com.aitraining.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 技能差距量化分析。
 *
 * <p>核心模型（论文第 4 章方法设计）：
 * <pre>
 *   gap(i)          = max(0, target(i) - current(i))          等级差距
 *   weightedGap(i)  = gap(i) × weight(i)                       加权差距
 *   priority(i)     = weightedGap(i) / max(weightedGap) × 100  归一化优先级得分
 *   matchRate       = 1 - Σ weightedGap(i) / Σ (target(i)×weight(i))
 * </pre>
 * 排序规则：加权差距降序 → 权重降序 → 技能ID升序（保证结果稳定可复现）。
 */
@Service
@RequiredArgsConstructor
public class SkillGapService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final PostMapper postMapper;
    private final PostSkillMapper postSkillMapper;
    private final EmpSkillMapper empSkillMapper;
    private final SkillMapper skillMapper;

    public GapResultVO analyze(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("员工不存在");
        }
        if (user.getPostId() == null) {
            throw new BizException("该用户尚未分配岗位，无法进行技能差距分析");
        }
        Post post = postMapper.selectById(user.getPostId());
        List<PostSkill> reqs = postSkillMapper.selectList(
                new LambdaQueryWrapper<PostSkill>().eq(PostSkill::getPostId, user.getPostId()));
        if (reqs.isEmpty()) {
            throw new BizException("岗位「" + (post == null ? "" : post.getPostName()) + "」尚未配置技能模型");
        }

        List<Long> skillIds = reqs.stream().map(PostSkill::getSkillId).toList();
        Map<Long, Skill> skillMap = new HashMap<>();
        skillMapper.selectBatchIds(skillIds).forEach(s -> skillMap.put(s.getId(), s));

        Map<Long, EmpSkill> empMap = new HashMap<>();
        empSkillMapper.selectList(new LambdaQueryWrapper<EmpSkill>()
                        .eq(EmpSkill::getUserId, userId)
                        .in(EmpSkill::getSkillId, skillIds))
                .forEach(es -> empMap.put(es.getSkillId(), es));

        List<GapItemVO> items = new ArrayList<>();
        BigDecimal totalWeightedGap = BigDecimal.ZERO;
        BigDecimal totalWeightedTarget = BigDecimal.ZERO;
        BigDecimal maxWeightedGap = BigDecimal.ZERO;

        for (PostSkill req : reqs) {
            Skill skill = skillMap.get(req.getSkillId());
            if (skill == null) {
                continue;
            }
            EmpSkill es = empMap.get(req.getSkillId());
            int current = es == null ? 0 : Math.max(0, es.getCurrentLevel());
            int target = req.getTargetLevel();
            int gap = Math.max(0, target - current);
            BigDecimal weight = req.getWeight() == null ? BigDecimal.ONE : req.getWeight();
            BigDecimal weightedGap = weight.multiply(BigDecimal.valueOf(gap));

            GapItemVO item = new GapItemVO();
            item.setSkillId(skill.getId());
            item.setSkillName(skill.getSkillName());
            item.setCategory(skill.getCategory());
            item.setTargetLevel(target);
            item.setCurrentLevel(current);
            item.setGap(gap);
            item.setWeight(weight);
            item.setWeightedGap(weightedGap.setScale(2, RoundingMode.HALF_UP));
            item.setAssessed(es != null);
            items.add(item);

            totalWeightedGap = totalWeightedGap.add(weightedGap);
            totalWeightedTarget = totalWeightedTarget.add(weight.multiply(BigDecimal.valueOf(target)));
            if (weightedGap.compareTo(maxWeightedGap) > 0) {
                maxWeightedGap = weightedGap;
            }
        }

        // 排序：加权差距降序 → 权重降序 → 技能ID升序
        items.sort(Comparator
                .comparing(GapItemVO::getWeightedGap, Comparator.reverseOrder())
                .thenComparing(GapItemVO::getWeight, Comparator.reverseOrder())
                .thenComparing(GapItemVO::getSkillId));

        int rank = 0;
        for (GapItemVO it : items) {
            if (it.getGap() > 0) {
                rank++;
                it.setPriorityRank(rank);
                BigDecimal score = maxWeightedGap.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : it.getWeightedGap().multiply(BigDecimal.valueOf(100))
                        .divide(maxWeightedGap, 2, RoundingMode.HALF_UP);
                it.setPriorityScore(score);
                it.setPriorityLevel(score.compareTo(BigDecimal.valueOf(60)) >= 0 ? "高"
                        : score.compareTo(BigDecimal.valueOf(30)) >= 0 ? "中" : "低");
            } else {
                it.setPriorityRank(null);
                it.setPriorityScore(BigDecimal.ZERO);
                it.setPriorityLevel("已达标");
            }
        }

        GapResultVO vo = new GapResultVO();
        vo.setUserId(userId);
        vo.setRealName(user.getRealName());
        vo.setPostName(post == null ? null : post.getPostName());
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getDeptName());
        }
        vo.setTotalWeightedGap(totalWeightedGap.setScale(2, RoundingMode.HALF_UP));
        vo.setTotalWeightedTarget(totalWeightedTarget.setScale(2, RoundingMode.HALF_UP));
        BigDecimal matchRate = totalWeightedTarget.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.valueOf(100)
                : BigDecimal.ONE.subtract(totalWeightedGap.divide(totalWeightedTarget, 4, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        vo.setMatchRate(matchRate.max(BigDecimal.ZERO));
        vo.setGapCount((int) items.stream().filter(i -> i.getGap() > 0).count());
        vo.setItems(items);

        vo.setRadarIndicators(items.stream().map(GapItemVO::getSkillName).toList());
        vo.setRadarCurrent(items.stream().map(GapItemVO::getCurrentLevel).toList());
        vo.setRadarTarget(items.stream().map(GapItemVO::getTargetLevel).toList());
        vo.setGapSummary(buildSummary(vo));
        return vo;
    }

    /**
     * 生成文字化差距摘要，供 Prompt 拼接（结构稳定，便于模型理解）
     */
    public String buildSummary(GapResultVO vo) {
        StringBuilder sb = new StringBuilder();
        for (GapItemVO it : vo.getItems()) {
            if (it.getGap() == null || it.getGap() <= 0) {
                continue;
            }
            sb.append(String.format("技能：%s（%s类）｜目标 %d 级，当前 %d 级，差距 %d 级｜权重 %.2f｜加权差距 %.2f｜优先级 %s（第 %d 位）%n",
                    it.getSkillName(), it.getCategory(), it.getTargetLevel(), it.getCurrentLevel(),
                    it.getGap(), it.getWeight().doubleValue(), it.getWeightedGap().doubleValue(),
                    it.getPriorityLevel(), it.getPriorityRank()));
        }
        return sb.toString();
    }

    /**
     * 批量分析（部门 / 岗位维度），只返回有差距的员工，按差距降序
     */
    public List<GapResultVO> batchAnalyze(Long deptId, Long postId) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.eq(SysUser::getStatus, 1).isNotNull(SysUser::getPostId);
        if (deptId != null) {
            w.eq(SysUser::getDeptId, deptId);
        }
        if (postId != null) {
            w.eq(SysUser::getPostId, postId);
        }
        List<SysUser> users = userMapper.selectList(w);
        List<GapResultVO> list = new ArrayList<>();
        for (SysUser u : users) {
            try {
                list.add(analyze(u.getId()));
            } catch (BizException ignore) {
                // 未配置技能模型的岗位跳过
            }
        }
        list.sort(Comparator.comparing(GapResultVO::getTotalWeightedGap).reversed());
        return list;
    }

    /**
     * 技能维度热力分析：某部门/岗位下所有员工在各技能上的平均差距，
     * 用于识别「群体性短板」（支撑群体化培训计划）。
     */
    public List<Map<String, Object>> skillHeatmap(Long deptId, Long postId) {
        // 批量取数：固定 4 次查询，替代 batchAnalyze 里「每个员工 5 次查询」的 N+1
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.eq(SysUser::getStatus, 1).isNotNull(SysUser::getPostId);
        if (deptId != null) {
            w.eq(SysUser::getDeptId, deptId);
        }
        if (postId != null) {
            w.eq(SysUser::getPostId, postId);
        }
        List<SysUser> users = userMapper.selectList(w);
        if (users.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> postIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (SysUser u : users) {
            postIds.add(u.getPostId());
            userIds.add(u.getId());
        }
        List<PostSkill> allReqs = postSkillMapper.selectList(
                new LambdaQueryWrapper<PostSkill>().in(PostSkill::getPostId, postIds));
        Map<Long, List<PostSkill>> reqByPost = new HashMap<>();
        Set<Long> skillIds = new HashSet<>();
        for (PostSkill r : allReqs) {
            reqByPost.computeIfAbsent(r.getPostId(), k -> new ArrayList<>()).add(r);
            skillIds.add(r.getSkillId());
        }
        Map<Long, Skill> skillMap = new HashMap<>();
        if (!skillIds.isEmpty()) {
            skillMapper.selectBatchIds(skillIds).forEach(s -> skillMap.put(s.getId(), s));
        }
        Map<String, EmpSkill> empMap = new HashMap<>();
        if (!skillIds.isEmpty()) {
            empSkillMapper.selectList(new LambdaQueryWrapper<EmpSkill>()
                            .in(EmpSkill::getUserId, userIds)
                            .in(EmpSkill::getSkillId, skillIds))
                    .forEach(es -> empMap.put(es.getUserId() + ":" + es.getSkillId(), es));
        }

        // 与 analyze() 口径一致：跳过未配置技能模型的岗位，员工按加权差距降序（保持技能出现顺序稳定）
        List<GapResultVO> list = new ArrayList<>();
        for (SysUser u : users) {
            List<PostSkill> reqs = reqByPost.get(u.getPostId());
            if (reqs == null || reqs.isEmpty()) {
                continue;
            }
            List<GapItemVO> items = new ArrayList<>();
            BigDecimal totalWeightedGap = BigDecimal.ZERO;
            for (PostSkill req : reqs) {
                Skill skill = skillMap.get(req.getSkillId());
                if (skill == null) {
                    continue;
                }
                EmpSkill es = empMap.get(u.getId() + ":" + req.getSkillId());
                int current = es == null ? 0 : Math.max(0, es.getCurrentLevel());
                int target = req.getTargetLevel();
                int gap = Math.max(0, target - current);
                BigDecimal weight = req.getWeight() == null ? BigDecimal.ONE : req.getWeight();
                GapItemVO item = new GapItemVO();
                item.setSkillId(skill.getId());
                item.setSkillName(skill.getSkillName());
                item.setCategory(skill.getCategory());
                item.setTargetLevel(target);
                item.setCurrentLevel(current);
                item.setGap(gap);
                item.setWeight(weight);
                item.setWeightedGap(weight.multiply(BigDecimal.valueOf(gap)));
                items.add(item);
                totalWeightedGap = totalWeightedGap.add(item.getWeightedGap());
            }
            GapResultVO vo = new GapResultVO();
            vo.setTotalWeightedGap(totalWeightedGap.setScale(2, RoundingMode.HALF_UP));
            vo.setItems(items);
            list.add(vo);
        }
        list.sort(Comparator.comparing(GapResultVO::getTotalWeightedGap).reversed());

        Map<Long, List<GapItemVO>> bySkill = new LinkedHashMap<>();
        for (GapResultVO vo : list) {
            for (GapItemVO it : vo.getItems()) {
                bySkill.computeIfAbsent(it.getSkillId(), k -> new ArrayList<>()).add(it);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<GapItemVO>> e : bySkill.entrySet()) {
            List<GapItemVO> items = e.getValue();
            double avgGap = items.stream().mapToInt(GapItemVO::getGap).average().orElse(0);
            long below = items.stream().filter(i -> i.getGap() > 0).count();
            Skill s = skillMap.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skillId", e.getKey());
            m.put("skillName", s == null ? "" : s.getSkillName());
            m.put("category", s == null ? "" : s.getCategory());
            m.put("avgGap", BigDecimal.valueOf(avgGap).setScale(2, RoundingMode.HALF_UP));
            m.put("belowCount", below);
            m.put("totalCount", items.size());
            m.put("belowRate", BigDecimal.valueOf(below * 100.0 / items.size()).setScale(1, RoundingMode.HALF_UP));
            out.add(m);
        }
        out.sort(Comparator
                .comparing((Map<String, Object> m) -> (BigDecimal) m.get("avgGap")).reversed()
                .thenComparing(m -> (Long) m.get("skillId")));
        return out;
    }
}
