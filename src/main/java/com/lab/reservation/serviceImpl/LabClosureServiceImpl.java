package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.LabClosureDTO;
import com.lab.reservation.entity.LabClosure;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabClosureMapper;
import com.lab.reservation.service.LabClosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 实验室闭馆日历服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：节假日/临时闭馆覆盖</b></p>
 * 开放时段按星期配置，真实排期还要考虑国庆、考试周、临时维护等「按日期」的例外。
 * 闭馆记录按日期区间命中，且支持 {@code lab_id = 0} 表示全校实验室停用，
 * 预约提交与可预约时段查询都会先做过这道校验。
 *
 * @author LabWise
 * @since 3.7.0
 */
@Service
@RequiredArgsConstructor
public class LabClosureServiceImpl implements LabClosureService {

    private final LabClosureMapper closureMapper;

    @Override
    public List<LabClosure> list(Long labId) {
        LambdaQueryWrapper<LabClosure> wrapper = new LambdaQueryWrapper<LabClosure>()
                .orderByDesc(LabClosure::getStartDate)
                .orderByDesc(LabClosure::getId);
        if (labId != null && labId > 0) {
            // 管理端按实验室筛选时，同时带出「全部实验室」的闭馆记录
            wrapper.and(w -> w.eq(LabClosure::getLabId, labId).or().eq(LabClosure::getLabId, 0L));
        }
        return closureMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(LabClosureDTO dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
        LabClosure closure = new LabClosure();
        BeanUtils.copyProperties(dto, closure);
        closure.setId(null);
        closure.setLabId(dto.getLabId() == null ? 0L : dto.getLabId());
        closureMapper.insert(closure);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LabClosure closure = closureMapper.selectById(id);
        if (closure == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "闭馆记录不存在");
        }
        closureMapper.deleteById(id);
    }

    @Override
    public LabClosure findClosure(Long labId, LocalDate date) {
        if (date == null) {
            return null;
        }
        return closureMapper.selectOne(new LambdaQueryWrapper<LabClosure>()
                .and(w -> w.eq(LabClosure::getLabId, 0L).or().eq(LabClosure::getLabId, labId))
                .le(LabClosure::getStartDate, date)
                .ge(LabClosure::getEndDate, date)
                .orderByDesc(LabClosure::getLabId)   // 指定实验室的闭馆优先于全校闭馆
                .last("LIMIT 1"));
    }
}
