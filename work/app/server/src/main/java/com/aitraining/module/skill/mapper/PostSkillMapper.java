package com.aitraining.module.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aitraining.module.skill.entity.PostSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostSkillMapper extends BaseMapper<PostSkill> {

    @Select("""
            SELECT ps.id, ps.post_id AS postId, ps.skill_id AS skillId,
                   ps.target_level AS targetLevel, ps.weight,
                   s.skill_code AS skillCode, s.skill_name AS skillName,
                   s.category, s.max_level AS maxLevel, s.skill_desc AS skillDesc
            FROM post_skill ps
            JOIN skill s ON s.id = ps.skill_id AND s.del_flag = 0
            WHERE ps.post_id = #{postId} AND ps.del_flag = 0
            ORDER BY ps.weight DESC, ps.skill_id
            """)
    List<Map<String, Object>> selectRequirementDetail(@Param("postId") Long postId);
}
