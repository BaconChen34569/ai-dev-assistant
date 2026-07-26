package com.example.aida.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 工具调用探针：用 ThreadLocal 记录「当前诊断会话」内真实发生的工具调用，
 * 用于补全 {@link DiagnosticResponse} 的 toolTrace / rounds。
 *
 * <p>背景：Spring AI Function Calling 在 {@code chatModel.call(...)} 内部自动执行工具，
 * 手写编排层拿到的 {@code AssistantMessage.getToolCalls()} 恒为空，无法据此统计真实调用次数。
 * 因此把探针放在「工具真正执行」的唯一切面——{@link ToolInvokerFactory} 动态代理，
 * 无论框架自动执行还是手写兜底执行，都会经过这里并被记录。
 */
public final class ToolCallProbe {

    private static final ThreadLocal<LongAdder> COUNT = ThreadLocal.withInitial(LongAdder::new);
    private static final ThreadLocal<List<String>> TRACE = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<LongAdder> LLM_COUNT = ThreadLocal.withInitial(LongAdder::new);
    /** 重复调用计数：用于拦截模型对完全相同工具的无效重试（节省 token / 延迟）。 */
    private static final ThreadLocal<Map<String, Integer>> REPEAT = ThreadLocal.withInitial(HashMap::new);

    private ToolCallProbe() {
    }

    /** 每次诊断开始前重置（同一线程串行诊断，避免上一次残留）。 */
    public static void reset() {
        COUNT.get().reset();
        TRACE.get().clear();
        LLM_COUNT.get().reset();
        REPEAT.get().clear();
    }

    /** 记录一次真实工具调用。 */
    public static void record(String toolName, Object args, String result) {
        COUNT.get().increment();
        TRACE.get().add(String.format("[工具调用 #%d] %s args=%s -> %s",
                COUNT.get().intValue(), toolName, args, truncate(result, 300)));
    }

    public static int count() {
        return COUNT.get().intValue();
    }

    /** 记录一次 LLM 调用（chatModel.call），用于统计真实往返轮数。 */
    public static void markLlmCall() {
        LLM_COUNT.get().increment();
    }

    public static int llmCount() {
        return LLM_COUNT.get().intValue();
    }

    public static List<String> trace() {
        return new ArrayList<>(TRACE.get());
    }

    /**
     * 重复调用抑制：同一 key（工具名 + 入参）调用次数超过 limit 后返回 true，
     * 用于拦截模型对完全相同工具的无效重试。仅统计完全一致的调用，不影响正常探索。
     */
    public static boolean repeatExceeds(String key, int limit) {
        Map<String, Integer> m = REPEAT.get();
        int c = m.merge(key, 1, Integer::sum);
        return c > limit;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...(已截断)";
    }
}
