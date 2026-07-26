package com.example.aida.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 存进 Redis 的分片结构（含向量）。Jackson 序列化用。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredChunk {

    public String id;
    public String filePath;
    public String className;
    public String methodName;
    public int startLine;
    public int endLine;
    public String language;
    public String code;
    public float[] embedding;

    public StoredChunk() {}

    public static StoredChunk from(CodeChunk c, float[] embedding) {
        StoredChunk sc = new StoredChunk();
        sc.id = c.getId();
        sc.filePath = c.getFilePath();
        sc.className = c.getClassName();
        sc.methodName = c.getMethodName();
        sc.startLine = c.getStartLine();
        sc.endLine = c.getEndLine();
        sc.language = c.getLanguage();
        sc.code = c.getCode();
        sc.embedding = embedding;
        return sc;
    }
}
