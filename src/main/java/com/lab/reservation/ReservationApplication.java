package com.lab.reservation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智慧实验室预约管理系统 - 启动类
 *
 * <p>技术栈：SpringBoot 3.4.0 / Java 25 / MyBatis-Plus / SpringSecurity6 / Redis</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@SpringBootApplication
@EnableCaching                                  // 开启 Spring Cache 注解式缓存（Redis 落地）
@EnableAsync                                    // 开启异步处理（日志落库等）
@EnableScheduling                               // 开启定时任务（预约过期状态推进等）
@MapperScan("com.lab.reservation.mapper")       // 扫描 MyBatis-Plus Mapper 接口
public class ReservationApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ReservationApplication.class, args);
        // 从运行环境读取实际绑定地址与端口，避免写死 localhost:8080
        Environment env = context.getEnvironment();
        String address = env.getProperty("server.address", "localhost");
        String port = env.getProperty("server.port", "8080");
        System.out.printf("""
                =====================================================
                智慧实验室预约管理系统启动成功！
                接口文档：http://%s:%s/doc.html
                =====================================================
                %n""", address, port);
    }
}