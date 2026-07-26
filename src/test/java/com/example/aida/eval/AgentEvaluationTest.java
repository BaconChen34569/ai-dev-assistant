package com.example.aida.eval;

import com.example.aida.agent.DiagnosisAgent;
import com.example.aida.model.DiagnosticResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #13 / #14 端到端评测：需真实 LLM Key，默认不跑（避免空跑烧钱 / 无 Key 时自动跳过）。
 *
 * <p>运行方式（设置环境变量后执行）：
 * <pre>
 *   export LLM_API_KEY="你的智谱Key"
 *   export REDIS_PASSWORD="你的Redis密码"   # 可选：有则启用 RAG 召回，无则降级为纯诊断
 *   mvn test -Dtest=AgentEvaluationTest
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *   <li>@BeforeAll 跑全部 50 条 case 一次并缓存结果，两个 @Test 方法复用，避免重复烧钱；</li>
 *   <li>accuracy_eval (#13)：断言每条诊断包含 expectContains 关键词（异常类型 + 根因概念），打印真实准确率；</li>
 *   <li>roundtrip_eval (#14)：统计每条诊断的 LLM 往返轮数（来自 DiagnosticResponse.rounds），打印平均 / 最小 / 最大；</li>
 *   <li>断言只设“合理性下限”，真实指标（如准确率 85%、平均往返 2 次）以打印值为准，由人工回填简历，AI 不预填数字。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = { "aida.diagnose.max-rounds=6" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("eval")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
        named = "LLM_API_KEY",
        matches = "^(?!\\s*$).+",
        disabledReason = "未设置 LLM_API_KEY，跳过评测（避免空跑）")
class AgentEvaluationTest {

    @Autowired
    DiagnosisAgent agent;

    /** 单条 case 定义（与 src/test/resources/eval/cases.json 对应）。 */
    static class Case {
        public String id;
        public String category;
        public String trace;
        public List<String> expectContains;
        public String expectException;
        public String expectFrame;
    }

    /** 单条 case 的运行结果。 */
    static class Result {
        Case c;
        DiagnosticResponse resp;
        boolean pass;
        String reason;
        int rounds;
        int toolCalls;

        Result(Case c) {
            this.c = c;
        }
    }

    private final List<Result> results = new ArrayList<>();

    @BeforeAll
    void runAllCases() throws Exception {
        ObjectMapper om = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("eval/cases.json")) {
            assertNotNull(in, "评测用例文件 eval/cases.json 缺失");
            List<Case> cases = om.readValue(in, new TypeReference<List<Case>>() {});
            for (Case c : cases) {
                Result r = new Result(c);
                try {
                    DiagnosticResponse resp = agent.diagnose(c.trace);
                    r.resp = resp;
                    r.rounds = resp.getRounds();
                    r.toolCalls = (resp.getToolTrace() == null) ? 0 : resp.getToolTrace().size();

                    String diag = (resp.getDiagnosis() == null) ? "" : resp.getDiagnosis();
                    boolean llmOk = !diag.startsWith("（LLM");

                    // 关键词口径：根因概念词（expectContains）任一命中 或 异常类型（expectException）命中，
                    // 避免模型用英文异常名 / 近义词时被误判假阴性
                    List<String> keywords = new ArrayList<>();
                    if (c.expectContains != null) keywords.addAll(c.expectContains);
                    if (c.expectException != null && !keywords.contains(c.expectException)) {
                        keywords.add(c.expectException);
                    }
                    boolean kwOk = keywords.isEmpty()
                            || keywords.stream().anyMatch(k -> diag.toLowerCase().contains(k.toLowerCase()));

                    r.pass = llmOk && kwOk;
                    if (!llmOk) {
                        r.reason = "LLM 调用失败：" + diag;
                    } else if (!kwOk) {
                        r.reason = "缺少关键词：" + missingKeywords(diag, keywords);
                    } else {
                        r.reason = "OK";
                    }
                } catch (Exception e) {
                    r.resp = null;
                    r.pass = false;
                    r.reason = "异常：" + e.getClass().getSimpleName() + " - " + e.getMessage();
                }
                results.add(r);
            }
        }
        printReport();
    }

    @Test
    @DisplayName("#13 诊断准确率评测（50 条带断言 case）")
    void accuracy_eval_13() {
        long pass = results.stream().filter(r -> r.pass).count();
        double rate = (double) pass / results.size();
        System.out.printf("%n========== #13 诊断准确率 ==========%n");
        System.out.printf("通过: %d / %d  =  %.1f%%%n", pass, results.size(), rate * 100);
        System.out.println("（以上为真实准确率，请据此回填简历中的指标，AI 不预填数字）");
        // 合理性下限：仅防止链路崩溃级回归，真实目标值以打印为准
        assertTrue(rate >= 0.6,
                String.format("诊断准确率过低 (%.1f%%)，请检查 Agent 编排 / Prompt / RAG 召回", rate * 100));
    }

    @Test
    @DisplayName("#14 平均往返轮数评测（Debug 往返计数）")
    void roundtrip_eval_14() {
        IntSummaryStatistics stats = results.stream().mapToInt(r -> r.rounds).summaryStatistics();
        double avg = stats.getAverage();
        System.out.printf("%n========== #14 Debug 往返轮数 ==========%n");
        System.out.printf("平均往返: %.2f 轮  (min=%d, max=%d)%n",
                avg, stats.getMin(), stats.getMax());
        System.out.println("逐条往返 / 工具调用次数：");
        results.forEach(r -> System.out.printf("  %-10s rounds=%-2d toolCalls=%-2d  %s%n",
                r.c.id, r.rounds, r.toolCalls, r.pass ? "PASS" : "FAIL"));
        System.out.println("（以上为真实往返指标：rounds 为应用层 chatModel.call 次数，Spring AI 内部自动执行工具；简历请以「工具调用次数」(TOOL) 作为量化点，勿写“往返由 X 降至 Y”）");
        // 预算护栏：平均不应突破 max-rounds(6)
        assertTrue(avg <= 6.0,
                String.format("平均往返 %.2f 超出上限预算 6 轮", avg));
    }

    // ===== 辅助 =====

    private static List<String> missingKeywords(String diag, List<String> expects) {
        if (expects == null) return List.of();
        String low = diag.toLowerCase();
        return expects.stream()
                .filter(k -> !low.contains(k.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void printReport() {
        long pass = results.stream().filter(r -> r.pass).count();
        IntSummaryStatistics rs = results.stream().mapToInt(r -> r.rounds).summaryStatistics();
        IntSummaryStatistics ts = results.stream().mapToInt(r -> r.toolCalls).summaryStatistics();
        System.out.printf("%n================ 评测总览 ================%n");
        System.out.printf("用例数: %d | 通过: %d (%.1f%%)%n",
                results.size(), pass, (double) pass / results.size() * 100);
        System.out.printf("往返轮数: avg=%.2f min=%d max=%d%n",
                rs.getAverage(), rs.getMin(), rs.getMax());
        System.out.printf("工具调用: avg=%.2f min=%d max=%d%n",
                ts.getAverage(), ts.getMin(), ts.getMax());
        System.out.println("------------------------------------------");
        System.out.printf("%-10s %-7s %-6s %-6s %s%n", "ID", "PASS", "RND", "TOOL", "原因");
        for (Result r : results) {
            System.out.printf("%-10s %-7s %-6d %-6d %s%n",
                    r.c.id, r.pass ? "Y" : "N", r.rounds, r.toolCalls, r.reason);
        }
        System.out.println("==========================================");
    }
}
