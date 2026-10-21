package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 助手消息 VO
 *
 * <p>用于回显最近会话。{@code createTime} 采用已格式化的字符串，
 * 便于直接存入 Redis 后原样回显，避免时间类型序列化差异。</p>
 *
 * @author LabWise
 * @since 1.6.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 助手消息")
public class AiMessageVO {

    @Schema(description = "角色：user / assistant")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "时间（yyyy-MM-dd HH:mm:ss）")
    private String createTime;
}
