package com.example.aida.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 手动构建 OpenAI 兼容的 Chat / Embedding 模型 Bean。
 *
 * <p>原因：Spring AI 1.0.0-M6 的 {@code OpenAiAutoConfiguration} 在通过
 * {@code OpenAiConnectionProperties}/{@code OpenAiChatProperties} 绑定 {@code base-url}
 * 时存在缺陷（继承自 {@code OpenAiParentProperties} 的字段未被正确绑定），
 * 导致 {@code OpenAiApi} 的 baseUrl 为 null，请求被相对解析回本应用自身（localhost），
 * 表现为调用 LLM 时收到自身 Whitelabel 404。
 *
 * <p>这里显式指定智谱的 base-url 与兼容路径，绕过有问题的属性绑定。
 */
@Configuration
public class OpenAiConfig {

    /** 智谱 OpenAI 兼容基础地址。 */
    private static final String BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String CHAT_MODEL = "glm-4-flash";
    private static final String EMBED_MODEL = "embedding-3";

    @Bean
    public OpenAiApi openAiApi(@Value("${LLM_API_KEY:${spring.ai.openai.api-key:}}") String apiKey) {
        return OpenAiApi.builder()
                .baseUrl(BASE_URL)
                .apiKey(apiKey)
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();
    }

    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi,
                                           ToolCallingManager toolCallingManager,
                                           ObjectProvider<ObservationRegistry> obs) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .toolCallingManager(toolCallingManager)
                .defaultOptions(OpenAiChatOptions.builder().model(CHAT_MODEL).build())
                .observationRegistry(obs.getIfUnique(() -> io.micrometer.observation.ObservationRegistry.NOOP))
                .build();
    }

    @Bean
    @Primary
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(EMBED_MODEL).build());
    }
}
