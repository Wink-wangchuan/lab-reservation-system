package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.KnowledgeDTO;
import com.lab.reservation.entity.AiKnowledge;
import com.lab.reservation.vo.KnowledgeHitVO;

import java.util.List;

/**
 * AI 知识库后台管理服务
 *
 * <p>知识库是 AI 回答的业务事实来源，任何写操作都会立即失效 Redis 缓存
 * {@code ai:kb:list}，避免「后台改了知识、线上 AI 仍按旧内容回答」。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
public interface KnowledgeService {

    /** 管理端分页（含停用项） */
    IPage<AiKnowledge> page(IPage<AiKnowledge> page, String keyword, String category, Integer status);

    /** 全部启用条目的分类清单 */
    List<String> categories();

    /** 新增 */
    AiKnowledge create(KnowledgeDTO dto);

    /** 编辑 */
    AiKnowledge update(Long id, KnowledgeDTO dto);

    /** 删除（逻辑删除） */
    void delete(Long id);

    /** 启用/停用 */
    void setStatus(Long id, Integer status);

    /** 命中测试：按提问关键词计分，返回命中的条目 */
    List<KnowledgeHitVO> test(String question, int limit);

    /** 导出全部条目（含停用，按分类/优先级排序） */
    List<AiKnowledge> exportAll();

    /** 批量导入：ID 存在则更新，否则新增；返回实际写入条数 */
    int importBatch(List<KnowledgeDTO> items);
}
