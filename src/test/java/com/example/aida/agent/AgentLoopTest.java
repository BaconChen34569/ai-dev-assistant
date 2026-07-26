package com.example.aida.agent;

import com.example.aida.function.CodeSearchTool;
import com.example.aida.function.FunctionRegistry;
import com.example.aida.function.LocalFileTool;
import com.example.aida.model.DiagnosticResponse;
import com.example.aida.prompt.DiagnosisPromptBuilder;
import com.example.aida.prompt.ExceptionContextExtractor;
import com.example.aida.prompt.PromptTemplateEngine;
import com.example.aida.rag.CodeChunker;
import com.example.aida.rag.CodeVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 多轮工具调用编排测试（无需真实 LLM Key）：
 * 用 Mockito 模拟 ChatModel —— 第一轮返回含 ToolCall 的 AssistantMessage，
 * 第二轮返回最终结论。校验「模型请求工具 → 本地反射执行 → 结果回填 → 收尾」整条链路。
 */
class AgentLoopTest {

    @Test
    void agentCallsToolThenReturnsFinalDiagnosis() throws IOException {
        // ---- 准备：真实工具（searchCode 走反射执行，不依赖 LLM / Redis）----
        Path sandbox = Files.createTempDirectory("aida-agent");
        Files.writeString(sandbox.resolve("Sample.java"),
                "public class Sample {\n"
                        + "    public void saveUser() {\n"
                        + "        int x = 1;\n"
                        + "    }\n"
                        + "    public void load() {\n"
                        + "    }\n"
                        + "}\n");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class);
        FunctionRegistry registry = ctx.getBean(FunctionRegistry.class);
        ctx.getBean(CodeSearchTool.class).indexCodebase(sandbox.toString());

        ExceptionContextExtractor extractor = new ExceptionContextExtractor();
        DiagnosisPromptBuilder promptBuilder =
                new DiagnosisPromptBuilder(new PromptTemplateEngine());

        // ---- Mock：RAG 检索返回空（无 Key / 未索引时降级）；LLM 两轮交互 ----
        CodeVectorStore vectorStore = mock(CodeVectorStore.class);
        when(vectorStore.search(anyString(), anyInt())).thenReturn(List.of());

        ChatModel chatModel = mock(ChatModel.class);

        // 第一轮：模型决定调用 searchCode(keyword=saveUser, topK=3)
        AssistantMessage toolAsk = new AssistantMessage("",
                Map.of(),
                List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "searchCode",
                        "{\"keyword\":\"saveUser\",\"topK\":3}")));
        ChatResponse resp1 = new ChatResponse(List.of(new Generation(toolAsk)));

        // 第二轮：模型已拿到工具结果，给出最终结论
        AssistantMessage finalMsg = new AssistantMessage(
                "根因：saveUser 中变量 x 在初始化前被读取，导致 NullPointerException。"
                        + "修复：确保先赋值再使用。");
        ChatResponse resp2 = new ChatResponse(List.of(new Generation(finalMsg)));

        when(chatModel.call(any(Prompt.class))).thenReturn(resp1, resp2);

        DiagnosisAgent agent = new DiagnosisAgent(
                chatModel, extractor, promptBuilder, vectorStore,
                registry, List.of(), 6, 5);

        // ---- 执行 ----
        String trace = "java.lang.NullPointerException: Cannot read field because \"u\" is null\n"
                + "\tat com.demo.Service.process(Service.java:42)\n"
                + "\tat com.demo.Controller.handle(Controller.java:18)\n"
                + "\tat java.base/java.lang.Thread.run(Thread.java:840)\n";
        DiagnosticResponse resp = agent.diagnose(trace);

        // ---- 断言：诊断结论 + 工具调用轨迹 ----
        assertTrue(resp.getDiagnosis() != null
                        && resp.getDiagnosis().contains("根因"),
                "应返回 LLM 最终诊断结论，实际: " + resp.getDiagnosis());
        assertFalse(resp.getToolTrace().isEmpty(),
                "应记录至少一次工具调用轨迹");
        assertTrue(resp.getToolTrace().get(0).contains("searchCode"),
                "工具轨迹应包含 searchCode 调用，实际: " + resp.getToolTrace());
        assertTrue(resp.getExceptionType().equals("NullPointerException"),
                "应正确解析异常类型为 NullPointerException，实际: " + resp.getExceptionType());

        ctx.close();
    }

    @Configuration
    static class Cfg {
        @Bean
        LocalFileTool localFileTool() {
            return new LocalFileTool(System.getProperty("java.io.tmpdir"));
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
        FunctionRegistry functionRegistry(org.springframework.context.ApplicationContext appCtx) {
            return new FunctionRegistry(appCtx);
        }
    }
}
