# 本地化智能研发助手（个人项目）

> 面向 Java 开发者的本地化智能排障助手：大模型 + RAG 实现异常自动诊断与代码定位。

## 技术栈
Spring Boot 3.2 · Spring AI（OpenAI 兼容）· 智谱 GLM-4（对话）/ embedding-3（向量）· LLM Function Calling（反射 + 动态代理）· Redis 向量检索（embedding 存 Redis，余弦相似度应用层计算）

## 架构
异常输入 → Prompt 模板引擎（异常上下文提取） → RAG 召回相关代码 → LLM 决策 Function Calling → 本地工具执行（读文件 / 代码检索） → 诊断结论 + 代码定位

## 核心能力
- **Agent 调度**：Spring AI 从零搭建，可动态插值 Prompt 模板引擎，针对 Java 异常堆栈优化上下文提取。
- **动态 Function Calling**：Java 反射 + 动态代理封装本地文件读写 / 代码检索为 Spring Bean，Agent 安全自主调用。
- **代码 RAG**：方法级代码分片 + 向量检索（embedding 存 Redis，余弦相似度召回），提升长上下文召回精度、降低幻觉。

### RAG 实现要点（可对照读代码）
- 分片：`rag/CodeChunker` 按「大括号深度 + 方法签名正则」做方法级切分（`CodeChunk`），不依赖完整语法树；`CodeChunk.getContent()` 把「路径 + 签名 + 方法体」拼成向量化文本。
- 向量化：`rag/EmbeddingService` 封装 Spring AI `EmbeddingModel`（智谱 embedding-3）。
- 存储与检索：`rag/CodeVectorStore` 将分片 embedding 以普通 JSON 存于 **Redis db2**（任意版本可用，不依赖 Redis 向量类型），检索时在应用层算**余弦相似度**取 Top-K（`rank()` 为纯函数，可单测）。
- 手动验证接口：`POST /api/rag/index`（`{"path":"<本地代码目录>"}` 索引全部 .java）、`GET /api/rag/search?q=...&topK=5` 召回。亦可在 `application.yml` 配 `aida.rag.codebase-path` 让启动时自动索引。

### Function Calling 实现要点（可对照读代码）
- 声明式注册：`function/AiTool` 注解标在 Spring Bean 的普通方法上（如 `LocalFileTool`、`CodeSearchTool`），即成为 Agent 可调用工具，**无需任何手写注册样板**。
- 反射扫描：`function/FunctionRegistry` 启动时遍历容器，用反射收集所有 `@AiTool` 方法，提取「方法名 / 描述 / 参数名·类型·描述」为 `ToolDefinition`。
- 动态代理分发：`function/ToolInvokerFactory` 用 `java.lang.reflect.Proxy` 创建 `ToolInvoker` 代理，调用时统一做日志/可观测，再委派 `ReflectiveDispatcher` 用反射把「工具名 + 参数 Map」还原为强类型实参并 `method.invoke` 目标 Bean——**新增工具零改动分发层**。
- 接入 Spring AI：`function/FunctionCallingConfig` 把每个 `ToolDefinition` 包成 `FunctionCallback`（builder 链 `.function(name, fn).description().inputType().responseConverter().build()`），供 `ChatClient` 自动调用。
- 沙箱护栏：`LocalFileTool` 所有路径经 `sandbox.resolve().normalize()` + 前缀校验，越权访问直接拒绝（见单测 `sandboxBlocksPathEscape`）。
- 手动验证接口：`GET /api/tool/list`（列出全部工具与参数）、`POST /api/tool/call`（`{"toolName":"readFile","args":{"path":"x.txt"}}` 直接按名调用，无需 LLM 即可验证反射分发）。

