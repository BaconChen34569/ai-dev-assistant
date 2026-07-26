package com.example.aida.rag;

/**
 * 检索结果：命中的分片 + 余弦相似度得分。
 */
public class ScoredChunk {

    private final StoredChunk chunk;
    private final double score;

    public ScoredChunk(StoredChunk chunk, double score) {
        this.chunk = chunk;
        this.score = score;
    }

    public StoredChunk getChunk() { return chunk; }
    public double getScore() { return score; }

    /** 给前端 / 日志用的简短定位串。 */
    public String getSnippet() {
        return chunk.filePath + "#" + chunk.methodName + " (L" + chunk.startLine + "-" + chunk.endLine + ")";
    }
}
