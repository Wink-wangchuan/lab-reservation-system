package com.lab.reservation.service;

import com.lab.reservation.dto.LabClosureDTO;
import com.lab.reservation.entity.LabClosure;

import java.time.LocalDate;
import java.util.List;

/**
 * 实验室闭馆日历服务
 *
 * @author LabWise
 * @since 3.7.0
 */
public interface LabClosureService {

    /** 闭馆列表：labId 为空表示全部实验室，含全局闭馆（lab_id = 0） */
    List<LabClosure> list(Long labId);

    /** 新增闭馆记录 */
    void save(LabClosureDTO dto);

    /** 删除闭馆记录 */
    void delete(Long id);

    /**
     * 指定实验室在某天是否闭馆
     *
     * @return 命中的闭馆记录，未闭馆返回 null
     */
    LabClosure findClosure(Long labId, LocalDate date);
}
