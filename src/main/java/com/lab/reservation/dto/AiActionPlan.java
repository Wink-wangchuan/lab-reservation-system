package com.lab.reservation.dto;

import lombok.Data;

/**
 * AI 代办指令的结构化表示
 *
 * <p>由 {@code AiIntentParser} 解析自然语言得到（优先走大模型，失败时回退规则），
 * 再由 {@code AiActionServiceImpl} 据此调用真实业务服务。</p>
 *
 * <p>注意：这里<b>只保存用户原话里的表达</b>（如「本周五」「下午两点」），
 * 具体的日期/时间换算与业务校验仍在执行器中完成，避免模型编造出不存在的时间。</p>
 *
 * @author LabWise
 * @since 3.0.0
 */
@Data
public class AiActionPlan {

    /** 预约实验室 */
    public static final String BOOK = "BOOK";
    /** 取消自己名下的预约 */
    public static final String CANCEL = "CANCEL";
    /** 审批他人预约 */
    public static final String APPROVE = "APPROVE";
    /** 提交设备报修 */
    public static final String REPAIR = "REPAIR";
    /** 非操作指令，交回普通问答 */
    public static final String NONE = "NONE";

    /** 动作类型，取值见上方常量 */
    private String action = NONE;

    /** 实验室名称或口语片段，如「第一计算机」「一机房」 */
    private String lab;

    /** 日期表达原文，如「本周五」「明天」「10月30日」 */
    private String dateText;

    /** 时段偏好原文，如「下午」「2点」「第二节」 */
    private String timeText;

    /** 使用人数，未提及为 null */
    private Integer peopleCount;

    /** 用途说明，未提及为空 */
    private String purpose;

    /** 审批/取消的目标：学生姓名或预约单号 */
    private String target;

    /** 审批决定：APPROVE / REJECT */
    private String decision;

    /** 驳回或取消原因 */
    private String reason;

    /** 设备名称或编号片段 */
    private String device;

    /** 故障描述 */
    private String faultDesc;

    /** 审批是否针对全部待审核单 */
    private Boolean all;

    /* ---------- 以下字段由执行器在澄清后回填，模型不输出 ---------- */

    /** 已确定的实验室ID */
    private Long labId;

    /** 已确定的预约ID */
    private Long reservationId;

    /** 已确定的设备ID */
    private Long deviceId;
}
