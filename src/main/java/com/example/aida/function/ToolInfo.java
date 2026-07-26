package com.example.aida.function;

/**
 * 工具的对外可读信息（不含 Bean / Method 句柄，避免 JSON 序列化泄漏内部对象）。
 */
public record ToolInfo(String name, String description, java.util.List<ParamInfo> params) {

    public record ParamInfo(String name, String type, String description) {
    }

    public static ToolInfo from(ToolDefinition def) {
        java.util.List<ParamInfo> ps = new java.util.ArrayList<>();
        for (ParamMeta pm : def.params()) {
            ps.add(new ParamInfo(pm.name(), pm.type().getSimpleName(), pm.description()));
        }
        return new ToolInfo(def.name(), def.description(), ps);
    }
}
