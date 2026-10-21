package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.CheckinVerifyDTO;
import com.lab.reservation.entity.LabCheckin;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.enums.CheckinStatus;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabCheckinMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.CheckinService;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.CheckinVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预约签到核销服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：</b>签到状态机 + 时间窗校验 + 定时任务推进。</p>
 * <ul>
 *   <li>审批通过即生成 6 位唯一核销码（签到凭证）</li>
 *   <li>核销签到限定在「开始前 30 分钟 ~ 结束时刻」时间窗内</li>
 *   <li>定时任务每 5 分钟扫描：超时未签到 → 已过期；已签到超时 → 已完成，并同步预约单状态</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final LabCheckinMapper checkinMapper;
    private final LabReservationMapper reservationMapper;
    /** 签到超时等状态推进需要给预约人推送站内消息 */
    private final NoticeService noticeService;
    private final StatisticsService statisticsService;

    /** 允许提前签到时长的分钟数 */
    private static final int EARLY_MINUTES = 30;

    /** 具备核销权限的权限码（供本人/管理员判定复用） */
    private static final String PERM_VERIFY = "checkin:verify";

    /** 超级管理员可查看核销码；教师等核销人只能现场输入核销码 */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createForReservation(LabReservation reservation) {
        if (reservation == null || reservation.getId() == null) {
            return;
        }
        Long exists = checkinMapper.selectCount(new LambdaQueryWrapper<LabCheckin>()
                .eq(LabCheckin::getReservationId, reservation.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        LabCheckin checkin = new LabCheckin();
        checkin.setCheckinCode(generateCode());
        checkin.setReservationId(reservation.getId());
        checkin.setReservationNo(reservation.getReservationNo());
        checkin.setLabId(reservation.getLabId());
        checkin.setLabName(reservation.getLabName());
        checkin.setUserId(reservation.getStudentId());
        checkin.setUserName(reservation.getStudentName());
        checkin.setReserveDate(reservation.getReserveDate());
        checkin.setStartTime(reservation.getStartTime());
        checkin.setEndTime(reservation.getEndTime());
        checkin.setStatus(CheckinStatus.SIGN_IN_PENDING.name());
        checkinMapper.insert(checkin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinVO codeOf(Long reservationId) {
        LoginUser current = SecurityUtil.getLoginUser();
        LabReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约单不存在");
        }
        boolean owner = reservation.getStudentId().equals(current.getUserId());
        // 核销码是学生到场出示的凭证：核销人（教师）只能现场输入核销，
        // 管理员需要兜底处理时可查看，故仅「本人或超级管理员」可取码
        if (!owner && !SecurityUtil.hasRole(ROLE_SUPER_ADMIN)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看本人的核销码");
        }
        ReservationStatus status = ReservationStatus.of(reservation.getStatus());
        if (status != ReservationStatus.APPROVED && status != ReservationStatus.COMPLETED) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "仅审批通过的预约可获取核销码，当前状态["
                            + (status == null ? "未知" : status.getDesc()) + "]");
        }
        LabCheckin checkin = checkinMapper.selectOne(new LambdaQueryWrapper<LabCheckin>()
                .eq(LabCheckin::getReservationId, reservationId)
                .last("LIMIT 1"));
        if (checkin == null) {
            // 历史数据兜底：审批通过但未生成签到凭证时按需补建
            createForReservation(reservation);
            checkin = checkinMapper.selectOne(new LambdaQueryWrapper<LabCheckin>()
                    .eq(LabCheckin::getReservationId, reservationId)
                    .last("LIMIT 1"));
        }
        return convert(checkin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinVO verify(CheckinVerifyDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();
        // 并发串行化：先按核销码锁行，再校验状态，避免同一码被并发核销两次
        checkinMapper.lockByCode(dto.getCheckinCode().trim());
        LabCheckin checkin = checkinMapper.selectOne(new LambdaQueryWrapper<LabCheckin>()
                .eq(LabCheckin::getCheckinCode, dto.getCheckinCode().trim())
                .last("LIMIT 1"));
        if (checkin == null) {
            throw new BusinessException(ResultCode.CHECKIN_CODE_INVALID);
        }
        CheckinStatus from = CheckinStatus.of(checkin.getStatus());
        if (!CheckinStatus.canTransition(from, CheckinStatus.CHECKED_IN)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "该核销码当前状态为[" + (from == null ? "未知" : from.getDesc()) + "]，无法签到");
        }
        LocalDateTime startAt = LocalDateTime.of(checkin.getReserveDate(), checkin.getStartTime());
        LocalDateTime endAt = LocalDateTime.of(checkin.getReserveDate(), checkin.getEndTime());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt.minusMinutes(EARLY_MINUTES))) {
            throw new BusinessException(ResultCode.CHECKIN_NOT_IN_WINDOW,
                    "尚未到达签到时间，最早可提前 " + EARLY_MINUTES + " 分钟签到");
        }
        if (now.isAfter(endAt)) {
            // 已超出时段：交由定时任务归档为「已过期」，此处直接拒绝
            throw new BusinessException(ResultCode.CHECKIN_EXPIRED);
        }
        checkin.setStatus(CheckinStatus.CHECKED_IN.name());
        checkin.setCheckinTime(now);
        checkin.setCheckinById(current.getUserId());
        checkin.setCheckinByName(current.getRealName());
        checkin.setRemark(dto.getRemark());
        checkinMapper.updateById(checkin);
        statisticsService.evictStatCache();
        return convert(checkin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinVO checkout(Long checkinId, String remark) {
        LoginUser current = SecurityUtil.getLoginUser();
        LabCheckin checkin = getCheckin(checkinId);
        boolean owner = checkin.getUserId().equals(current.getUserId());
        if (!owner && !SecurityUtil.hasPermission(PERM_VERIFY)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能签退本人的预约");
        }
        CheckinStatus from = CheckinStatus.of(checkin.getStatus());
        if (!CheckinStatus.canTransition(from, CheckinStatus.COMPLETED)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "当前状态[" + (from == null ? "未知" : from.getDesc()) + "]无法签退");
        }
        checkin.setStatus(CheckinStatus.COMPLETED.name());
        checkin.setCheckoutTime(LocalDateTime.now());
        if (StrUtil.isNotBlank(remark)) {
            checkin.setRemark(remark);
        }
        checkinMapper.updateById(checkin);
        // 签退完成同步预约单为已完成，闭合业务链路
        completeReservation(checkin.getReservationId());
        statisticsService.evictStatCache();
        return convert(checkin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByReservation(Long reservationId, String reason) {
        LabCheckin checkin = checkinMapper.selectOne(new LambdaQueryWrapper<LabCheckin>()
                .eq(LabCheckin::getReservationId, reservationId)
                .last("LIMIT 1"));
        if (checkin == null) {
            return;
        }
        CheckinStatus from = CheckinStatus.of(checkin.getStatus());
        if (!CheckinStatus.canTransition(from, CheckinStatus.CANCELLED)) {
            return;
        }
        checkin.setStatus(CheckinStatus.CANCELLED.name());
        checkin.setRemark(reason);
        checkinMapper.updateById(checkin);
    }

    @Override
    public IPage<CheckinVO> pageMine(IPage<CheckinVO> page, String status) {
        Long userId = SecurityUtil.getUserId();
        LambdaQueryWrapper<LabCheckin> wrapper = new LambdaQueryWrapper<LabCheckin>()
                .eq(LabCheckin::getUserId, userId)
                .eq(StrUtil.isNotBlank(status), LabCheckin::getStatus, status)
                .orderByDesc(LabCheckin::getReserveDate)
                .orderByDesc(LabCheckin::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    public IPage<CheckinVO> pageAll(IPage<CheckinVO> page, String status, String keyword) {
        // 管理员可核对核销码；教师仅能现场核销，列表不返回核销码、也不支持按码搜索
        boolean viewCode = SecurityUtil.hasRole(ROLE_SUPER_ADMIN);
        LambdaQueryWrapper<LabCheckin> wrapper = new LambdaQueryWrapper<LabCheckin>()
                .eq(StrUtil.isNotBlank(status), LabCheckin::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> {
                    w.like(LabCheckin::getReservationNo, keyword)
                            .or().like(LabCheckin::getUserName, keyword)
                            .or().like(LabCheckin::getLabName, keyword);
                    if (viewCode) {
                        w.or().like(LabCheckin::getCheckinCode, keyword);
                    }
                })
                .orderByDesc(LabCheckin::getReserveDate)
                .orderByDesc(LabCheckin::getCreateTime);
        IPage<CheckinVO> result = queryPage(page, wrapper);
        if (!viewCode) {
            result.getRecords().forEach(vo -> vo.setCheckinCode(null));
        }
        return result;
    }

    @Override
    public CheckinVO detail(Long checkinId) {
        LabCheckin checkin = getCheckin(checkinId);
        // 仅本人、或具备签到核销/管理权限的角色可查看，防止越权读取他人签到信息
        LoginUser current = SecurityUtil.getLoginUser();
        boolean owner = checkin.getUserId().equals(current.getUserId());
        boolean manager = SecurityUtil.hasPermission(PERM_VERIFY) || SecurityUtil.hasPermission("checkin:list");
        if (!owner && !manager) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该签到单");
        }
        CheckinVO vo = convert(checkin);
        // 核销码对预约本人与超级管理员可见，其余核销人（教师）看详情时脱敏
        if (!owner && !SecurityUtil.hasRole(ROLE_SUPER_ADMIN)) {
            vo.setCheckinCode(null);
        }
        return vo;
    }

    /**
     * 定时推进签到状态（每 5 分钟）：
     * 超出预约结束时刻后，待签到 → 已过期，已签到 → 已完成，并同步预约单为已完成。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoCloseExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<LabCheckin> list = checkinMapper.selectList(new LambdaQueryWrapper<LabCheckin>()
                .in(LabCheckin::getStatus,
                        List.of(CheckinStatus.SIGN_IN_PENDING.name(),
                                CheckinStatus.CHECKED_IN.name())));
        int changed = 0;
        for (LabCheckin checkin : list) {
            LocalDateTime endAt = LocalDateTime.of(checkin.getReserveDate(), checkin.getEndTime());
            if (now.isBefore(endAt)) {
                continue;
            }
            CheckinStatus from = CheckinStatus.of(checkin.getStatus());
            CheckinStatus to = from == CheckinStatus.SIGN_IN_PENDING
                    ? CheckinStatus.EXPIRED
                    : CheckinStatus.COMPLETED;
            if (!CheckinStatus.canTransition(from, to)) {
                continue;
            }
            if (to == CheckinStatus.COMPLETED && checkin.getCheckoutTime() == null) {
                checkin.setCheckoutTime(endAt);
            }
            checkin.setStatus(to.name());
            checkinMapper.updateById(checkin);
            if (to == CheckinStatus.COMPLETED) {
                completeReservation(checkin.getReservationId());
            } else {
                // 超时未签到：主动告知预约人，避免其以为预约仍然有效
                noticeService.push(checkin.getUserId(), "预约已过期",
                        String.format("实验室「%s」%s %s-%s 的预约因未在时段内签到已过期。",
                                checkin.getLabName(), checkin.getReserveDate(),
                                checkin.getStartTime(), checkin.getEndTime()),
                        NoticeType.RESERVATION, checkin.getReservationId());
            }
            changed++;
        }
        if (changed > 0) {
            statisticsService.evictStatCache();
            log.info("[签到核销] 定时推进完成，本次更新 {} 条签到单", changed);
        }
    }

    /* ==================== 辅助逻辑 ==================== */

    private LabCheckin getCheckin(Long id) {
        // 并发串行化：锁住签到单行，避免并发核销/签退绕过状态机校验
        checkinMapper.lockCheckinRow(id);
        LabCheckin checkin = checkinMapper.selectById(id);
        if (checkin == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "签到单不存在");
        }
        return checkin;
    }

    /** 预约单置为已完成（仅 APPROVED -> COMPLETED 合法） */
    private void completeReservation(Long reservationId) {
        if (reservationId == null) {
            return;
        }
        reservationMapper.update(null, new LambdaUpdateWrapper<LabReservation>()
                .eq(LabReservation::getId, reservationId)
                .eq(LabReservation::getStatus, ReservationStatus.APPROVED.name())
                .set(LabReservation::getStatus, ReservationStatus.COMPLETED.name())
                .set(LabReservation::getCompleteTime, LocalDateTime.now()));
    }

    /** 生成 6 位数字核销码，最多重试 20 次保证唯一 */
    private String generateCode() {
        for (int i = 0; i < 20; i++) {
            String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
            Long exists = checkinMapper.selectCount(new LambdaQueryWrapper<LabCheckin>()
                    .eq(LabCheckin::getCheckinCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BusinessException(ResultCode.BUSINESS_ERROR, "核销码生成失败，请重试");
    }

    private IPage<CheckinVO> queryPage(IPage<CheckinVO> voPage,
                                       LambdaQueryWrapper<LabCheckin> wrapper) {
        Page<LabCheckin> entityPage = new Page<>(voPage.getCurrent(), voPage.getSize());
        IPage<LabCheckin> result = checkinMapper.selectPage(entityPage, wrapper);
        Page<CheckinVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    /**
     * 实体 -> VO：展示层按当前时间推导过期/完成状态，并给出可签到/可签退标记
     */
    private CheckinVO convert(LabCheckin checkin) {
        CheckinVO vo = new CheckinVO();
        BeanUtils.copyProperties(checkin, vo);
        CheckinStatus status = CheckinStatus.of(checkin.getStatus());
        LocalDateTime now = LocalDateTime.now();
        if (checkin.getReserveDate() != null && checkin.getStartTime() != null
                && checkin.getEndTime() != null) {
            LocalDateTime startAt = LocalDateTime.of(checkin.getReserveDate(), checkin.getStartTime());
            LocalDateTime endAt = LocalDateTime.of(checkin.getReserveDate(), checkin.getEndTime());
            if (status == CheckinStatus.SIGN_IN_PENDING && now.isAfter(endAt)) {
                status = CheckinStatus.EXPIRED;
            } else if (status == CheckinStatus.CHECKED_IN && now.isAfter(endAt)) {
                status = CheckinStatus.COMPLETED;
            }
            vo.setCheckable(status == CheckinStatus.SIGN_IN_PENDING
                    && !now.isBefore(startAt.minusMinutes(EARLY_MINUTES))
                    && !now.isAfter(endAt));
        }
        vo.setCheckoutable(status == CheckinStatus.CHECKED_IN);
        vo.setStatus(status == null ? checkin.getStatus() : status.name());
        vo.setStatusDesc(status == null ? "未知" : status.getDesc());
        return vo;
    }
}
