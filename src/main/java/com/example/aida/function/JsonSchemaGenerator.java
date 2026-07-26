package com.example.aida.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 依据反射得到的参数元信息，生成 Function Calling 的 JSON Schema。
 *
 * <p>这是「动态」的另一半：LLM 需要的入参结构不再由手写 POJO 决定，
 * 而是由方法的真实签名在运行时推导出来，保证 schema 与实际可调用签名始终一致。
 */
public final class JsonSchemaGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemaGenerator() {
    }

    public static String build(ToolDefinition def) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ArrayNode required = root.putArray("required");
        for (ParamMeta p : def.params()) {
            ObjectNode pNode = properties.putObject(p.name());
            pNode.put("type", jsonType(p.type()));
            if (p.description() != null && !p.description().isEmpty()) {
                pNode.put("description", p.description());
            }
            required.add(p.name());
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"type\":\"object\"}";
        }
    }

    private static String jsonType(Class<?> t) {
        if (t == int.class || t == Integer.class
                || t == long.class || t == Long.class
                || t == short.class || t == Short.class) {
            return "integer";
        }
        if (t == double.class || t == Double.class
                || t == float.class || t == Float.class) {
            return "number";
        }
        if (t == boolean.class || t == Boolean.class) {
            return "boolean";
        }
        return "string";
    }
}
