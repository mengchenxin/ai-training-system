package com.aitraining.module.train.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aitraining.module.train.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}