package com.aitraining.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aitraining.module.system.entity.Post;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
}