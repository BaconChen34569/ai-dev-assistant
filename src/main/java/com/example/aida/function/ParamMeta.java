package com.example.aida.function;

/**
 * 工具参数元信息（由反射从 {@link AiTool} 方法提取）。
 */
public record ParamMeta(String name, Class<?> type, String description) {
}
