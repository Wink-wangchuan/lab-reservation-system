package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.KnowledgeRanker;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.dto.KnowledgeDTO;
import com.lab.reservation.entity.AiKnowledge;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.AiKnowledgeMapper;
import com.lab.reservation.service.KnowledgeService;
import com.lab.reservation.vo.KnowledgeHitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * AI 知识库后台管理实现
 *
 * @author LabWise
 * @since 4.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final AiKnowledgeMapper knowledgeMapper;
    /** 知识变更后立即失效 AI 检索缓存 */
    private final StringRedisTemplate stringRedisTemplate;

    private static final int MAX_IMPORT_ITEMS = 500;
    private static final int HIT_CONTENT_PREVIEW = 200;

    @Override
    public IPage<AiKnowledge> page(IPage<AiKnowledge> page, String keyword,
                                   String category, Integer status) {
        LambdaQueryWrapper<AiKnowledge> wrapper = new LambdaQueryWrapper<AiKnowledge>()
                .eq(StrUtil.isNotBlank(category), AiKnowledge::getCategory, category)
                .eq(status != null, AiKnowledge::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(AiKnowledge::getTitle, keyword)
                        .or().like(AiKnowledge::getKeywords, keyword)
                        .or().like(AiKnowledge::getContent, keyword))
                .orderByAsc(AiKnowledge::getCategory)
                .orderByDesc(AiKnowledge::getPriority)
                .orderByDesc(AiKnowledge::getUpdateTime);
        Page<AiKnowledge> entityPage = new Page<>(page.getCurrent(), page.getSize());
        return knowledgeMapper.selectPage(entityPage, wrapper);
    }

    @Override
    public List<String> categories() {
        return knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                        .select(AiKnowledge::getCategory)
                        .eq(AiKnowledge::getStatus, 1))
                .stream()
                .map(AiKnowledge::getCategory)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledge create(KnowledgeDTO dto) {
        AiKnowledge entity = new AiKnowledge();
        copy(dto, entity);
        knowledgeMapper.insert(entity);
        evictCache();
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledge update(Long id, KnowledgeDTO dto) {
        AiKnowledge entity = require(id);
        copy(dto, entity);
        knowledgeMapper.updateById(entity);
        evictCache();
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        knowledgeMapper.deleteById(id);
        evictCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能是 0（停用）或 1（启用）");
        }
        AiKnowledge entity = require(id);
        entity.setStatus(status);
        knowledgeMapper.updateById(entity);
        evictCache();
    }

    @Override
    public List<KnowledgeHitVO> test(String question, int limit) {
        if (StrUtil.isBlank(question)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请输入测试问题");
        }
        int max = limit <= 0 ? 5 : Math.min(limit, 20);
        List<AiKnowledge> enabled = knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(AiKnowledge::getStatus, 1));
        // 与线上检索同一套打分规则，命中测试结果才能代表真实表现
        return enabled.stream()
                .map(item -> {
                    KnowledgeHitVO vo = new KnowledgeHitVO();
                    vo.setId(item.getId());
                    vo.setCategory(item.getCategory());
                    vo.setTitle(item.getTitle());
                    vo.setScore(KnowledgeRanker.hits(question, item));
                    vo.setContent(StrUtil.maxLength(item.getContent(), HIT_CONTENT_PREVIEW));
                    return vo;
                })
                .filter(vo -> vo.getScore() > 0)
                .sorted(Comparator.comparingInt(KnowledgeHitVO::getScore).reversed())
                .limit(max)
                .toList();
    }

    @Override
    public List<AiKnowledge> exportAll() {
        return knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .orderByAsc(AiKnowledge::getCategory)
                .orderByDesc(AiKnowledge::getPriority));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importBatch(List<KnowledgeDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "导入内容为空");
        }
        if (items.size() > MAX_IMPORT_ITEMS) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单次最多导入 " + MAX_IMPORT_ITEMS + " 条");
        }
        int written = 0;
        for (KnowledgeDTO dto : items) {
            if (dto == null || StrUtil.isBlank(dto.getTitle()) || StrUtil.isBlank(dto.getContent())) {
                continue;
            }
            AiKnowledge existing = dto.getId() == null ? null : knowledgeMapper.selectById(dto.getId());
            if (existing == null) {
                AiKnowledge entity = new AiKnowledge();
                // 导入保留原 ID，保证多次导入同一文件不会产生重复条目
                entity.setId(dto.getId());
                copy(dto, entity);
                knowledgeMapper.insert(entity);
            } else {
                copy(dto, existing);
                knowledgeMapper.updateById(existing);
            }
            written++;
        }
        if (written > 0) {
            evictCache();
        }
        return written;
    }

    private void copy(KnowledgeDTO dto, AiKnowledge entity) {
        entity.setCategory(StrUtil.trimToEmpty(dto.getCategory()));
        entity.setTitle(StrUtil.trimToEmpty(dto.getTitle()));
        entity.setKeywords(StrUtil.trimToEmpty(dto.getKeywords()));
        entity.setContent(dto.getContent());
        entity.setPriority(dto.getPriority() == null ? 0 : dto.getPriority());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private AiKnowledge require(Long id) {
        AiKnowledge entity = id == null ? null : knowledgeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识条目不存在");
        }
        return entity;
    }

    /** 知识变更立即失效缓存，后台改完马上生效 */
    private void evictCache() {
        try {
            stringRedisTemplate.delete(RedisKeyConstants.AI_KB);
        } catch (Exception e) {
            log.warn("[AI] 知识库缓存失效失败，将在 10 分钟内自动过期：{}", e.getMessage());
        }
    }
}
