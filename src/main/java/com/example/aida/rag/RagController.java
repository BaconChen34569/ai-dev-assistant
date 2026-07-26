package com.example.aida.rag;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * RAG 调试 / 演示接口（手动验证用）：
 *   POST /api/rag/index   body: {"path":"/abs/path"}  -> 索引该目录全部 .java
 *   GET  /api/rag/search  ?q=...&topK=5            -> 返回 Top-K 相关分片
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final CodeVectorStore store;

    public RagController(CodeVectorStore store) {
        this.store = store;
    }

    @PostMapping("/index")
    public Map<String, Object> index(@RequestBody Map<String, String> body) throws IOException {
        String path = body.get("path");
        if (path == null || path.isBlank()) {
            return Map.of("ok", false, "error", "缺少 path 参数");
        }
        int n = store.indexDirectory(Path.of(path));
        return Map.of("ok", true, "indexed", n, "root", path);
    }

    @GetMapping("/search")
    public List<ScoredChunk> search(@RequestParam String q,
                                     @RequestParam(defaultValue = "5") int topK) {
        return store.search(q, topK);
    }
}
