package com.example.aida.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeVectorStoreTest {

    private StoredChunk chunk(String id, float[] vec) {
        StoredChunk c = new StoredChunk();
        c.id = id;
        c.filePath = id + ".java";
        c.methodName = id;
        c.embedding = vec;
        return c;
    }

    @Test
    void cosineIdenticalIsOne() {
        float[] v = {1, 2, 3};
        assertEquals(1.0, CodeVectorStore.cosine(v, v), 1e-9);
    }

    @Test
    void cosineOrthogonalIsZero() {
        assertEquals(0.0, CodeVectorStore.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-9);
    }

    @Test
    void rankReturnsTopKBySimilarity() {
        float[] q = {1, 0, 0};
        List<StoredChunk> candidates = List.of(
                chunk("far", new float[]{0, 1, 0}),
                chunk("near", new float[]{1, 0, 0}),
                chunk("mid", new float[]{0.7f, 0.7f, 0})
        );
        List<ScoredChunk> top = CodeVectorStore.rank(q, candidates, 2);
        assertEquals(2, top.size());
        assertEquals("near", top.get(0).getChunk().methodName);
        assertEquals("mid", top.get(1).getChunk().methodName);
        assertTrue(top.get(0).getScore() > top.get(1).getScore());
    }

    @Test
    void rankRespectsTopKLimit() {
        float[] q = {1, 0};
        List<StoredChunk> candidates = List.of(
                chunk("a", new float[]{1, 0}),
                chunk("b", new float[]{0, 1}),
                chunk("c", new float[]{1, 1})
        );
        List<ScoredChunk> top = CodeVectorStore.rank(q, candidates, 1);
        assertEquals(1, top.size());
        assertEquals("a", top.get(0).getChunk().methodName);
    }
}
