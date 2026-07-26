package com.example.aida.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 代码向量库：方法级分片 -> 向量化 -> 存 Redis(db2) -> 应用层余弦检索。
 *
 * 设计要点（与简历措辞一致）：
 *   - embedding 以普通 JSON 数据存于 Redis，不依赖 Redis 向量类型，任意版本可用；
 *   - 余弦相似度在应用层（本类）计算，召回 Top-K。
 */
@Service
public class CodeVectorStore {

    private static final String INDEX_KEY = "rag:index";
    private static final String CHUNK_KEY_PREFIX = "rag:chunk:";

    private final StringRedisTemplate redis;
    private final EmbeddingService embeddingService;
    private final CodeChunker chunker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeVectorStore(StringRedisTemplate redis, EmbeddingService embeddingService, CodeChunker chunker) {
        this.redis = redis;
        this.embeddingService = embeddingService;
        this.chunker = chunker;
    }

    /** 将单个分片向量化并写入 Redis（db2）。 */
    public void save(CodeChunk chunk) {
        try {
            float[] vec = embeddingService.embed(chunk.getContent());
            StoredChunk sc = StoredChunk.from(chunk, vec);
            String json = objectMapper.writeValueAsString(sc);
            String key = CHUNK_KEY_PREFIX + chunk.getId();
            redis.opsForValue().set(key, json);
            redis.opsForSet().add(INDEX_KEY, key);
        } catch (IOException e) {
            throw new RuntimeException("序列化分片失败: " + chunk.getId(), e);
        }
    }

    /** 递归索引某目录下的全部 .java 文件，返回分片总数。 */
    public int indexDirectory(Path root) throws IOException {
        if (!Files.exists(root)) return 0;
        int[] count = {0};
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        for (CodeChunk c : chunkFile(p)) {
                            save(c);
                            count[0]++;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("索引失败: " + p, e);
                    }
                });
        return count[0];
    }

    public List<CodeChunk> chunkFile(Path p) throws IOException {
        String src = Files.readString(p);
        return chunker.chunk(p.toString(), "java", src);
    }

    /** 按查询文本检索 Top-K 相关代码片段（余弦相似度，应用层计算）。 */
    public List<ScoredChunk> search(String query, int topK) {
        float[] q = embeddingService.embed(query);
        Set<String> keys = redis.opsForSet().members(INDEX_KEY);
        if (keys == null || keys.isEmpty()) return List.of();
        List<StoredChunk> all = new ArrayList<>();
        for (String k : keys) {
            String v = redis.opsForValue().get(k);
            if (v != null) {
                try {
                    all.add(objectMapper.readValue(v, StoredChunk.class));
                } catch (IOException ignored) {
                    // 损坏条目跳过
                }
            }
        }
        return rank(q, all, topK);
    }

    // ===== 纯函数：可直接单元测试，无需 Redis / LLM =====

    public static List<ScoredChunk> rank(float[] queryVec, List<StoredChunk> candidates, int topK) {
        List<ScoredChunk> scored = new ArrayList<>();
        for (StoredChunk c : candidates) {
            double sim = cosine(queryVec, c.embedding);
            scored.add(new ScoredChunk(c, sim));
        }
        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return scored.stream().limit(topK).collect(Collectors.toList());
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
