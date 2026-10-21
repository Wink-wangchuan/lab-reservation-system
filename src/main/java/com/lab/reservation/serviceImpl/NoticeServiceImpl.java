package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.entity.SysNotice;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.SysNoticeMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.vo.NoticeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息通知中心服务实现
 *
 * <p>业务事件驱动的个人消息：预约提交/审批、报修受理/完成均会调用 {@link #push} 推送给相关用户；
 * 未读数用于顶栏红点，一键已读与删除保证消息中心可用性。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void push(Long userId, String title, String content, NoticeType type, Long bizId) {
        if (userId == null || StrUtil.isBlank(title)) {
            return;
        }
        noticeMapper.insert(build(userId, title, content, type, bizId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushAll(List<Long> userIds, String title, String content,
                        NoticeType type, Long bizId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream().distinct().forEach(uid -> push(uid, title, content, type, bizId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void broadcast(String title, String content, NoticeType type) {
        // 仅向启用状态的用户广播，避免打扰已禁用账号
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1));
        users.forEach(u -> push(u.getId(), title, content, type, null));
    }

    @Override
    public Long unreadCount() {
        Long userId = SecurityUtil.getUserId();
        Long count = noticeMapper.selectCount(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getReadFlag, 0));
        return count == null ? 0L : count;
    }

    @Override
    public IPage<NoticeVO> pageMine(IPage<NoticeVO> page, Integer readFlag, String noticeType) {
        Long userId = SecurityUtil.getUserId();
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getUserId, userId)
                .eq(readFlag != null, SysNotice::getReadFlag, readFlag)
                .eq(StrUtil.isNotBlank(noticeType), SysNotice::getNoticeType, noticeType)
                .orderByAsc(SysNotice::getReadFlag)
                .orderByDesc(SysNotice::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    public IPage<NoticeVO> pageAll(IPage<NoticeVO> page, String keyword, String noticeType) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .eq(StrUtil.isNotBlank(noticeType), SysNotice::getNoticeType, noticeType)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysNotice::getTitle, keyword)
                        .or().like(SysNotice::getContent, keyword))
                .orderByDesc(SysNotice::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long noticeId) {
        SysNotice notice = getNotice(noticeId);
        if (!notice.getUserId().equals(SecurityUtil.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本人的消息");
        }
        if (notice.getReadFlag() != null && notice.getReadFlag() == 1) {
            return;
        }
        notice.setReadFlag(1);
        notice.setReadTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead() {
        Long userId = SecurityUtil.getUserId();
        noticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                .eq(SysNotice::getUserId, userId)
                .eq(SysNotice::getReadFlag, 0)
                .set(SysNotice::getReadFlag, 1)
                .set(SysNotice::getReadTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long noticeId) {
        SysNotice notice = getNotice(noticeId);
        if (!notice.getUserId().equals(SecurityUtil.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本人的消息");
        }
        noticeMapper.deleteById(noticeId);
    }

    /* ==================== 辅助逻辑 ==================== */

    private SysNotice getNotice(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "消息不存在");
        }
        return notice;
    }

    private SysNotice build(Long userId, String title, String content,
                            NoticeType type, Long bizId) {
        SysNotice notice = new SysNotice();
        notice.setUserId(userId);
        notice.setTitle(StrUtil.maxLength(title, 120));
        notice.setContent(content);
        notice.setNoticeType((type == null ? NoticeType.SYSTEM : type).name());
        notice.setBizId(bizId);
        notice.setReadFlag(0);
        return notice;
    }

    private IPage<NoticeVO> queryPage(IPage<NoticeVO> voPage,
                                      LambdaQueryWrapper<SysNotice> wrapper) {
        Page<SysNotice> entityPage = new Page<>(voPage.getCurrent(), voPage.getSize());
        IPage<SysNotice> result = noticeMapper.selectPage(entityPage, wrapper);
        Page<NoticeVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    private NoticeVO convert(SysNotice notice) {
        NoticeVO vo = new NoticeVO();
        BeanUtils.copyProperties(notice, vo);
        vo.setNoticeTypeDesc(NoticeType.of(notice.getNoticeType()).getDesc());
        return vo;
    }
}
