package com.aitraining.module.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aitraining.module.skill.entity.EmpSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EmpSkillMapper extends BaseMapper<EmpSkill> {

    @Select("""
            SELECT es.skill_id AS skillId, es.current_level AS currentLevel,
                   es.assess_source AS assessSource, es.assess_time AS assessTime,
                   s.skill_code AS skillCode, s.skill_name AS skillName, s.category,
                   ps.target_level AS targetLevel, ps.weight
            FROM emp_skill es
            JOIN skill s ON s.id = es.skill_id AND s.del_flag = 0
            JOIN sys_user u ON u.id = es.user_id
            LEFT JOIN post_skill ps ON ps.post_id = u.post_id AND ps.skill_id = es.skill_id AND ps.del_flag = 0
            WHERE es.user_id = #{userId} AND es.del_flag = 0
            ORDER BY es.skill_id
            """)
    List<Map<String, Object>> selectEmpSkillDetail(@Param("userId") Long userId);
}
