package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.AnnouncementDTO;
import com.lab.reservation.entity.SysAnnouncement;

import java.util.List;

/**
 * 系统公告服务
 *
 * @author LabWise
 * @since 4.6.0
 */
public interface AnnouncementService {

    /** 管理端分页 */
    IPage<SysAnnouncement> page(IPage<SysAnnouncement> page, String keyword, Integer status);

    /** 当前生效中的公告（最多 5 条） */
    List<SysAnnouncement> active();

    SysAnnouncement create(AnnouncementDTO dto);

    SysAnnouncement update(Long id, AnnouncementDTO dto);

    void delete(Long id);

    void setStatus(Long id, Integer status);
}
