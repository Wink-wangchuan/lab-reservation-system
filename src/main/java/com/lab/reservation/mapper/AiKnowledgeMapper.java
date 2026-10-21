package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.AiKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 助手知识库 Mapper
 *
 * @author LabWise
 * @since 2.2.0
 */
@Mapper
public interface AiKnowledgeMapper extends BaseMapper<AiKnowledge> {
}
