package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.vo.NoticeVO;

import java.util.List;

/**
 * 站内消息通知中心服务接口
 *
 * <p>预约、报修等业务事件在服务层主动调用 {@link #push} 推送个人消息，
 * 用户端通过未读计数与消息列表消费，构成站内通知闭环。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
public interface NoticeService {

    /** 推送一条个人消息 */
    void push(Long userId, String title, String content, NoticeType type, Long bizId);

    /** 批量推送个人消息 */
    void pushAll(List<Long> userIds, String title, String content, NoticeType type, Long bizId);

    /** 全体广播（展开为每个启用用户一条消息） */
    void broadcast(String title, String content, NoticeType type);

    /** 当前用户未读消息数 */
    Long unreadCount();

    /** 我的消息分页 */
    IPage<NoticeVO> pageMine(IPage<NoticeVO> page, Integer readFlag, String noticeType);

    /** 全部消息分页（管理端） */
    IPage<NoticeVO> pageAll(IPage<NoticeVO> page, String keyword, String noticeType);

    /** 标记单条已读 */
    void markRead(Long noticeId);

    /** 一键全部已读 */
    void markAllRead();

    /** 删除我的一条消息（逻辑删除） */
    void delete(Long noticeId);
}
