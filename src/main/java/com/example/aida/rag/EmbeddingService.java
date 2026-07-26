package com.example.aida.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * 文本向量化封装。底层用 Spring AI 的 EmbeddingModel（本项目接智谱 embedding-3）。
 * 单测中可用 @MockBean 替换，避免真实网络调用。
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        // Spring AI 的 EmbeddingModel 自带 embed(String) 便利方法
        return embeddingModel.embed(text);
    }
}
