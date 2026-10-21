package com.lab.reservation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Knife4j 接口文档配置
 *
 * <p>基于 springdoc-openapi3 + Knife4j 4.5.0，访问 /doc.html 查看增强文档，
 * 为答辩提供直观的接口说明、参数/响应模型展示。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智慧实验室预约管理系统 API")
                        .description("""
                                智慧实验室预约管理系统后端接口文档
                                技术栈：SpringBoot 3.4.0 / MyBatis-Plus / SpringSecurity6 / Redis
                                核心业务：时间段预约 + 时间冲突校验 + 多级审批流 + 预约状态机
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("LabWise").email("admin@lab.com")));
    }
}