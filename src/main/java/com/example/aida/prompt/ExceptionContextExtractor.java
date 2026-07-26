package com.example.aida.prompt;

import com.example.aida.model.ExceptionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 针对 Java 异常堆栈做结构化上下文提取：
 *  - 异常类型 / 信息
 *  - 解析 at ... 调用栈，提取 类 / 方法 / 文件 / 行号
 *  - 推断"业务包名"（首条非 JDK 帧的包），用于后续 RAG 定向检索
 */
@Component
public class ExceptionContextExtractor {
    private static final Pattern HEAD = Pattern.compile("^(\\S+)(?::\\s*(.*))?$");
    private static final Pattern THREAD_PREFIX = Pattern.compile("^Exception in thread \"[^\"]*\"\\s+(.*)$");
    private static final Pattern FRAME = Pattern.compile(
            "\\s+at\\s+([\\w.$]+)\\.([\\w$<>,]+)\\(([^):]*)(?::(-?\\d+))?\\)");
    private static final List<String> JDK_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "java.base/", "sun.", "org.springframework.", "com.sun.");

    public ExceptionContext extract(String trace) {
        if (trace == null || trace.isBlank()) {
            throw new IllegalArgumentException("stack trace must not be empty");
        }
        String[] lines = trace.split("\\R");
        ExceptionContext.ExceptionContextBuilder b = ExceptionContext.builder().rawTrace(trace);

        String head = lines[0].trim();
        Matcher tm = THREAD_PREFIX.matcher(head);
        if (tm.find()) head = tm.group(1).trim();

        Matcher hm = HEAD.matcher(head);
        if (hm.find()) {
            String type = hm.group(1);
            b.exceptionType(type);
            String[] parts = type.split("\\.");
            b.shortType(parts[parts.length - 1]);
            b.message(hm.group(2) == null ? "" : hm.group(2).trim());
        }

        List<ExceptionContext.StackFrame> frames = new ArrayList<>();
        String pkgHint = null;
        for (int i = 1; i < lines.length; i++) {
            Matcher fm = FRAME.matcher(lines[i]);
            if (!fm.find()) continue;
            ExceptionContext.StackFrame f = ExceptionContext.StackFrame.builder()
                    .declaringClass(fm.group(1))
                    .methodName(fm.group(2))
                    .fileName(fm.group(3))
                    .lineNumber(fm.group(4) == null ? -1 : Integer.parseInt(fm.group(4)))
                    .raw(lines[i].trim())
                    .build();
            frames.add(f);
            if (pkgHint == null && !isJdk(f.getDeclaringClass()) && f.getDeclaringClass().contains(".")) {
                pkgHint = f.getDeclaringClass().substring(0, f.getDeclaringClass().lastIndexOf('.'));
            }
        }
        b.frames(frames).rootFrame(frames.isEmpty() ? null : frames.get(0)).packageHint(pkgHint);
        return b.build();
    }

    private boolean isJdk(String declaringClass) {
        return JDK_PREFIXES.stream().anyMatch(declaringClass::startsWith);
    }
}
