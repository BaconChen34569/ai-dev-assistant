package com.example.aida.function;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 把反射收集到的工具定义，封装为 Spring AI 的 {@link FunctionCallback} 列表，
 * 供 ChatClient 在对话中按需自动调用。
 *
 * <p>每个工具对应一个 {@code FunctionCallback}：模型产出的参数经
 * {@link ToolRequest} 承载，再交给 {@link ToolInvoker}（动态代理）做反射分发。
 * 新增一个 {@code @AiTool} 方法，这里无需任何改动即可自动生效。
 */
@Configuration
public class FunctionCallingConfig {

    @Bean
    public List<FunctionCallback> toolCallbacks(FunctionRegistry registry) {
        List<FunctionCallback> list = new ArrayList<>();
        for (ToolDefinition def : registry.getDefinitions()) {
            String name = def.name();
            FunctionCallback cb = FunctionCallback.builder()
                    .function(name, (ToolRequest req) -> {
                        try {
                            Object result = registry.getInvoker().call(name, req.getArgs());
                            return result == null ? "" : result.toString();
                        } catch (Exception e) {
                            // 工具执行异常作为可读错误返回给模型，便于其自我纠正
                            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                        }
                    })
                    .description(def.description())
                    .inputType(ToolRequest.class)
                    .responseConverter(r -> r == null ? "" : r.toString())
                    .build();
            list.add(cb);
        }
        return list;
    }
}