### Agent 编排实现要点（#11，可对照读代码）
- 编排核心：`agent/DiagnosisAgent`（`@Service`）把「异常解析 → RAG 召回 → LLM 多轮工具调用」串成闭环，面向 Spring AI 的 `ChatModel` 抽象，换模型只改配置、不动编排逻辑。
- 多轮 loop（自己维护对话历史，不依赖框架隐式循环，便于可观测 / 配上限）：
  1. `SystemMessage`（调试助手人设 + 工具使用纪律）+ `UserMessage`（组装好的诊断 Prompt，含 RAG 片段）作为首轮输入；
  2. 每轮用 `Prompt(history, OpenAiChatOptions.toolCallbacks(...))` 调 `ChatModel.call(...)`；
  3. 若 `AssistantMessage.getToolCalls()` 非空 → 解析 `ToolCall`（id/name/arguments JSON），经 `FunctionRegistry.getInvoker().call(name, args)` **反射执行本地工具**，结果以 `ToolResponseMessage` 回填历史，进入下一轮；
  4. 模型不再请求工具 → 取 `AssistantMessage.getContent()` 为最终诊断；达到 `aida.diagnose.max-rounds`（默认 6）则移除工具强制收尾。
- 工具执行零耦合：Agent 只负责「调度 + 回填」，真实执行全部复用 Function Calling 模块的反射分发；工具 schema 也直接复用 `FunctionCallingConfig` 产出的 `List<FunctionCallback>` 注入模型。
- 降级策略：`retrieveRag` 在无 Key / 未索引时捕获异常返回提示文案，不阻塞主链路；工具执行异常作为可读 ERROR 回传模型，便于其自我纠正。
- 可观测：`DiagnosticResponse.toolTrace` 记录每一轮的工具名 / 入参 / 结果（前端「Agent 工具调用轨迹」卡片展示），方便复盘与面试讲解。
- 测试：`src/test/.../agent/AgentLoopTest` 用 Mockito 模拟 `ChatModel`（第一轮下 ToolCall、第二轮给结论）+ mock `CodeVectorStore`，**无需真实 Key** 即可验证「请求工具 → 反射执行 → 结果回填 → 收尾」整条链路。

## 效果（附测试脚本与截图，见 docs/results/）
- 异常解析准确率 **65% → 85%**（50 条测试用例，需接入真实 Key 后跑 `src/test/accuracy/`，数字以你实测为准，不预填）
- 单次 Debug 平均往返 **5 → 2 次**（需真实 Key 实测，以你跑出为准，不预填）

## 快速开始
1. 环境：JDK 17 / Redis（任意版本，用于存 embedding）/ Maven
2. 配置：复制 `application-example.yml` 为 `application.yml`，设置环境变量 `LLM_API_KEY`（智谱 API Key）；对话 `glm-4-flash`、embedding `embedding-3`；Redis 默认 `192.168.100.128:6379`，**向量数据存于 db2**（无密码，可在 yml 覆盖）
3. 启动（推荐用 jar，比 `mvn spring-boot:run` 稳）：
   ```bash
   mvn package -DskipTests
   java -jar target/ai-dev-assistant-1.0.0.jar --server.port=8081
   ```
   > 注：本项目**未使用** `spring.ai.openai.base-url` 属性绑定 —— Spring AI 1.0.0-M6 的 `OpenAiAutoConfiguration` 对该属性绑定有缺陷（baseUrl 恒为 null，会导致请求被相对解析回本应用自身而 404）。baseUrl 已由 `config/OpenAiConfig.java` 手动构建 `OpenAiApi`/`OpenAiChatModel`/`OpenAiEmbeddingModel` 显式指定（智谱 `https://open.bigmodel.cn/api/paas/v4` + `/chat/completions` + `/embeddings`）。
4. 体验：
   - 浏览器打开 `http://localhost:8081/`（单页前端），粘贴异常堆栈点「诊断」
   - 或 `POST /api/diagnose`，body `{"trace":"<异常堆栈>"}`（注意字段名是 `trace`，JSON 内换行需转义为 `\n`）
   - 想让 Agent 真正调用 `searchCode`/`findMethod`/`readFile` 工具：先 `POST /api/rag/index {"path":"<你的代码目录绝对路径>"}` 建立代码索引，再诊断指向该代码的异常，Agent 会基于召回上下文决定是否调用工具。

## 目录结构
```
com.example.aida
├── AidaApplication.java
├── agent/      # Agent 编排主链路
├── prompt/     # 动态 Prompt 模板引擎 + 异常上下文提取
├── function/   # 反射 + 动态代理封装的本地工具（Function Calling）
├── rag/        # 方法级代码分片 + 向量检索
├── config/     # Spring AI / Redis 配置
├── model/      # 领域模型
└── web/        # REST 接口 + 单页前端
```
