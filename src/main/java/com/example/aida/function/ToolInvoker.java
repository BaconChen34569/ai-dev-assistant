package com.example.aida.function;

import java.util.Map;

/**
 * 工具调用入口（动态代理接口）。
 *
 * <p>由 {@link ToolInvokerFactory} 通过 {@code java.lang.reflect.Proxy}
 * 创建代理实例：代理在被调用时统一做日志/校验，再委派给
 * {@link ReflectiveDispatcher} 用反射真正执行目标 Bean 的方法。
 * 这样「调用入口」与「反射执行」解耦，且新增工具无需改动本接口。
 */
public interface ToolInvoker {
    Object call(String toolName, Map<String, Object> args) throws Exception;
}
