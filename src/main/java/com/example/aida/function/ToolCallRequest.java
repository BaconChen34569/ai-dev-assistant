package com.example.aida.function;

import java.util.Map;

/**
 * {@code POST /api/tool/call} 的入参。
 */
public record ToolCallRequest(String toolName, Map<String, Object> args) {
}
