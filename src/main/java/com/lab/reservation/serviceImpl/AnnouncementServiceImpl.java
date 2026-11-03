package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.AnnouncementDTO;
import com.lab.reservation.entity.SysAnnouncement;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.SysAnnouncementMapper;
import com.lab.reservation.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统公告实现
 *
 * @author LabWise
 * @since 4.6.0
 */
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final SysAnnouncementMapper announcementMapper;

    private static final List<String> LEVELS = List.of("INFO", "WARNING", "URGENT");
    private static final int ACTIVE_LIMIT = 5;

    @Override
    public IPage<SysAnnouncement> page(IPage<SysAnnouncement> page, String keyword, Integer status) {
        LambdaQueryWrapper<SysAnnouncement> wrapper = new LambdaQueryWrapper<SysAnnouncement>()
                .eq(status != null, SysAnnouncement::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysAnnouncement::getTitle, keyword)
                        .or().like(SysAnnouncement::getContent, keyword))
                .orderByDesc(SysAnnouncement::getCreateTime);
        return announcementMapper.selectPage(new Page<>(page.getCurrent(), page.getSize()), wrapper);
    }

    @Override
    public List<SysAnnouncement> active() {
        LocalDateTime now = LocalDateTime.now();
        return announcementMapper.selectList(new LambdaQueryWrapper<SysAnnouncement>()
                .eq(SysAnnouncement::getStatus, 1)
                .and(w -> w.isNull(SysAnnouncement::getStartTime)
                        .or().le(SysAnnouncement::getStartTime, now))
                .and(w -> w.isNull(SysAnnouncement::getEndTime)
                        .or().ge(SysAnnouncement::getEndTime, now))
                .orderByDesc(SysAnnouncement::getCreateTime)
                .last("LIMIT " + ACTIVE_LIMIT));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAnnouncement create(AnnouncementDTO dto) {
        SysAnnouncement entity = new SysAnnouncement();
        copy(dto, entity);
        announcementMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysAnnouncement update(Long id, AnnouncementDTO dto) {
        SysAnnouncement entity = require(id);
        copy(dto, entity);
        announcementMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        announcementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能是 0 或 1");
        }
        SysAnnouncement entity = require(id);
        entity.setStatus(status);
        announcementMapper.updateById(entity);
    }

    private void copy(AnnouncementDTO dto, SysAnnouncement entity) {
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        String level = StrUtil.isBlank(dto.getLevel()) ? "INFO" : dto.getLevel().toUpperCase();
        if (!LEVELS.contains(level)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "公告级别只能是 INFO/WARNING/URGENT");
        }
        entity.setTitle(StrUtil.trimToEmpty(dto.getTitle()));
        entity.setContent(dto.getContent());
        entity.setLevel(level);
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
    }

    private SysAnnouncement require(Long id) {
        SysAnnouncement entity = id == null ? null : announcementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return entity;
    }
}
