package com.lab.reservation.service;

import com.lab.reservation.dto.PasswordChangeDTO;
import com.lab.reservation.dto.ProfileUpdateDTO;
import com.lab.reservation.vo.ProfileStatsVO;
import com.lab.reservation.vo.ProfileVO;

/**
 * 个人中心服务接口
 *
 * <p>提供当前登录用户的资料查看/维护、密码修改与个人数据统计。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
public interface ProfileService {

    /** 我的基础资料 */
    ProfileVO detail();

    /** 更新我的基础资料（同步刷新 Redis 中的登录用户缓存） */
    ProfileVO update(ProfileUpdateDTO dto);

    /** 修改密码（校验原密码，BCrypt 重新加密） */
    void changePassword(PasswordChangeDTO dto);

    /** 我的数据统计（预约/报修/未读消息） */
    ProfileStatsVO stats();
}
