package com.lab.reservation.controller;

import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.PasswordChangeDTO;
import com.lab.reservation.dto.ProfileUpdateDTO;
import com.lab.reservation.service.ProfileService;
import com.lab.reservation.vo.ProfileStatsVO;
import com.lab.reservation.vo.ProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 个人中心接口
 *
 * <p>所有登录用户可维护本人资料、修改密码并查看个人数据统计。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Tag(name = "个人中心", description = "我的资料、密码修改、个人数据统计")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "我的资料")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<ProfileVO> detail() {
        return Result.success(profileService.detail());
    }

    @Operation(summary = "更新我的资料")
    @PutMapping
    @OperationLog(module = "个人中心", businessType = "update", description = "更新个人资料")
    @PreAuthorize("isAuthenticated()")
    public Result<ProfileVO> update(@Valid @RequestBody ProfileUpdateDTO dto) {
        return Result.success("资料已更新", profileService.update(dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    @OperationLog(module = "个人中心", businessType = "update", description = "修改密码")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        profileService.changePassword(dto);
        return Result.success("密码修改成功，请牢记新密码", null);
    }

    @Operation(summary = "我的数据统计")
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Result<ProfileStatsVO> stats() {
        return Result.success(profileService.stats());
    }
}
