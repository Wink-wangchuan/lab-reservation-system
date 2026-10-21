package com.lab.reservation.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报修状态机单元测试
 *
 * @author LabWise
 * @since 2.3.0
 */
@DisplayName("报修状态机 RepairStatus")
class RepairStatusTest {

    @Test
    @DisplayName("待处理 → 维修中 / 已取消")
    void pending() {
        assertTrue(RepairStatus.canTransition(RepairStatus.PENDING, RepairStatus.REPAIRING));
        assertTrue(RepairStatus.canTransition(RepairStatus.PENDING, RepairStatus.CANCELLED));
    }

    @Test
    @DisplayName("维修中 → 已完成 / 已取消")
    void repairing() {
        assertTrue(RepairStatus.canTransition(RepairStatus.REPAIRING, RepairStatus.COMPLETED));
        assertTrue(RepairStatus.canTransition(RepairStatus.REPAIRING, RepairStatus.CANCELLED));
    }

    @Test
    @DisplayName("不能从待处理直接完成，也不能跳过维修中")
    void cannotSkip() {
        assertFalse(RepairStatus.canTransition(RepairStatus.PENDING, RepairStatus.COMPLETED));
    }

    @Test
    @DisplayName("终态不可再流转")
    void terminal() {
        for (RepairStatus terminal : new RepairStatus[]{
                RepairStatus.COMPLETED, RepairStatus.CANCELLED}) {
            for (RepairStatus target : RepairStatus.values()) {
                assertFalse(RepairStatus.canTransition(terminal, target));
            }
        }
    }

    @Test
    @DisplayName("null 参数返回 false")
    void nullSafe() {
        assertFalse(RepairStatus.canTransition(null, RepairStatus.REPAIRING));
        assertFalse(RepairStatus.canTransition(RepairStatus.PENDING, null));
    }

    @Test
    @DisplayName("字符串解析")
    void parse() {
        assertEquals(RepairStatus.REPAIRING, RepairStatus.of(" repairing "));
        assertNull(RepairStatus.of("BAD"));
        assertNull(RepairStatus.of(null));
    }
}
