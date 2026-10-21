package com.lab.reservation.common;

import cn.hutool.core.util.StrUtil;
import com.lab.reservation.entity.AiKnowledge;

import java.util.Comparator;
import java.util.List;

/**
 * AI 知识库条目排序器
 *
 * <p>按「提问命中关键词的个数」为每条知识计分，取分数最高的若干条注入提示词。
 * 同分时依次比较：优先级（大者优先）→ 条目 ID（小者优先，保证结果稳定可复现）。</p>
 *
 * <p>抽成独立工具类而非私有方法，是为了让这套排序规则可以被单元测试直接覆盖——
 * 知识库的关键词表调整频繁，命中顺序一旦错乱，AI 会答成另一条知识的内容。</p>
 *
 * @author LabWise
 * @since 2.4.0
 */
public final class KnowledgeRanker {

    private KnowledgeRanker() {
    }

    /**
     * 按提问相关度排序并截断
     *
     * @param question   用户提问
     * @param candidates 候选知识条目（已过滤停用项）
     * @param limit      最多返回条数
     * @return 命中的知识条目，未命中返回空列表
     */
    public static List<AiKnowledge> rank(String question, List<AiKnowledge> candidates, int limit) {
        if (StrUtil.isBlank(question) || candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        return candidates.stream()
                .map(item -> new Scored(item, hits(question, item)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator
                        .comparingInt(Scored::score).reversed()
                        .thenComparing(scored -> priority(scored.item()), Comparator.reverseOrder())
                        .thenComparing(scored -> scored.item().getId(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .map(Scored::item)
                .toList();
    }

    /**
     * 统计提问命中了该条目多少个关键词
     *
     * <p>关键词以英文逗号分隔，逐项 trim 后做包含判断；空项忽略。</p>
     */
    public static int hits(String question, AiKnowledge item) {
        if (StrUtil.isBlank(question) || item == null || StrUtil.isBlank(item.getKeywords())) {
            return 0;
        }
        int count = 0;
        for (String keyword : item.getKeywords().split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && question.contains(trimmed)) {
                count++;
            }
        }
        return count;
    }

    private static int priority(AiKnowledge item) {
        return item.getPriority() == null ? 0 : item.getPriority();
    }

    private record Scored(AiKnowledge item, int score) {
    }
}
