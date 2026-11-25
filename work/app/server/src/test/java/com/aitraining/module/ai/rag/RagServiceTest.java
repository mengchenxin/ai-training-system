package com.aitraining.module.ai.rag;

import com.aitraining.module.ai.entity.KbChunk;
import com.aitraining.module.ai.entity.KbDoc;
import com.aitraining.module.ai.llm.AiProperties;
import com.aitraining.module.ai.mapper.KbChunkMapper;
import com.aitraining.module.ai.mapper.KbDocMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BM25 检索单元测试。
 *
 * <p>覆盖两个最容易被追问的点：中文 2-gram 切分是否正确、
 * 打分排序是否真的把相关片段排在前面（而不是靠插入顺序）。
 */
class RagServiceTest {

    private KbChunkMapper chunkMapper;
    private KbDocMapper docMapper;
    private AiProperties props;
    private RagService rag;

    @BeforeEach
    void setUp() {
        chunkMapper = mock(KbChunkMapper.class);
        docMapper = mock(KbDocMapper.class);
        props = new AiProperties();
        props.getRag().setEnabled(true);
        props.getRag().setMinScore(0.0);
        props.getRag().setTopK(5);
        props.getRag().setMaxContextChars(3000);

        List<KbChunk> chunks = new ArrayList<>();
        chunks.add(chunk(1L, 10L, "新员工入职培训制度：入职首月须完成安全生产与信息安全两门必修课。"));
        chunks.add(chunk(2L, 11L, "Java 后端开发技能标准：掌握 Spring Boot、MyBatis 与数据库优化。"));
        chunks.add(chunk(3L, 12L, "培训学时管理办法：每人年度培训学时不少于 40 学时。"));
        when(chunkMapper.selectList(any())).thenReturn(chunks);

        List<KbDoc> docs = new ArrayList<>();
        docs.add(doc(10L, "新员工入职培训制度"));
        docs.add(doc(11L, "研发岗位技能标准"));
        docs.add(doc(12L, "培训学时管理办法"));
        when(docMapper.selectBatchIds(any())).thenReturn(docs);

        rag = new RagService(chunkMapper, docMapper, props);
        rag.reload();
    }

    private KbChunk chunk(Long id, Long docId, String content) {
        KbChunk c = new KbChunk();
        c.setId(id);
        c.setDocId(docId);
        c.setContent(content);
        return c;
    }

    private KbDoc doc(Long id, String name) {
        KbDoc d = new KbDoc();
        d.setId(id);
        d.setDocName(name);
        d.setDocType("制度");
        d.setSource("内部");
        return d;
    }

    @Test
    @DisplayName("中文按单字 + 2-gram 切分")
    void tokenizeChineseBigram() {
        List<String> tokens = RagService.tokenize("技能标准");
        assertThat(tokens).contains("技", "能", "技", "技能", "标准");
        assertThat(tokens).contains("技能", "能标", "标准");
        assertThat(tokens).doesNotContain("技能标准");
    }

    @Test
    @DisplayName("英文与数字按词切分并转小写")
    void tokenizeEnglish() {
        List<String> tokens = RagService.tokenize("Spring Boot 3 培训");
        assertThat(tokens).contains("spring", "boot", "3");
        assertThat(tokens).doesNotContain("Spring");
    }

    @Test
    @DisplayName("中英混排时按字符类型正确断词")
    void tokenizeMixed() {
        List<String> tokens = RagService.tokenize("SQL优化");
        assertThat(tokens).contains("sql", "优", "化", "优化");
    }

    @Test
    @DisplayName("空串与 null 返回空列表而不是抛异常")
    void tokenizeNullSafe() {
        assertThat(RagService.tokenize(null)).isEmpty();
        assertThat(RagService.tokenize("")).isEmpty();
        assertThat(RagService.tokenize("   ")).isEmpty();
    }

    @Test
    @DisplayName("检索把相关片段排在最前")
    void searchRanksRelevantFirst() {
        List<RagHit> hits = rag.search("Java 技能标准", 3);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getDocName()).isEqualTo("研发岗位技能标准");
        assertThat(hits.get(0).getScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("得分归一到 0-1 且降序排列")
    void searchScoreNormalizedAndSorted() {
        List<RagHit> hits = rag.search("培训 学时", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getScore()).isEqualTo(1.0);
        for (int i = 1; i < hits.size(); i++) {
            assertThat(hits.get(i - 1).getScore()).isGreaterThanOrEqualTo(hits.get(i).getScore());
        }
    }

    @Test
    @DisplayName("topK 限制返回条数")
    void searchRespectsTopK() {
        assertThat(rag.search("培训", 1)).hasSize(1);
        assertThat(rag.search("培训", 2)).hasSize(2);
    }

    @Test
    @DisplayName("无关查询返回空，不硬凑结果")
    void searchNoMatchReturnsEmpty() {
        assertThat(rag.search("量子纠缠与弦论", 3)).isEmpty();
    }

    @Test
    @DisplayName("空查询直接返回空")
    void searchBlankQuery() {
        assertThat(rag.search("", 3)).isEmpty();
        assertThat(rag.search(null, 3)).isEmpty();
    }

    @Test
    @DisplayName("索引规模等于片段数，重建后不重复累加")
    void indexSizeAfterReload() {
        assertThat(rag.indexSize()).isEqualTo(3);
        rag.reload();
        assertThat(rag.indexSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("上下文拼装带来源与相关度标注")
    void buildContextHasSource() {
        String ctx = rag.buildContext(rag.search("技能标准", 1));
        assertThat(ctx).contains("来源：").contains("相关度");
    }

    @Test
    @DisplayName("检索关闭时返回空")
    void searchDisabled() {
        props.getRag().setEnabled(false);
        assertThat(rag.search("培训", 3)).isEmpty();
    }
}
