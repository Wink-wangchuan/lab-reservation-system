package com.lab.reservation.aop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志脱敏测试
 *
 * <p>验证请求参数落库前会把密码、令牌等敏感字段替换掉，
 * 避免 {@code sys_oper_log.oper_param} 出现明文凭据。</p>
 *
 * @author LabWise
 * @since 2.9.0
 */
@DisplayName("操作日志敏感字段脱敏")
class OperationLogAspectTest {

    @Test
    @DisplayName("password 字段被替换为 ******")
    void maskPassword() {
        String json = "{\"username\":\"admin\",\"password\":\"123456\"}";

        String masked = OperationLogAspect.maskSensitive(json);

        assertFalse(masked.contains("123456"));
        assertTrue(masked.contains("\"password\":\"******\""));
        assertTrue(masked.contains("\"username\":\"admin\""));
    }

    @Test
    @DisplayName("大小写不同的密码字段同样被脱敏")
    void maskCaseInsensitive() {
        String json = "{\"oldPassword\":\"old-pwd\",\"newPassword\":\"new-pwd\"}";

        String masked = OperationLogAspect.maskSensitive(json);

        assertFalse(masked.contains("old-pwd"));
        assertFalse(masked.contains("new-pwd"));
        assertTrue(masked.contains("\"oldPassword\":\"******\""));
        assertTrue(masked.contains("\"newPassword\":\"******\""));
    }

    @Test
    @DisplayName("嵌套对象与令牌字段一并脱敏")
    void maskNestedAndToken() {
        String json = "{\"dto\":{\"password\":\"p@ss\",\"realName\":\"张三\"},\"token\":\"jwt-token-value\"}";

        String masked = OperationLogAspect.maskSensitive(json);

        assertFalse(masked.contains("p@ss"));
        assertFalse(masked.contains("jwt-token-value"));
        assertTrue(masked.contains("\"realName\":\"张三\""));
        assertEquals(2, masked.split("\\*\\*\\*\\*\\*\\*", -1).length - 1);
    }

    @Test
    @DisplayName("普通参数不受影响，空值安全返回")
    void keepNormalArgs() {
        String json = "{\"labId\":1,\"status\":\"APPROVED\"}";

        assertEquals(json, OperationLogAspect.maskSensitive(json));
        assertNull(OperationLogAspect.maskSensitive(null));
        assertEquals("", OperationLogAspect.maskSensitive(""));
    }
}
