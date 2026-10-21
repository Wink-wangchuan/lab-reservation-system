package com.lab.reservation.common;

import com.lab.reservation.entity.AiKnowledge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 知识库排序器测试
 *
 * <p>知识库的关键词表会随业务调整，排序规则一旦错乱，AI 会拿错知识作答。
 * 这里锁定三条不变量：命中多的优先、同分优先级高的优先、再同分按 ID 稳定排序。</p>
 *
 * @author LabWise
 * @since 2.4.0
 */
@DisplayName("知识库检索 KnowledgeRanker")
class KnowledgeRankerTest {

    @Test
    @DisplayName("按命中关键词个数计分")
    void countHits() {
        AiKnowledge item = knowledge(1L, "预约,审批,驳回", 0);
        assertEquals(2, KnowledgeRanker.hits("预约审批规则是什么", item));
        assertEquals(0, KnowledgeRanker.hits("签到流程", item));
    }

    @Test
    @DisplayName("关键词前后的空格会被忽略")
    void trimKeywords() {
        AiKnowledge item = knowledge(1L, " 预约 , 审批 ", 0);
        assertEquals(2, KnowledgeRanker.hits("预约和审批", item));
    }

    @Test
    @DisplayName("空提问、空关键词、空条目都返回 0")
    void blankInputs() {
        AiKnowledge item = knowledge(1L, "预约", 0);
        assertEquals(0, KnowledgeRanker.hits("", item));
        assertEquals(0, KnowledgeRanker.hits(null, item));
        assertEquals(0, KnowledgeRanker.hits("预约", knowledge(2L, "", 0)));
        assertEquals(0, KnowledgeRanker.hits("预约", knowledge(3L, "   ", 0)));
        assertEquals(0, KnowledgeRanker.hits("预约", null));
    }

    @Test
    @DisplayName("命中数量多的条目排在前面")
    void sortByScore() {
        AiKnowledge low = knowledge(1L, "预约", 0);
        AiKnowledge high = knowledge(2L, "预约,审批,冲突", 0);

        List<AiKnowledge> result = KnowledgeRanker.rank("预约审批冲突", List.of(low, high), 5);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getId());
    }

    @Test
    @DisplayName("同分时优先级高的在前")
    void sortByPriority() {
        AiKnowledge normal = knowledge(1L, "预约", 10);
        AiKnowledge important = knowledge(2L, "预约", 90);

        List<AiKnowledge> result = KnowledgeRanker.rank("预约", List.of(normal, important), 5);

        assertEquals(2L, result.get(0).getId());
    }

    @Test
    @DisplayName("同分同优先级时按 ID 升序，保证结果稳定")
    void stableOrder() {
        AiKnowledge a = knowledge(7L, "预约", 50);
        AiKnowledge b = knowledge(3L, "预约", 50);

        List<AiKnowledge> result = KnowledgeRanker.rank("预约", List.of(a, b), 5);

        assertEquals(3L, result.get(0).getId());
        assertEquals(7L, result.get(1).getId());
    }

    @Test
    @DisplayName("未命中的条目不参与结果")
    void excludeMisses() {
        AiKnowledge hit = knowledge(1L, "签到", 0);
        AiKnowledge miss = knowledge(2L, "报修", 0);

        List<AiKnowledge> result = KnowledgeRanker.rank("怎么签到", List.of(hit, miss), 5);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("超过上限时截断")
    void respectLimit() {
        List<AiKnowledge> items = List.of(
                knowledge(1L, "预约", 0),
                knowledge(2L, "预约", 0),
                knowledge(3L, "预约", 0));

        assertEquals(2, KnowledgeRanker.rank("预约", items, 2).size());
    }

    @Test
    @DisplayName("空输入与非法上限返回空列表")
    void emptyInputs() {
        assertTrue(KnowledgeRanker.rank(null, List.of(knowledge(1L, "预约", 0)), 5).isEmpty());
        assertTrue(KnowledgeRanker.rank("预约", List.of(), 5).isEmpty());
        assertTrue(KnowledgeRanker.rank("预约", null, 5).isEmpty());
        assertTrue(KnowledgeRanker.rank("预约", List.of(knowledge(1L, "预约", 0)), 0).isEmpty());
    }

    private AiKnowledge knowledge(Long id, String keywords, int priority) {
        AiKnowledge item = new AiKnowledge();
        item.setId(id);
        item.setKeywords(keywords);
        item.setPriority(priority);
        item.setTitle("知识" + id);
        item.setContent("内容" + id);
        return item;
    }
}
