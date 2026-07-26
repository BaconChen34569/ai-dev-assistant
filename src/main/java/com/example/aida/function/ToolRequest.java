package com.example.aida.function;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Function Calling 的统一入参载体。
 *
 * <p>LLM 产出的 JSON 参数被反序列化为本对象；真正的参数位于 {@link #args} 中。
 * 分发层（{@link ReflectiveDispatcher}）再按各工具的反射元信息将 {@code args}
 * 还原为强类型实参。这是一种「动态契约」：所有工具共用同一入参形状，
 * 差异由反射元信息描述，而非为每工具定义独立类。
 */
public class ToolRequest {

    @JsonProperty("args")
    private Map<String, Object> args = new HashMap<>();

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args == null ? new HashMap<>() : args;
    }
}
