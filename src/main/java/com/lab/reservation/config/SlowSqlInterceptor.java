package com.lab.reservation.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

/**
 * 慢 SQL 监控拦截器
 *
 * <p>只记录语句 ID 与耗时，不打印参数，避免把手机号、消息正文等业务数据写进日志。
 * 阈值固定 500ms：超过这个量级通常意味着缺索引或查询范围失控。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private static final long THRESHOLD_MS = 500L;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs >= THRESHOLD_MS) {
                Object first = invocation.getArgs().length > 0 ? invocation.getArgs()[0] : null;
                String id = first instanceof MappedStatement ms ? ms.getId() : "unknown";
                log.warn("[慢SQL] {} 耗时 {} ms", id, elapsedMs);
            }
        }
    }
}
