package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 向量库配置类
 * 作用：初始化向量存储，将 Markdown 文档向量化并存入内存向量库
 * 适配：Spring AI 1.1.2 正式版
 */
// 1. 核心注解：声明这是 Spring 配置类，项目启动时自动加载
@Configuration
// 2. Lombok 日志注解：打印向量库初始化、文档加载日志
@Slf4j
// 3. 公开类（Spring 配置类强制规范，避免注入失败）
public class LoveAppVectorStoreConfig {

    // 4. 注入文档加载器（用于读取 Markdown 文档）
    // 构造器注入：Spring 官方推荐的注入方式，比 @Resource / @Autowired 更安全
    private final LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    // 5. 构造方法：Spring 自动注入 LoveAppDocumentLoader 对象
    public LoveAppVectorStoreConfig(LoveAppDocumentLoader loveAppDocumentLoader) {
        this.loveAppDocumentLoader = loveAppDocumentLoader;
    }

    /**
     * 6. 核心：创建向量库 Bean 对象，交给 Spring 管理
     *
     * @param dashscopeEmbeddingModel 阿里云通义千问嵌入模型（自动注入）
     * @return 初始化好的向量存储对象
     */
    @Bean
    public VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        // 7. 初始化 内存版向量库（SimpleVectorStore）
        // builder 模式：传入嵌入模型，将文本转为数值向量
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        log.info("内存向量库初始化成功");

        // 8. 调用文档加载器，读取所有 Markdown 文件，转为 Document 对象
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();

        // 9. 健壮性判断：如果没有加载到任何文档，直接返回空向量库，避免报错
        if (documents.isEmpty()) {
            log.warn("未加载到任何 Markdown 文档，向量库无数据");
            return simpleVectorStore;
        }
        //自动为文档补充元数据
        List<Document> enrichdocuments = myKeywordEnricher.enrichDocuments(documents);

        // 10. 核心操作：将文档向量化，并添加到向量库中
        // 底层逻辑：调用 EmbeddingModel 把文本转成向量 → 存入内存
        simpleVectorStore.add(enrichdocuments);
        log.info("向量库加载完成，共存入 {} 条文档数据", enrichdocuments.size());

        // 11. 返回初始化好的向量库对象，供其他类（如对话服务）注入使用
        return simpleVectorStore;
    }
}