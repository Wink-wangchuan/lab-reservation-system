package com.lab.reservation.common;

import com.lab.reservation.exception.BusinessException;

/**
 * 乐观锁更新结果校验
 *
 * <p>MyBatis-Plus 乐观锁（{@code @Version}）在版本冲突时只返回影响行数 0，
 * 不会抛出异常；若不校验，表现为"提示保存成功但数据并未变更"。
 * 统一在此处把 0 行结果转成可读的业务异常，避免静默丢写。</p>
 *
 * <p>与悲观行锁的分工：管理端台账类实体（实验室/设备/用户/角色/权限）采用乐观锁，
 * 冲突时提示用户刷新重试；预约提交、报修流转、签到核销属于"复合条件校验 + 写入"，
 * 采用 {@code SELECT ... FOR UPDATE} 行锁保证原子性。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
public final class OptimisticLockGuard {

    private OptimisticLockGuard() {
    }

    /**
     * 校验更新影响行数
     *
     * @param affectedRows 更新影响行数（0 表示版本冲突或记录已不存在）
     * @param subject      业务主体描述，用于拼装提示，如"该设备"
     */
    public static void check(int affectedRows, String subject) {
        if (affectedRows == 0) {
            throw new BusinessException(ResultCode.CONCURRENT_MODIFIED,
                    subject + "已被他人修改，请刷新后重试");
        }
    }
}
