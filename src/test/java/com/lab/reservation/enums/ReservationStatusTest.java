package com.lab.reservation.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约状态机单元测试
 *
 * <p>状态机是预约业务的核心约束，任何非法跳转（如 已驳回 → 已通过）都必须在
 * Service 层之前被拦截，因此这里对全部合法/非法组合做穷举断言。</p>
 *
 * @author LabWise
 * @since 2.3.0
 */
@DisplayName("预约状态机 ReservationStatus")
class ReservationStatusTest {

    @Nested
    @DisplayName("合法流转")
    class AllowedTransitions {

        @Test
        @DisplayName("草稿 → 待审核 / 已取消")
        void draft() {
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.DRAFT, ReservationStatus.PENDING));
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.DRAFT, ReservationStatus.CANCELLED));
        }

        @Test
        @DisplayName("待审核 → 已通过 / 已驳回 / 已取消")
        void pending() {
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.PENDING, ReservationStatus.APPROVED));
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.PENDING, ReservationStatus.REJECTED));
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.PENDING, ReservationStatus.CANCELLED));
        }

        @Test
        @DisplayName("已通过 → 已完成 / 已取消")
        void approved() {
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.APPROVED, ReservationStatus.COMPLETED));
            assertTrue(ReservationStatus.canTransition(
                    ReservationStatus.APPROVED, ReservationStatus.CANCELLED));
        }
    }

    @Nested
    @DisplayName("非法流转")
    class ForbiddenTransitions {

        @Test
        @DisplayName("终态不可再流转")
        void terminalStates() {
            for (ReservationStatus terminal : new ReservationStatus[]{
                    ReservationStatus.REJECTED,
                    ReservationStatus.COMPLETED,
                    ReservationStatus.CANCELLED}) {
                for (ReservationStatus target : ReservationStatus.values()) {
                    assertFalse(ReservationStatus.canTransition(terminal, target),
                            terminal + " 是终态，不应允许流转到 " + target);
                }
            }
        }

        @Test
        @DisplayName("已通过不能被再次审批")
        void cannotReapprove() {
            assertFalse(ReservationStatus.canTransition(
                    ReservationStatus.APPROVED, ReservationStatus.APPROVED));
            assertFalse(ReservationStatus.canTransition(
                    ReservationStatus.APPROVED, ReservationStatus.REJECTED));
        }

        @Test
        @DisplayName("待审核不能直接完成")
        void pendingCannotComplete() {
            assertFalse(ReservationStatus.canTransition(
                    ReservationStatus.PENDING, ReservationStatus.COMPLETED));
        }

        @Test
        @DisplayName("草稿不能直接通过或完成")
        void draftCannotJump() {
            assertFalse(ReservationStatus.canTransition(
                    ReservationStatus.DRAFT, ReservationStatus.APPROVED));
            assertFalse(ReservationStatus.canTransition(
                    ReservationStatus.DRAFT, ReservationStatus.COMPLETED));
        }
    }

    @Nested
    @DisplayName("边界与解析")
    class EdgeCases {

        @Test
        @DisplayName("null 参数返回 false 而不是抛 NPE")
        void nullSafe() {
            assertFalse(ReservationStatus.canTransition(null, ReservationStatus.PENDING));
            assertFalse(ReservationStatus.canTransition(ReservationStatus.PENDING, null));
            assertFalse(ReservationStatus.canTransition(null, null));
        }

        @Test
        @DisplayName("字符串解析忽略大小写与空白")
        void parse() {
            assertEquals(ReservationStatus.PENDING, ReservationStatus.of(" pending "));
            assertEquals(ReservationStatus.APPROVED, ReservationStatus.of("APPROVED"));
        }

        @Test
        @DisplayName("非法状态串解析为 null")
        void parseInvalid() {
            assertNull(ReservationStatus.of("UNKNOWN_STATUS"));
            assertNull(ReservationStatus.of(null));
        }

        @Test
        @DisplayName("中文描述不为空，供前端展示")
        void descriptions() {
            for (ReservationStatus status : ReservationStatus.values()) {
                assertTrue(status.getDesc() != null && !status.getDesc().isBlank());
            }
        }
    }

    @Test
    @DisplayName("inTransitionsFrom 只返回合法目标")
    void inTransitionsFrom() {
        assertEquals(1, ReservationStatus.inTransitionsFrom(
                ReservationStatus.PENDING, ReservationStatus.APPROVED).size());
        assertTrue(ReservationStatus.inTransitionsFrom(
                ReservationStatus.REJECTED, ReservationStatus.APPROVED).isEmpty());
    }

    @Test
    @DisplayName("Set.of(...).contains(null) 确实会抛异常，验证判空必要性")
    void documentNpeRisk() {
        assertThrows(NullPointerException.class,
                () -> java.util.Set.of(ReservationStatus.PENDING).contains(null));
    }
}
