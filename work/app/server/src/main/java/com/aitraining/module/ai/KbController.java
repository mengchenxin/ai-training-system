package com.aitraining.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aitraining.common.BizException;
import com.aitraining.common.Result;
import com.aitraining.module.ai.entity.KbChunk;
import com.aitraining.module.ai.entity.KbDoc;
import com.aitraining.module.ai.mapper.KbChunkMapper;
import com.aitraining.module.ai.mapper.KbDocMapper;
import com.aitraining.module.ai.rag.RagService;
import com.aitraining.security.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理：文档与片段的维护，索引重建
 */
@Tag(name = "11-知识库")
@RestController
@RequestMapping("/ai/kb")
@RequiredArgsConstructor
public class KbController {

    private final KbDocMapper docMapper;
    private final KbChunkMapper chunkMapper;
    private final RagService ragService;

    @Operation(summary = "文档列表")
    @GetMapping("/doc/list")
    public Result<List<Map<String, Object>>> docList(@RequestParam(required = false) String docType) {
        LoginHelper.requireManager();
        LambdaQueryWrapper<KbDoc> w = new LambdaQueryWrapper<>();
        if (docType != null && !docType.isBlank()) {
            w.eq(KbDoc::getDocType, docType);
        }
        w.orderByAsc(KbDoc::getId);
        List<KbDoc> docs = docMapper.selectList(w);
        List<Map<String, Object>> out = new ArrayList<>();
        for (KbDoc d : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("docName", d.getDocName());
            m.put("docType", d.getDocType());
            m.put("source", d.getSource());
            m.put("chunkCount", d.getChunkCount());
            m.put("status", d.getStatus());
            m.put("createTime", d.getCreateTime());
            out.add(m);
        }
        return Result.ok(out);
    }

    @Operation(summary = "文档片段")
    @GetMapping("/doc/{id}/chunks")
    public Result<List<KbChunk>> chunks(@PathVariable Long id) {
        LoginHelper.requireManager();
        return Result.ok(chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, id).orderByAsc(KbChunk::getChunkIndex)));
    }

    @Operation(summary = "新增文档（自动按段落切分并建索引）")
    @PostMapping("/doc")
    public Result<Long> addDoc(@RequestBody Map<String, String> body) {
        LoginHelper.requireManager();
        String name = body.get("docName");
        String content = body.get("content");
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            throw new BizException("文档名称与正文不能为空");
        }
        KbDoc doc = new KbDoc();
        doc.setDocName(name);
        doc.setDocType(body.getOrDefault("docType", "OTHER"));
        doc.setSource(body.getOrDefault("source", "手动录入"));
        doc.setStatus("READY");
        docMapper.insert(doc);

        List<String> parts = split(content, 300);
        int idx = 0;
        for (String part : parts) {
            KbChunk c = new KbChunk();
            c.setDocId(doc.getId());
            c.setChunkIndex(idx++);
            c.setContent(part);
            c.setTokenCount(part.length());
            chunkMapper.insert(c);
        }
        doc.setChunkCount(parts.size());
        docMapper.updateById(doc);
        ragService.reload();
        return Result.ok(doc.getId());
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/doc/{id}")
    public Result<Void> removeDoc(@PathVariable Long id) {
        LoginHelper.requireManager();
        docMapper.deleteById(id);
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, id));
        ragService.reload();
        return Result.ok();
    }

    @Operation(summary = "重建检索索引")
    @PostMapping("/reload")
    public Result<Integer> reload() {
        LoginHelper.requireManager();
        return Result.ok(ragService.reload());
    }

    /** 按句子边界切分，避免把一句话截断 */
    private List<String> split(String text, int maxLen) {
        List<String> out = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").trim();
        for (String para : normalized.split("\n+")) {
            if (para.isBlank()) {
                continue;
            }
            if (para.length() <= maxLen) {
                out.add(para.trim());
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (String sentence : para.split("(?<=[。！？；])")) {
                if (sb.length() + sentence.length() > maxLen && sb.length() > 0) {
                    out.add(sb.toString().trim());
                    sb.setLength(0);
                }
                sb.append(sentence);
            }
            if (sb.length() > 0) {
                out.add(sb.toString().trim());
            }
        }
        return out;
    }
}
