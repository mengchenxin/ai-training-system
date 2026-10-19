package com.aitraining.module.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aitraining.module.exam.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}