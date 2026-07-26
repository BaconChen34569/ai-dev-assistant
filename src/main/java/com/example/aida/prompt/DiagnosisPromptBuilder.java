package com.example.aida.prompt;

import com.example.aida.model.ExceptionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于模板引擎组装诊断 Prompt：把异常上下文 + RAG 召回片段注入固定模板。
 * 模板可配置化，后期调优只需改模板，不改代码。
 */
@Component
public class DiagnosisPromptBuilder {
    private final PromptTemplateEngine engine;

    public DiagnosisPromptBuilder(PromptTemplateEngine engine) {
        this.engine = engine;
    }

    public String build(ExceptionContext ctx, String ragContext, int round) {
        String tpl = """
                你是一名资深 Java 工程师。请诊断下列异常，给出根因与修复建议。

                ## 异常类型
                {{exceptionType}}

                ## 异常信息
                {{message}}

                ## 关键调用栈（顶层用户代码）
                {{rootFrame}}

                ## 相关源代码（RAG 召回，仅供参考）
                {{rag}}

                ## 要求
                1. 指出最可能的根因（文件:行号 / 方法）。
                2. 给出具体修复代码片段。
                3. 使用中文，简洁。""";
        Map<String, String> vars = new HashMap<>();
        vars.put("exceptionType", ctx.getShortType() + " (" + ctx.getExceptionType() + ")");
        vars.put("message", ctx.getMessage() == null ? "" : ctx.getMessage());
        vars.put("rootFrame", ctx.getRootFrame() == null ? "" : ctx.getRootFrame().getRaw());
        vars.put("rag", ragContext == null ? "" : ragContext);
        return engine.render(tpl, vars);
    }
}
