package com.example.aida.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 可选启动索引器：仅当配置了 aida.rag.codebase-path 时才执行，
 * 否则直接跳过，避免在 CI / 单测 / 无 Key 环境下发起无谓的 embedding 调用。
 */
@Component
@Order(1)
public class RagIndexStartup implements CommandLineRunner {

    private final CodeVectorStore store;
    private final String codebasePath;

    public RagIndexStartup(CodeVectorStore store,
                           @Value("${aida.rag.codebase-path:}") String codebasePath) {
        this.store = store;
        this.codebasePath = codebasePath;
    }

    @Override
    public void run(String... args) throws Exception {
        if (codebasePath == null || codebasePath.isBlank()) {
            return;
        }
        int n = store.indexDirectory(Path.of(codebasePath));
        System.out.println("[RAG] 启动索引完成，共 " + n + " 个方法分片 <- " + codebasePath);
    }
}
