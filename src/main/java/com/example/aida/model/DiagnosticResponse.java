package com.example.aida.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosticResponse {
    private String exceptionType;
    private String message;
    private String rootFrame;
    private String packageHint;
    private String builtPrompt;   // 已组装给 LLM 的 Prompt（调试 / 可解释用）
    private String diagnosis;      // LLM 诊断结论（链路接入后填充）
    private java.util.List<String> toolTrace; // Agent 多轮工具调用轨迹（可观测）
    private int rounds;            // LLM 往返轮数（含工具轮与收尾轮），供评测统计
    private String note;          // 状态说明
}
