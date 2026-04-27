package com.yupi.yuaiagent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LoveAppRagCloudAdvisorConfig {

    // 从配置文件中读取你的DashScope API Key
    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Bean
    public Advisor loveAppRagCloudAdvisor() {
        // 1. 使用构建器(Builder)模式创建 DashScopeApi 实例
        //    这是解决兼容性问题的关键，相比直接注入更可控、更稳定
        DashScopeApi dashScopeApi = DashScopeApi.builder() // 调用静态builder()方法
                .apiKey(dashScopeApiKey) // 设置从配置文件中读取的API Key
                .build(); // 其他参数使用默认值，完成创建

        // 2. 定义你的知识库索引名（需与阿里云百炼平台上创建的一致）
        final String KNOWLEDGE_INDEX = "恋爱大师";

        // 3. 创建文档检索器，指向你的专属知识库
        DocumentRetriever mydocumentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder() // 使用构建器创建检索选项
                        .withIndexName(KNOWLEDGE_INDEX) // 指定要检索的知识库
                        .build()); // 完成选项构建

        // 4. 创建并返回一个RAG增强顾问（Advisor），它会将知识库检索结果注入到聊天上下文中
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(mydocumentRetriever) // 指定使用的检索器
                .build();
    }
}