package com.example.aida.function;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * 动态代理工厂：用 {@code java.lang.reflect.Proxy} 创建 {@link ToolInvoker} 代理。
 *
 * <p>代理的 {@link InvocationHandler} 在每次 {@code call} 时统一做：
 * <ol>
 *   <li>日志/可观测性（记录被调用的工具名与入参）；</li>
 *   <li>委派给 {@link ReflectiveDispatcher} 完成真正的反射执行。</li>
 * </ol>
 * 这样「调用入口」与「反射执行」解耦——这正是动态代理的价值：
 * 工具调用链上可以无侵入地插入横切逻辑（日志、鉴权、限流），
 * 而 {@link ReflectiveDispatcher} 只关心「怎么反射调起来」。
 */
public final class ToolInvokerFactory {

    private ToolInvokerFactory() {
    }

    public static ToolInvoker create(Map<String, ToolDefinition> definitions) {
        ReflectiveDispatcher dispatcher = new ReflectiveDispatcher(definitions);
        InvocationHandler handler = (proxy, method, callArgs) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(dispatcher, callArgs);
            }
            // handler 仅拦截 ToolInvoker.call(String, Map)
            String toolName = (String) callArgs[0];
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) callArgs[1];
            // 重复调用抑制：同一工具 + 相同入参超过 4 次后，返回提示而非再次执行，降低 token 浪费
            String argsKey = (args == null) ? "null" : args.toString();
            if (ToolCallProbe.repeatExceeds(toolName + "#" + argsKey, 4)) {
                return "【提示】该工具调用已重复多次且结果未变，请直接基于已获取的信息定位根因，停止重试相同检索。";
            }
            // 代理侧可观测性：记录每一次工具调度
            System.out.println("[ToolInvoker] dispatch -> " + toolName + " args=" + args);
            Object result = dispatcher.dispatch(toolName, args);
            // 探针：无论框架自动执行还是手写兜底执行，真实工具调用都在此被记录
            ToolCallProbe.record(toolName, args, result == null ? "" : result.toString());
            return result;
        };
        return (ToolInvoker) Proxy.newProxyInstance(
                ToolInvokerFactory.class.getClassLoader(),
                new Class<?>[]{ToolInvoker.class},
                handler);
    }
}
