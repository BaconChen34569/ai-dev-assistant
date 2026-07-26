package com.example.aida.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为可被 Agent 调用的本地工具。
 *
 * <p>设计意图：业务工具只需在 Spring Bean 的普通方法上加 {@code @AiTool}，
 * 即可被 {@link FunctionRegistry} 通过反射自动发现并封装为 Spring AI 的 Function Calling，
 * 无需为每个工具手写请求/响应 POJO 与注册代码（动态接入）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AiTool {
    /** 工具名（Agent 侧看到的 function name），缺省用方法名。 */
    String name() default "";

    /** 工具描述，决定 LLM 是否会选择该工具。 */
    String description() default "";

    /** 参数名（显式指定，避免依赖 -parameters 编译开关）。 */
    String[] paramNames() default {};

    /** 参数描述，对应 paramNames 顺序。 */
    String[] paramDescriptions() default {};
}
