package com.example.aida.function;

import java.lang.reflect.Method;

/**
 * 一个可调用工具的完整定义：名称、描述、参数元信息，以及反射调用所需的
 * 目标 Bean 与方法句柄。
 *
 * <p>注意：{@link #bean()} 与 {@link #method()} 不参与 JSON 序列化，
 * 仅在进程内反射分发时使用（见 {@link ReflectiveDispatcher}）。
 */
public record ToolDefinition(
        String name,
        String description,
        java.util.List<ParamMeta> params,
        Object bean,
        Method method) {
}
