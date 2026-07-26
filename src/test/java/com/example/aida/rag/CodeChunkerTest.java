package com.example.aida.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker();

    @Test
    void chunksMultipleMethods() {
        String src = """
            package demo;
            public class Calc {
                private int x;
                public int add(int a, int b) {
                    return a + b;
                }
                public int sub(int a, int b) {
                    int r = a - b;
                    return r;
                }
            }
            """;
        List<CodeChunk> chunks = chunker.chunk("Calc.java", "java", src);
        assertEquals(2, chunks.size());
        assertEquals("add", chunks.get(0).getMethodName());
        assertEquals("sub", chunks.get(1).getMethodName());
        assertEquals("Calc", chunks.get(0).getClassName());
        assertEquals(4, chunks.get(0).getStartLine());
        assertEquals(6, chunks.get(0).getEndLine());
        assertTrue(chunks.get(0).getCode().contains("return a + b;"));
    }

    @Test
    void ignoresFieldWithInitializer() {
        String src = """
            public class Foo {
                private Map<String, Integer> m = new HashMap<>();
                public void run() {
                    m.clear();
                }
            }
            """;
        List<CodeChunk> chunks = chunker.chunk("Foo.java", "java", src);
        // 只应切出 run()，不应把 m = new HashMap<>() 当作方法
        assertEquals(1, chunks.size());
        assertEquals("run", chunks.get(0).getMethodName());
    }

    @Test
    void capturesConstructor() {
        String src = """
            public class Point {
                private final int x;
                public Point(int x) {
                    this.x = x;
                }
            }
            """;
        List<CodeChunk> chunks = chunker.chunk("Point.java", "java", src);
        assertEquals(1, chunks.size());
        assertEquals("Point", chunks.get(0).getMethodName());
    }

    @Test
    void capturesAbstractMethodWithoutBody() {
        String src = """
            public abstract class Base {
                public abstract void init();
                public void run() {
                    init();
                }
            }
            """;
        List<CodeChunk> chunks = chunker.chunk("Base.java", "java", src);
        assertEquals(2, chunks.size());
        assertEquals("init", chunks.get(0).getMethodName());
        assertEquals("run", chunks.get(1).getMethodName());
        assertEquals(2, chunks.get(0).getEndLine()); // 抽象方法：起止同行（init 在第 2 行）
    }
}
