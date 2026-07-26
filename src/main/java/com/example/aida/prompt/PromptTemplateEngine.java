package com.example.aida.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态 Prompt 模板引擎：支持 {{var}} 占位符插值。
 * 设计目标：Prompt 模板与变量解耦，可配置化、可单测、可热改。
 */
@Component
public class PromptTemplateEngine {
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");

    public String render(String template, Map<String, String> vars) {
        if (template == null) return null;
        Matcher m = VAR.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = vars.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
