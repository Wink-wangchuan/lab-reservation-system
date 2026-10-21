package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 助手知识库条目实体
 *
 * <p>知识库是系统提示词的「静态业务知识」来源：按提问关键词检索命中条目，
 * 将正文注入 system prompt，让 AI 能准确回答预约规则、审批流程、
 * 签到核销、角色权限等业务问题，而不是靠模型自由发挥。</p>
 *
 * @author LabWise
 * @since 2.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge")
public class AiKnowledge extends BaseEntity {

    /** 知识分类：系统概览/角色权限/预约流程/审批规则/签到核销/开放时段/设备报修/消息通知/统计看板/常见问题 */
    private String category;

    /** 知识标题 */
    private String title;

    /** 触发关键词（逗号分隔，命中任意即计分） */
    private String keywords;

    /** 知识正文（命中后注入提示词） */
    private String content;

    /** 命中排序优先级：同分时越大越靠前 */
    private Integer priority;

    /** 状态：0停用 1启用 */
    private Integer status;
}
