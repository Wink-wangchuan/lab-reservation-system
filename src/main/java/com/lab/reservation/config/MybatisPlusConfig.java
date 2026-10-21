package com.lab.reservation.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>注册分页插件与乐观锁插件。分页插件是实现"复杂查询 + 分页"的根基，
 * 配合 {@code PageVO} 返回结构统一前端契约。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 插件组
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件（指定数据库类型为 MySQL）
        PaginationInnerInterceptor pagination =
                new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);          // 单次最大查询条数，防全表拉取
        pagination.setOverflow(false);         // 页码越界不 base64 回绕
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁插件：对声明了 @Version 的管理端台账类实体（实验室/设备/用户/角色/权限）生效，
        // 冲突时由 OptimisticLockGuard 统一转成可读提示；预约/报修/签到等复合校验流程另用行锁保证原子性
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}