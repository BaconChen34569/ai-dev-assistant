package com.example.aida.function;

import com.example.aida.rag.CodeChunk;
import com.example.aida.rag.CodeChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 代码检索工具：方法级分片 + 确定性子串检索。
 *
 * <p>与 RAG 向量检索的区别：本工具不依赖 LLM embedding / Redis，
 * 用纯子串匹配，检索结果完全可控、可单测——适合「按方法名精确定位」
 * 或「调试态下快速找相关片段」的场景。语义级召回交由 RAG 链路负责。
 */
@Component
public class CodeSearchTool {

    private final CodeChunker chunker;
    private final List<CodeChunk> index = new CopyOnWriteArrayList<>();
    /** 已索引的目录（去重，避免同一次/同一会话内重复全量扫描）。 */
    private final Set<String> indexedRoots = ConcurrentHashMap.newKeySet();
    private int lastIndexedCount = 0;
    /** 默认代码库目录（来自 aida.rag.codebase-path，模型未指定目录时使用）。 */
    @Value("${aida.rag.codebase-path:}")
    private String defaultCodebasePath;

    public CodeSearchTool(CodeChunker chunker) {
        this.chunker = chunker;
    }

    @AiTool(
            name = "indexCodebase",
            description = "递归索引某个目录下所有 .java 文件，建立方法级代码片段库（供 searchCode / findMethod 使用）；同一目录重复调用会被跳过",
            paramNames = "rootDir",
            paramDescriptions = "待索引的本地目录绝对路径（留空则使用默认代码库目录）")
    public String indexCodebase(String rootDir) throws IOException {
        String key = (rootDir == null || rootDir.isBlank())
                ? (defaultCodebasePath == null || defaultCodebasePath.isBlank() ? "DEFAULT" : defaultCodebasePath)
                : rootDir;
        if (key.equals("DEFAULT")) {
            return "未指定目录且未配置默认代码库路径(aida.rag.codebase-path)，跳过索引。";
        }
        if (indexedRoots.contains(key)) {
            return "已索引（" + lastIndexedCount + " 条），跳过重复全量扫描。";
        }
        Path root = Paths.get(key);
        if (!Files.exists(root)) {
            return "目录不存在: " + key;
        }
        index.clear();
        int[] count = {0};
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String src = Files.readString(p);
                        for (CodeChunk c : chunker.chunk(p.toString(), "java", src)) {
                            index.add(c);
                            count[0]++;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("索引失败: " + p, e);
                    }
                });
        indexedRoots.add(key);
        lastIndexedCount = count[0];
        return "indexed chunks: " + count[0];
    }

    @AiTool(
            name = "searchCode",
            description = "在已索引代码中按关键词子串检索相关方法片段，返回最相关的若干条",
            paramNames = {"keyword", "topK"},
            paramDescriptions = {"关键词（方法名/类名/异常类型等）", "返回条数，缺省为 5"})
    public String searchCode(String keyword, Integer topK) {
        int k = (topK == null || topK <= 0) ? 5 : topK;
        if (keyword == null || keyword.isBlank()) {
            return "keyword 不能为空";
        }
        String kw = keyword.toLowerCase();
        List<Scored> scored = new ArrayList<>();
        for (CodeChunk c : index) {
            int pos = c.getContent().toLowerCase().indexOf(kw);
            if (pos >= 0) {
                // 越靠前命中得分越高；用内容长度做轻微归一
                double score = 1.0 / (1.0 + pos) + 1.0 / (c.getContent().length() + 1);
                scored.add(new Scored(c, score));
            }
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        StringBuilder sb = new StringBuilder();
        sb.append("命中 ").append(scored.size()).append(" 条，展示前 ").append(Math.min(k, scored.size())).append(" 条:\n");
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            CodeChunk c = scored.get(i).chunk;
            sb.append("• ").append(c.getFilePath())
                    .append("#").append(c.getMethodName())
                    .append(" (L").append(c.getStartLine()).append("-").append(c.getEndLine()).append(")\n");
            sb.append(indent(c.getContent())).append("\n");
        }
        return sb.toString();
    }

    @AiTool(
            name = "findMethod",
            description = "按方法名匹配定位代码片段（支持精确 / 前缀 / 包含匹配，默认包含匹配）",
            paramNames = {"methodName", "matchMode"},
            paramDescriptions = {"要查找的方法名", "匹配模式：exact=精确, prefix=前缀, contains=包含(默认)"})
    public String findMethod(String methodName, String matchMode) {
        if (methodName == null || methodName.isBlank()) {
            return "methodName 不能为空";
        }
        String m = methodName.toLowerCase();
        String mode = (matchMode == null) ? "contains" : matchMode.toLowerCase();
        List<CodeChunk> hits = new ArrayList<>();
        for (CodeChunk c : index) {
            if (c.getMethodName() == null) continue;
            String name = c.getMethodName().toLowerCase();
            boolean match = switch (mode) {
                case "exact" -> name.equals(m);
                case "prefix" -> name.startsWith(m);
                default -> name.contains(m);
            };
            if (match) hits.add(c);
        }
        if (hits.isEmpty()) {
            return "未找到方法: " + methodName;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("命中 ").append(hits.size()).append(" 条（模式=").append(mode).append("）:\n");
        for (CodeChunk c : hits) {
            sb.append("• ").append(c.getFilePath())
                    .append("#").append(c.getMethodName())
                    .append(" (L").append(c.getStartLine()).append("-").append(c.getEndLine()).append(")\n");
            sb.append(indent(c.getContent())).append("\n");
        }
        return sb.toString();
    }

    private static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            sb.append("    ").append(line).append("\n");
        }
        return sb.toString();
    }

    private record Scored(CodeChunk chunk, double score) {
    }
}
