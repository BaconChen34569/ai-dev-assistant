package com.example.aida.function;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 反射分发器：把「工具名 + 参数 Map」映射到具体 Bean 方法的强类型调用。
 *
 * <p>这是「Java 反射 + 动态代理」中反射一侧的核心：
 * 不依赖任何硬编码的工具实现类，而是依据 {@link ToolDefinition} 中通过
 * 反射收集的方法句柄与参数元信息，完成「字符串参数 → 方法实参」的类型转换
 * 与方法调用。新增工具时，这里一行都不用改。
 */
public class ReflectiveDispatcher {

    private final Map<String, ToolDefinition> definitions;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 参数名别名表：LLM 常自由填写 {@code name / query / method / file} 等近义 key，
     * 而工具期望的形参名是 {@code methodName / keyword / path}。这里做容错映射，
     * 避免「methodName 不能为空」这类因命名不匹配导致的工具空返回。
     */
    private static final Map<String, List<String>> ALIASES = Map.of(
            "methodName", List.of("methodName", "name", "method", "methods", "method_name",
                    "methodname", "func", "funcName", "function", "functionName"),
            "keyword", List.of("keyword", "keywords", "query", "search", "searchTerm",
                    "term", "q", "text", "kw"),
            "topK", List.of("topK", "top_k", "k", "limit", "count", "n", "num"),
            "rootDir", List.of("rootDir", "dir", "directory", "path", "root",
                    "rootdir", "codebase", "codebasePath"),
            "path", List.of("path", "filePath", "file", "filename", "file_path", "p", "location"),
            "content", List.of("content", "text", "data", "body", "contents")
    );

    public ReflectiveDispatcher(Map<String, ToolDefinition> definitions) {
        this.definitions = definitions;
    }

    public Object dispatch(String toolName, Map<String, Object> args) throws Exception {
        ToolDefinition def = definitions.get(toolName);
        if (def == null) {
            throw new IllegalArgumentException("未知工具: " + toolName);
        }
        Method method = def.method();
        Class<?>[] paramTypes = method.getParameterTypes();
        List<ParamMeta> params = def.params();

        Object[] callArgs = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            ParamMeta pm = params.get(i);
            Object raw = resolveArg(args, pm.name());
            // Jackson convertValue 负责把 LLM 给来的宽松类型（Integer/Double/String）转成方法形参类型
            callArgs[i] = mapper.convertValue(raw, paramTypes[i]);
        }
        // 反射调用目标 Bean 的真实方法
        return method.invoke(def.bean(), callArgs);
    }

    /**
     * 按形参名取值，缺失时回退到别名表（LLM 自由命名的兼容层）。
     */
    private static Object resolveArg(Map<String, Object> args, String primary) {
        if (args == null) return null;
        Object v = args.get(primary);
        if (v != null) return v;
        List<String> aliases = ALIASES.get(primary);
        if (aliases != null) {
            for (String alias : aliases) {
                Object av = args.get(alias);
                if (av != null) return av;
            }
        }
        // LLM 偶发把参数整体再包一层 "args"（如 {"args":{"methodName":"x"}}），
        // 解包内层再按同样的 primary/别名取一次，避免 methodName 取到 null 而返回"不能为空"
        Object wrapped = args.get("args");
        if (wrapped instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = (Map<String, Object>) wrapped;
            Object fromInner = resolveArg(inner, primary);
            if (fromInner != null) return fromInner;
        }
        // 兜底：模型把方法名+类塞进一个字符串（如 "args":"read MapReader.java:61"），
        // 直接返回该字符串交由工具做"未找到"软失败，而非 null 触发"不能为空"
        if (wrapped instanceof String) {
            return wrapped;
        }
        return null;
    }
}
