package com.aitraining.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.module.ai.entity.AiPromptTemplate;
import com.aitraining.module.ai.mapper.AiPromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板管理：模板存库、变量渲染、版本化。
 * 相比把提示词写死在代码里，这里的模板可由讲师在后台调整并保留版本，
 * 便于做 Prompt 版本对比实验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

    private final AiPromptTemplateMapper templateMapper;

    /** 场景 -> 模板缓存（取启用状态下的最高版本） */
    private final Map<String, AiPromptTemplate> cache = new HashMap<>();

    public AiPromptTemplate getByScene(String scene) {
        AiPromptTemplate t = cache.get(scene);
        if (t != null) {
            return t;
        }
        t = templateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getScene, scene)
                .eq(AiPromptTemplate::getStatus, 1)
                .orderByDesc(AiPromptTemplate::getVersion)
                .last("limit 1"));
        if (t != null) {
            cache.put(scene, t);
        }
        return t;
    }

    public AiPromptTemplate getByCode(String code) {
        return templateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getTemplateCode, code)
                .eq(AiPromptTemplate::getStatus, 1)
                .orderByDesc(AiPromptTemplate::getVersion)
                .last("limit 1"));
    }

    public void clearCache() {
        cache.clear();
    }

    /** 渲染模板，未提供的变量替换为空串 */
    public static String render(String template, Map<String, Object> vars) {
        if (template == null) {
            return "";
        }
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object v = vars.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
