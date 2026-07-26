package com.example.aida.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方法级代码分片器（Java）。
 *
 * 采用轻量「大括号深度追踪 + 方法签名正则」策略，不依赖完整语法树，
 * 足以支撑 RAG 的片段召回。已知限制（演示可接受）：
 *   - 跨行方法签名（参数换行）不保证精准；
 *   - 匿名类 / lambda 内部的方法、内部类中的方法不保证被捕获；
 *   - 极少量「调用式」误判（如某行孤立的方法调用）理论上可能，但类体层级过滤已大幅规避。
 */
@Component
public class CodeChunker {

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public|final|abstract|sealed|non-sealed|\\s)*\\b(class|interface|enum|@interface|record)\\s+(\\w+)");

    // 方法签名（行内）：可选注解/修饰符 + 可选返回类型 + 方法名 + ( 参数 )
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*"
            + "(?:@\\w+(?:\\([^\\)]*\\))?\\s*)*"                 // 前置注解
            + "(?:public|protected|private|static|final|abstract|native|synchronized|default|strictfp|transient|volatile|\\s)*"
            + "(?!if|for|while|switch|catch|synchronized|try|do|return|throw|new|assert|else|case)" // 排除控制结构起头
            + "(?:[\\w<>,.\\[\\]\\?\\s]+?\\s+)?"                 // 可选返回类型（非贪婪）
            + "(?!if|for|while|switch|catch|return|throw|new|assert)" // 方法名前再排雷
            + "\\w+"                                              // 方法名
            + "\\s*\\([^)]*\\)"                                  // ( 参数 )
            + "\\s*(?:throws\\s+[\\w<>,.\\s]+)?"                 // 可选 throws
            + "\\s*[\\{\\;]?\\s*$");

    public List<CodeChunk> chunk(String filePath, String language, String source) {
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = source.split("\\R");
        String currentClass = null;
        int depth = 0;

        Integer methodStartLine = null;
        String methodName = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;

            if (depth == 0) {
                Matcher cm = CLASS_PATTERN.matcher(line);
                if (cm.find()) {
                    currentClass = cm.group(2);
                }
            }

            // 仅在类体层级（depth==1）且当前没有「打开中」的方法时，检测方法签名
            if (depth == 1 && methodStartLine == null) {
                Matcher mm = METHOD_PATTERN.matcher(line);
                if (mm.find()) {
                    String name = extractMethodName(line);
                    if (name != null) {
                        methodStartLine = lineNo;
                        methodName = name;
                    }
                }
            }

            int open = countChar(line, '{');
            int close = countChar(line, '}');
            depth += (open - close);

            // 方法体闭合判定（三种情形统一，避免误把方法体内 ";" 语句当闭合）：
            //   1) closedByBodyEnd：多行方法，闭合 '}' 那行 depth 回到 1 且越过起始行；
            //   2) singleLineMethod：单行方法 "int f() { return 1; }"（同行 '{' 与 '}' 抵消）；
            //   3) abstractDecl：抽象/原生方法 "void init();"（无 '{'，声明行即终）。
            if (methodStartLine != null) {
                boolean closedByBodyEnd = (depth == 1 && lineNo > methodStartLine);
                boolean singleLineMethod = (lineNo == methodStartLine && depth == 1
                        && open > 0 && close > 0 && !line.trim().startsWith("}"));
                boolean abstractDecl = (lineNo == methodStartLine && depth == 1 && !line.contains("{"));
                if (closedByBodyEnd || singleLineMethod || abstractDecl) {
                    chunks.add(build(currentClass, methodName, methodStartLine, lineNo, filePath, language, lines));
                    methodStartLine = null;
                    methodName = null;
                }
            }
        }
        return chunks;
    }

    private CodeChunk build(String currentClass, String methodName, int start, int end,
                            String filePath, String language, String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = start - 1; i <= end - 1; i++) {
            sb.append(lines[i]).append("\n");
        }
        return new CodeChunk(filePath, currentClass, methodName, start, end, language, sb.toString());
    }

    private String extractMethodName(String line) {
        String trimmed = line.trim();
        int paren = trimmed.indexOf('(');
        if (paren < 0) return null;
        String before = trimmed.substring(0, paren);
        String[] tokens = before.split("\\s+");
        for (int k = tokens.length - 1; k >= 0; k--) {
            String t = tokens[k].replaceAll("[^\\w]", "");
            if (!t.isEmpty() && !isKeyword(t)) {
                return t;
            }
        }
        return null;
    }

    private static boolean isKeyword(String t) {
        return switch (t) {
            case "if", "for", "while", "switch", "catch", "try", "do", "return",
                 "throw", "new", "assert", "synchronized", "else", "case" -> true;
            default -> false;
        };
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}
