package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.NoticeSendDTO;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.vo.NoticeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内消息通知中心接口
 *
 * @author LabWise
 * @since 1.1.0
 */
@Tag(name = "消息中心", description = "站内消息查询、已读、发送")
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "我的消息分页")
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public Result<PageVO<NoticeVO>> pageMine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(required = false) String noticeType) {
        IPage<NoticeVO> page = noticeService.pageMine(
                new Page<>(current, size), readFlag, noticeType);
        return Result.success(PageVO.of(page, n -> n));
    }

    @Operation(summary = "我的未读消息数")
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> unreadCount() {
        return Result.success(noticeService.unreadCount());
    }

    @Operation(summary = "标记消息已读")
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(id);
        return Result.success();
    }

    @Operation(summary = "一键全部已读")
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAllRead() {
        noticeService.markAllRead();
        return Result.success("已全部标记为已读", null);
    }

    @Operation(summary = "删除我的消息")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success("消息已删除", null);
    }

    @Operation(summary = "全部消息分页（管理端）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('notice:list')")
    public Result<PageVO<NoticeVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String keyword) {
        IPage<NoticeVO> page = noticeService.pageAll(new Page<>(current, size), keyword, noticeType);
        return Result.success(PageVO.of(page, n -> n));
    }

    @Operation(summary = "发送消息（定向/批量/全体广播）")
    @PostMapping("/send")
    @OperationLog(module = "消息中心", businessType = "insert", description = "发送站内消息")
    @PreAuthorize("hasAuthority('notice:send')")
    public Result<Void> send(@Valid @RequestBody NoticeSendDTO dto) {
        NoticeType type = NoticeType.of(dto.getNoticeType());
        if (dto.getUserId() != null) {
            noticeService.push(dto.getUserId(), dto.getTitle(), dto.getContent(), type, dto.getBizId());
        } else if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            List<Long> ids = dto.getUserIds();
            noticeService.pushAll(ids, dto.getTitle(), dto.getContent(), type, dto.getBizId());
        } else {
            noticeService.broadcast(dto.getTitle(), dto.getContent(), type);
        }
        return Result.success("消息已发送", null);
    }
}
