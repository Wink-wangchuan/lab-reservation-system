-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.7.0 -> v1.8.0
--
--  目标：把设备台账补充到与实际院校规模相符的量级
--
--  背景：原先每间实验室只录了 2-3 件「代表设备」，16 间实验室合计仅 32 台，
--        与「60 座的计算机实验室应有 60 台机型」这类实际情况不符，
--        驾驶舱「设备总数」因此显得异常（统计口径本身没有问题）。
--
--  规则：按各实验室 capacity 与 equipment_desc 补齐主要资产
--        计算机类按座位数补台式机/工作站；实训室按工位数补实训器械；
--        机房按机柜/节点数、实验室按实验台/仪器数补齐。
--
--  生成方式：设备台账 = 每件独立资产一行
--    - 主键 id     = 实验室ID*10000 + 该实验室内的资产序号  → 天然唯一且不与既有 2xx/3xx 冲突
--    - 设备编号 code = DEV-<实验室ID>-<三位序号>            → 唯一，重复执行由 INSERT IGNORE 跳过
--    - 表单字段一律取自下方计划表，设备的类型全部落在前端「设备类型」词表内
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    set -a && . ~/lab-reservation/.env && set +a
--    ~/opt/jre21/bin/java -cp <SqlRunner.class 目录>:<mysql-connector.jar> \
--        SqlRunner @upgrade_v1.8.sql
--
--  【执行后必做】本脚本是直接改库、未经应用写操作，
--   而驾驶舱统计走 Redis 版本化缓存（stat:cache:{业务}:v{版本}），不会自动感知数据变化，
--   因此执行完必须自增版本号让统计重新计算，否则「设备总数」仍显示旧值：
--     ~/opt/redis/bin/redis-cli -h 127.0.0.1 -p 6379 INCR stat:cache:version
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- 计划表 p：(实验室ID, 序号基址, 数量, 名称, 类型, 购置日期, 品牌型号, 单价)
-- 数字集 t：0..69（十位 0-6 × 个位 0-9），满足各实验室单类最多 65 件的生成需求
INSERT IGNORE INTO device
(id, device_code, device_name, device_type, lab_id, lab_name, purchase_date, brand_model, status, fault_status, price)
SELECT l.id * 10000 + p.base + t.n,
       CONCAT('DEV-', l.id, '-', LPAD(p.base + t.n, 3, '0')),
       p.dname, p.dtype, l.id, l.lab_name, p.pdate, p.model, 1, 0, p.price
