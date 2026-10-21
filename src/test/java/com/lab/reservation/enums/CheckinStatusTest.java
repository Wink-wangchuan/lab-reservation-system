package com.lab.reservation.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签到核销状态机单元测试
 *
 * @author LabWise
 * @since 2.3.0
 */
@DisplayName("签到状态机 CheckinStatus")
class CheckinStatusTest {

    @Nested
    @DisplayName("合法流转")
    class AllowedTransitions {

        @Test
        @DisplayName("待签到 → 已签到 / 已过期 / 已取消")
        void signInPending() {
            assertTrue(CheckinStatus.canTransition(
                    CheckinStatus.SIGN_IN_PENDING, CheckinStatus.CHECKED_IN));
            assertTrue(CheckinStatus.canTransition(
                    CheckinStatus.SIGN_IN_PENDING, CheckinStatus.EXPIRED));
            assertTrue(CheckinStatus.canTransition(
                    CheckinStatus.SIGN_IN_PENDING, CheckinStatus.CANCELLED));
        }

        @Test
        @DisplayName("已签到 → 已完成 / 已取消")
        void checkedIn() {
            assertTrue(CheckinStatus.canTransition(
                    CheckinStatus.CHECKED_IN, CheckinStatus.COMPLETED));
            assertTrue(CheckinStatus.canTransition(
                    CheckinStatus.CHECKED_IN, CheckinStatus.CANCELLED));
        }
    }

    @Nested
    @DisplayName("非法流转")
    class ForbiddenTransitions {

        @Test
        @DisplayName("终态不可再流转")
        void terminalStates() {
            for (CheckinStatus terminal : new CheckinStatus[]{
                    CheckinStatus.EXPIRED,
                    CheckinStatus.COMPLETED,
                    CheckinStatus.CANCELLED}) {
                assertTrue(terminal.isTerminal());
                for (CheckinStatus target : CheckinStatus.values()) {
                    assertFalse(CheckinStatus.canTransition(terminal, target),
                            terminal + " 是终态，不应允许流转到 " + target);
                }
            }
        }

        @Test
        @DisplayName("已过期不能重新签到")
        void expiredCannotCheckin() {
            assertFalse(CheckinStatus.canTransition(
                    CheckinStatus.EXPIRED, CheckinStatus.CHECKED_IN));
        }

        @Test
        @DisplayName("待签到不能直接完成")
        void pendingCannotComplete() {
            assertFalse(CheckinStatus.canTransition(
                    CheckinStatus.SIGN_IN_PENDING, CheckinStatus.COMPLETED));
        }
    }

    @Nested
    @DisplayName("边界与解析")
    class EdgeCases {

        @Test
        @DisplayName("null 参数返回 false 而不是抛 NPE")
        void nullSafe() {
            assertFalse(CheckinStatus.canTransition(null, CheckinStatus.CHECKED_IN));
            assertFalse(CheckinStatus.canTransition(CheckinStatus.SIGN_IN_PENDING, null));
            assertFalse(CheckinStatus.canTransition(null, null));
        }

        @Test
        @DisplayName("字符串解析忽略大小写，非法值返回 null")
        void parse() {
            assertEquals(CheckinStatus.CHECKED_IN, CheckinStatus.of(" checked_in "));
            assertNull(CheckinStatus.of("NOT_A_STATUS"));
            assertNull(CheckinStatus.of(null));
        }

        @Test
        @DisplayName("非终态标记正确")
        void terminalFlag() {
            assertFalse(CheckinStatus.SIGN_IN_PENDING.isTerminal());
            assertFalse(CheckinStatus.CHECKED_IN.isTerminal());
        }
    }
}
