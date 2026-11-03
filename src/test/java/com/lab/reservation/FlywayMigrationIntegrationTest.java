package com.lab.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移集成测试（真实 MySQL）
 *
 * <p>容器启动时先执行 {@code sql/mysql.sql} 建立 v4.5 基线结构，
 * 应用启动后 Flyway 以 4.5 为 baseline 执行 V4.6，验证：
 * 新权限、报修 SLA 字段、系统配置/公告/数据权限表、归档表全部落地。</p>
 *
 * <p>本机没有 Docker 时自动跳过（{@code disabledWithoutDocker}），
 * CI 的 integration 任务会真实执行。</p>
 *
 * <p>注意必须使用 MOCK Web 环境：应用里的 Security 过滤链与 Knife4j 都依赖
 * Servlet 上下文，用 NONE 会因为缺少 HttpSecurity / SpringDocConfigProperties 启动失败。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class FlywayMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lab_reservation")
            .withUsername("root")
            .withPassword("root")
            // 容器首次初始化时执行基线脚本（与生产建库脚本一致）
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Paths.get("sql/mysql.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/01-baseline.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 测试期间关闭所有定时任务与归档，避免与断言抢数据
        registry.add("lab.schedule.auto-complete-reservation", () -> "false");
        registry.add("lab.schedule.remind-reservation", () -> "false");
        registry.add("lab.schedule.auto-close-checkin", () -> "false");
        registry.add("lab.schedule.promote-waitlist", () -> "false");
        registry.add("lab.schedule.purge-oper-log", () -> "false");
        registry.add("lab.schedule.repair-sla", () -> "false");
        registry.add("lab.archive.enabled", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v46MigrationApplied() {
        // 1. 新权限与角色授权
        Integer perms = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_permission
                 WHERE perm_code IN ('system:knowledge:manage', 'system:config:manage',
                                     'system:announcement:manage')
                """, Integer.class);
        assertThat(perms).isEqualTo(3);

        Integer grants = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                  JOIN sys_permission p ON p.id = rp.permission_id
                 WHERE p.perm_code IN ('system:knowledge:manage', 'system:config:manage',
                                       'system:announcement:manage')
                   AND rp.role_id IN (1, 2)
                """, Integer.class);
        assertThat(grants).isGreaterThanOrEqualTo(6);

        // 2. 报修 SLA 字段
        Integer slaColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'device_repair'
                   AND column_name IN ('priority', 'assignee_id', 'assignee_name',
                                       'expected_finish_time', 'sla_notified')
                """, Integer.class);
        assertThat(slaColumns).isEqualTo(5);

        // 3. 系统配置种子
        Integer configs = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_config
                 WHERE config_key IN ('system.maintenance', 'notify.external.enabled')
                """, Integer.class);
        assertThat(configs).isEqualTo(2);

        // 4. 新表存在
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_name IN ('sys_announcement', 'sys_user_lab',
                                      'lab_reservation_archive', 'sys_oper_log_archive')
                """, Integer.class);
        assertThat(tables).isEqualTo(4);

        // 5. Flyway 版本记录中存在 4.6
        Integer applied = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                 WHERE version = '4.6' AND success = 1
                """, Integer.class);
        assertThat(applied).isEqualTo(1);
    }
}