FROM lab_info l
JOIN (
    SELECT 101 AS lab_id, 0 AS base, 60 AS cnt, '教学台式机'       AS dname, '计算机'     AS dtype, '2024-03-12' AS pdate, '联想 ThinkCentre M760'   AS model, 5200.00  AS price
    UNION ALL SELECT 102, 0,  40, 'AI 训练工作站',    '计算机',     '2025-03-20', 'RTX 4090 图形工作站',   42000.00
    UNION ALL SELECT 103, 0,  25, '物理实验台',       '实验仪器',   '2023-09-01', '国产定制实验台',         8800.00
    UNION ALL SELECT 104, 0,  20, '数字示波器',       '实验仪器',   '2023-05-10', 'Tektronix TBS1104',      6500.00
    UNION ALL SELECT 104, 20, 25, '数字万用表',       '实验仪器',   '2023-05-10', 'Fluke 17B+',              680.00
    UNION ALL SELECT 105, 0,  60, '教学台式机',       '计算机',     '2024-03-12', '联想 ThinkCentre M760',   5200.00
    UNION ALL SELECT 106, 0,   8, '服务器机柜',       '实验仪器',   '2024-09-01', '图腾 42U 标准机柜',       4800.00
    UNION ALL SELECT 106, 8,   4, 'GPU 计算节点',     '服务器',     '2024-09-01', 'NVIDIA A100 计算节点',  268000.00
    UNION ALL SELECT 107, 0,  50, '双屏开发工作站',   '计算机',     '2024-04-18', 'Dell Precision 3660',     9800.00
    UNION ALL SELECT 108, 0,  20, '路由交换实验台',   '网络设备',   '2023-11-06', '华为 S5731 实验台',      15600.00
    UNION ALL SELECT 108, 20,  6, '网络实训机柜',     '实验仪器',   '2023-11-06', '图腾 42U 标准机柜',       2600.00
    UNION ALL SELECT 109, 0,  40, 'AI 训练工作站',    '服务器',     '2025-03-20', 'RTX 4090 图形工作站',    62000.00
    UNION ALL SELECT 109, 40, 10, '边缘推理盒子',     '物联网设备', '2025-03-20', 'Jetson Orin NX 套件',    4200.00
    UNION ALL SELECT 110, 0,  48, '语音学习终端',     '计算机',     '2023-06-15', '讯飞语音学习终端',       3600.00
    UNION ALL SELECT 111, 0,  40, 'STM32 开发套件',   '实训器械',   '2024-05-08', 'STM32F407 开发板',        680.00
    UNION ALL SELECT 111, 40, 20, '传感器实验箱',     '实训器械',   '2024-05-08', '物联网传感器实验箱',     2600.00
    UNION ALL SELECT 112, 0,  45, '焊接工位',         '实训器械',   '2023-10-11', '白光 FX-888D 焊接工位',  1200.00
    UNION ALL SELECT 112, 45, 20, '数字示波器',       '实验仪器',   '2023-10-11', 'Tektronix TBS1104',      6500.00
    UNION ALL SELECT 113, 0,  18, 'PLC 综合实训台',   '实训器械',   '2024-01-09', '西门子 S7-1200 实训台', 26000.00
    UNION ALL SELECT 113, 18, 18, '变频器实训模块',   '实训器械',   '2024-01-09', '西门子 G120 变频模块',   8600.00
    UNION ALL SELECT 113, 36, 18, '伺服电机实训套件', '实训器械',   '2024-01-09', '松下伺服电机套件',       5400.00
    UNION ALL SELECT 114, 0,  20, '电子分析天平',     '实验仪器',   '2022-09-05', '赛多利斯 BSA224S',      12800.00
    UNION ALL SELECT 114, 20, 12, '化学通风橱',       '实验仪器',   '2022-09-05', '定制全钢通风橱',         9800.00
    UNION ALL SELECT 115, 0,   6, '六轴工业机器人',   '实训器械',   '2025-04-02', '发那科 LR Mate 200iD', 168000.00
    UNION ALL SELECT 115, 6,  20, '数字孪生工作站',   '计算机',     '2025-04-02', '联想 P3 图形工作站',    24000.00
    UNION ALL SELECT 115, 26,  4, '机器视觉检测台',   '实训器械',   '2025-04-02', '海康 Vision 检测台',    36000.00
    UNION ALL SELECT 116, 0,  30, '双目生物显微镜',   '实验仪器',   '2023-03-28', '奥林巴斯 CX23',          6800.00
    UNION ALL SELECT 116, 30,  6, '恒温培养箱',       '实验仪器',   '2023-03-28', '上海一恒 BPX-82',        7200.00
) p ON p.lab_id = l.id
JOIN (
    SELECT u.d * 10 + v.d AS n
    FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
          UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) u
    CROSS JOIN (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
                UNION ALL SELECT 8 UNION ALL SELECT 9) v
) t ON t.n BETWEEN 1 AND p.cnt
WHERE l.deleted = 0;

-- ----------------------------------------------------------------------------
-- 校验
-- ----------------------------------------------------------------------------
-- 1. 设备总数（原 32 + 新增 693 = 725）
SELECT COUNT(*) AS devices_total FROM device WHERE deleted = 0;

-- 2. 各实验室设备数应与容量/工位数相匹配
SELECT l.id AS lab_id, l.lab_name, l.capacity, COUNT(d.id) AS devices
FROM lab_info l LEFT JOIN device d ON d.lab_id = l.id AND d.deleted = 0
WHERE l.deleted = 0
GROUP BY l.id, l.lab_name, l.capacity
ORDER BY l.id;

-- 3. 设备类型分布（应全部落在前端词表内）
SELECT device_type, COUNT(*) AS cnt FROM device WHERE deleted = 0 GROUP BY device_type ORDER BY device_type;

-- 4. 设备编号唯一性（重复编号应为空）
SELECT device_code, COUNT(*) AS c FROM device GROUP BY device_code HAVING c > 1;
