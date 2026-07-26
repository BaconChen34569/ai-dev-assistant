package com.example.aida.function;

import com.example.aida.rag.CodeChunker;
import com.example.aida.function.CodeSearchTool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Function Calling 链路测试（纯本地，无需 LLM / Redis）：
 * 验证「反射扫描 @AiTool → 动态代理分发 → 真实方法执行」整条链路。
 */
class FunctionCallingTest {

    static Path sandbox;
    static AnnotationConfigApplicationContext ctx;

    @BeforeAll
    static void setup() throws IOException {
        sandbox = Files.createTempDirectory("aida-fc");
        Files.writeString(sandbox.resolve("hello.txt"), "hello world");
        ctx = new AnnotationConfigApplicationContext(Cfg.class);
    }

    @AfterAll
    static void teardown() {
        ctx.close();
    }

    @Test
    void scansAllAnnotatedTools() {
        FunctionRegistry reg = ctx.getBean(FunctionRegistry.class);
        List<String> names = reg.getDefinitions().stream().map(ToolDefinition::name).toList();
        assertTrue(names.containsAll(List.of(
                "readFile", "writeFile", "listDirectory",
                "indexCodebase", "searchCode", "findMethod")),
                "应自动发现全部 6 个工具，实际: " + names);
    }

    @Test
    void proxyDispatchRoutesToCorrectMethod() throws Exception {
        ToolInvoker invoker = ctx.getBean(FunctionRegistry.class).getInvoker();

        Object written = invoker.call("writeFile",
                Map.of("path", "out.txt", "content", "ping"));
        assertTrue(written.toString().contains("written"), "应写入文件");

        Object read = invoker.call("readFile", Map.of("path", "out.txt"));
        assertEquals("ping", read, "反射分发应正确把 content 写回并读回");
    }

    @Test
    void typeConversionFromMapArgs() throws Exception {
        // searchCode 第二参为 Integer，验证 Map 里的数值被反射还原为强类型
        CodeSearchTool cs = ctx.getBean(CodeSearchTool.class);
        Path src = sandbox.resolve("Sample.java");
        Files.writeString(src,
                "public class Sample {\n"
                        + "    public void saveUser() {\n"
                        + "        int x = 1;\n"
                        + "    }\n"
                        + "    public void load() {\n"
                        + "    }\n"
                        + "}\n");
        cs.indexCodebase(sandbox.toString());

        ToolInvoker invoker = ctx.getBean(FunctionRegistry.class).getInvoker();
        Object r = invoker.call("searchCode", Map.of("keyword", "saveUser", "topK", 3));
        assertTrue(r.toString().contains("saveUser"), "应检索到 saveUser 片段");
    }

    @Test
    void sandboxBlocksPathEscape() {
        ToolInvoker invoker = ctx.getBean(FunctionRegistry.class).getInvoker();
        Exception ex = assertThrows(Exception.class,
                () -> invoker.call("readFile", Map.of("path", "../../etc/passwd")));
        Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
        assertTrue(cause instanceof SecurityException,
                "沙箱越权访问应被拒绝，实际抛出: " + cause);
    }

    @Test
    void generatedSchemaMatchesSignature() {
        FunctionRegistry reg = ctx.getBean(FunctionRegistry.class);
        ToolDefinition readFile = reg.getDefinitions().stream()
                .filter(d -> d.name().equals("readFile")).findFirst().orElseThrow();
        String schema = JsonSchemaGenerator.build(readFile);
        assertTrue(schema.contains("\"path\""), "schema 应含参数 path");
        assertTrue(schema.contains("\"type\":\"string\""), "path 应为 string");

        ToolDefinition search = reg.getDefinitions().stream()
                .filter(d -> d.name().equals("searchCode")).findFirst().orElseThrow();
        String sSchema = JsonSchemaGenerator.build(search);
        assertTrue(sSchema.contains("\"topK\""), "schema 应含参数 topK");
        assertTrue(sSchema.contains("\"type\":\"integer\""), "topK 应为 integer");
    }

    @Test
    void reflectiveDispatcherBindsByName() throws Exception {
        // 直接验证反射分发器：未知工具名应报错
        FunctionRegistry reg = ctx.getBean(FunctionRegistry.class);
        ToolInvoker invoker = reg.getInvoker();
        Exception ex = assertThrows(Exception.class,
                () -> invoker.call("notExist", Map.of()));
        assertTrue(ex.getMessage().contains("未知工具"), "未知工具应被识别");
    }

    @Configuration
    static class Cfg {
        @Bean
        LocalFileTool localFileTool() {
            return new LocalFileTool(sandbox.toString());
        }

        @Bean
        CodeChunker codeChunker() {
            return new CodeChunker();
        }

        @Bean
        CodeSearchTool codeSearchTool(CodeChunker chunker) {
            return new CodeSearchTool(chunker);
        }

        @Bean
        FunctionRegistry functionRegistry(ApplicationContext appCtx) {
            return new FunctionRegistry(appCtx);
        }
    }
}
