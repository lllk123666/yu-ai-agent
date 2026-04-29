package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemoryRepository;
import com.yupi.yuaiagent.rag.LoveAppContextualQueryAugmenterFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Slf4j
public class LoveApp {

    // 对话记忆的最大消息数，超过后旧消息会被裁剪（滑动窗口机制）
    private static final int MAX_MEMORY_MESSAGES = 10;

    // RAG 检索时返回的最相似文档片段数量（Top-K）
    private static final int RAG_TOP_K = 4;

    // RAG 检索相似度阈值，得分低于该值的文档不会被用作上下文
    private static final double RAG_SIMILARITY_THRESHOLD = 0.6;

    // 系统提示词，定义 AI 助理的角色定位、行为准则和引导话术
    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。"
            + "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；"
            + "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。"
            + "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    // Spring AI 的 ChatClient，用于与大模型交互
    private final ChatClient chatClient;

    // 向量存储，存放恋爱心理领域的知识库文档，用于 RAG 增强检索
    private final VectorStore loveAppVectorStore;

    //查询重写
    @Resource
    private QueryRewriter queryRewriter;

    //工具调用
    @Resource
    private ToolCallback[] allTools;

    /**
     * 初始化 ChatClient，设置系统提示和记忆顾问，使用文件持久化对话记忆
     *
     * @param dashscopeChatModel 阿里云通义千问大模型实例（由 Spring 注入）
     * @param loveAppVectorStore 名为 "loveAppVectorStore" 的向量存储 Bean（由 Spring 注入）
     * @param storagePath        对话记忆文件存储路径，支持配置文件指定，默认 "./chat-memory"
     */
    public LoveApp(ChatModel dashscopeChatModel,
                   @Qualifier("loveAppVectorStore") VectorStore loveAppVectorStore,
                   @Value("${chat-memory.storage-path:./chat-memory}") String storagePath) {
        // 创建基于文件系统的对话记忆仓库，实现记忆持久化（重启不丢失）
        ChatMemoryRepository chatMemoryRepository = new FileBasedChatMemoryRepository(storagePath);
        // 构建基于滑动窗口的对话记忆对象，限制最大消息数，超出后自动淘汰最早的消息
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)   // 指定记忆存储仓库（文件持久化）
                .maxMessages(MAX_MEMORY_MESSAGES)             // 设置会话保留的最大消息数
                .build();
        // 构建 ChatClient，配置默认的系统提示词和默认的顾问链
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)                 // 设置全局默认系统提示词，所有请求都会携带
                .defaultAdvisors(//设置全局默认顾问链，所有请求都会依次应用这些顾问
                        // 对话记忆顾问，根据方法中动态指定的会话ID,自动注入历史消息上下文，
                        // 实现多轮对话记忆功能
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义的日志记录顾问，用于记录请求和响应信息，方便调试与审计
                        new MyLoggerAdvisor()
                )
                .build();
        // 保存注入的向量存储引用，供 RAG 增强对话方法使用
        this.loveAppVectorStore = loveAppVectorStore;
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    // 公开方法：输入用户消息和会话ID，返回模型回答
    public String doChat(String message, String chatId) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message 不能为空");
        }
        // 三元表达式条件部分：chatId 是否有效
        // 条件为真，使用传入 chatId
        // 条件为假，使用默认会话ID，保证程序仍可运行但没有连续上下文
        String conversationId = StringUtils.hasText(chatId)
                ? chatId
                : ChatMemory.DEFAULT_CONVERSATION_ID;
        // 使用当前对象的 chatClient 发起调用
        String content = this.chatClient
                .prompt()
                // 设置用户消息
                .user(message)
                // Lambda：给本次调用注入会话ID参数,
                // 让记忆顾问MessageChatMemoryAdvisor知道当前对话属于哪个会话，
                // 从而正确注入历史上下文
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                // 执行同步调用
                .call()
                // 直接取模型返回的文本内容（String）
                .content();
        // 防御式编程：极端情况下内容可能为 null
        if (content == null) {
            // 统一转为空串，避免上层空指针
            content = "";
        }

        log.info("chatId={}", conversationId);
        return content;
    }

    /**
     * AI 对话（支持 RAG 知识库增强 + 多轮对话记忆）
     * 使用本地知识库
     *
     *
     * @param message 用户输入
     * @param chatId  会话 ID
     * @param status  文档标签
     * @return 模型回答
     */
    public String doChatWithRag(String message, String chatId, String status) {
        // 校验用户输入消息不能为空或仅包含空白字符，避免无效请求
        if (!StringUtils.hasText(message)) {
            // 抛出非法参数异常，明确告知调用方 message 是必填项
            throw new IllegalArgumentException("message 不能为空");
        }
        //查询重写用户的信息
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);

        // 确定使用的会话 ID：若传入的 chatId 有效则使用它，否则使用默认会话 ID（如 "default"）
        // 这样即使不传 chatId 也能实现对话记忆，只是所有匿名对话共享同一记忆
        String conversationId = StringUtils.hasText(chatId)
                ? chatId
                : ChatMemory.DEFAULT_CONVERSATION_ID;

        // 构建过滤条件，假设每个文档在存入向量库时都带有 "status" 这个元数据字段
        Filter.Expression filterExpression = new FilterExpressionBuilder()
                .eq("status", status)   // 只检索 status=published 的文档
                .build();
        // 构造带过滤的文档检索器
        DocumentRetriever documentRetrieve = VectorStoreDocumentRetriever.builder()
                .vectorStore(loveAppVectorStore)          // 需要用到的向量库,指定向量存储实例，里面存有业务知识
                .filterExpression(filterExpression)       // 文档过滤条件
                .similarityThreshold(RAG_SIMILARITY_THRESHOLD)  // 设置相似度阈值，低于该值的文档不被采用
                .topK(RAG_TOP_K)                           // 设置返回最相似的 Top-K 条文档片段
                .build();
        // 生成 LoveAppRagCustomAdvisor
        Advisor LoveAppRagCustomAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetrieve)
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                .build();                                      // 构建 RetrievalAugmentationAdvisor 实例

        // 通过 ChatClient 构建并发送提示词，依次应用多个 Advisor（顾问）处理器
        String content = this.chatClient
                .prompt()                                       // 创建一个提示词构建器
                .user(rewrittenMessage)                                  // 设置用户输入的消息内容(经过查询重写后的）
                .advisors(spec -> spec.param(                   // 添加第一个 Advisor：对话记忆顾问
                        ChatMemory.CONVERSATION_ID, conversationId  // 指定当前会话 ID，实现多轮对话记忆
                ))
                .advisors(LoveAppRagCustomAdvisor)                // 添加第二个 Advisor：RAG 本地知识库增强顾问
                .call()                                         // 发送请求并获取模型响应
                .content();                                     // 提取响应中的文本内容

        // 防御性处理：若模型返回的文本为 null，则置为空字符串，避免上层出现 NullPointerException
        if (content == null) {
            content = "";
        }

        // 记录对话日志，方便追踪不同会话的 RAG 参数和会话 ID
        log.info("chatId={}, ragTopK={}, ragSimilarityThreshold={}", conversationId, RAG_TOP_K, RAG_SIMILARITY_THRESHOLD);
        // 返回模型生成的回答文本
        return content;
    }


    /**
     * AI 对话（支持 RAG 知识库增强 + 多轮对话记忆）
     * 使用自定义的工具,允许模型在对话中调用预定义的工具函数
     *
     * @param message 用户输入
     * @param chatId  会话 ID
     * @return 模型回答
     */
    public String doChatWithTools(String message, String chatId) {
        // 校验用户输入消息不能为空或仅包含空白字符，避免无效请求
        if (!StringUtils.hasText(message)) {
            // 抛出非法参数异常，明确告知调用方 message 是必填项
            throw new IllegalArgumentException("message 不能为空");
        }

        // 确定使用的会话 ID：若传入的 chatId 有效则使用它，否则使用默认会话 ID（如 "default"）
        // 这样即使不传 chatId 也能实现对话记忆，只是所有匿名对话共享同一记忆
        String conversationId = StringUtils.hasText(chatId)
                ? chatId
                : ChatMemory.DEFAULT_CONVERSATION_ID;

        // 通过 ChatClient 构建并发送提示词，依次应用多个 Advisor（顾问）处理器
        String content = this.chatClient
                .prompt()                                       // 创建一个提示词构建器
                .user(message)                                  // 设置用户输入的消息内容(经过查询重写后的）
                .advisors(spec -> spec.param(                   // 添加第一个 Advisor：对话记忆顾问
                        ChatMemory.CONVERSATION_ID, conversationId  // 指定当前会话 ID，实现多轮对话记忆
                ))
                .toolCallbacks(allTools)                                  // 添加工具调用器，允许模型在对话中调用预定义的工具函数
                .call()                                         // 发送请求并获取模型响应
                .content();                                     // 提取响应中的文本内容

        // 防御性处理：若模型返回的文本为 null，则置为空字符串，避免上层出现 NullPointerException
        if (content == null) {
            content = "";
        }

        // 记录对话日志，方便追踪不同会话的 RAG 参数和会话 ID
        log.info("chatId={}, ragTopK={}, ragSimilarityThreshold={}", conversationId, RAG_TOP_K, RAG_SIMILARITY_THRESHOLD);
        // 返回模型生成的回答文本
        return content;
    }


    // 注入云知识库 RAG 增强顾问（已在 LoveAppRagCloudAdvisorConfig 中配置）
    @Autowired
    private Advisor loveAppRagCloudAdvisor;

    /**
     * AI 对话（支持云知识库增强 + 多轮对话记忆）
     * 使用阿里云百炼云知识库（DashScope 文档检索）
     *
     * @param message 用户输入
     * @param chatId  会话 ID
     * @return 模型回答
     */
    public String doChatWithCloudRag(String message, String chatId) {
        // 校验用户输入消息不能为空或仅包含空白字符，避免无效请求
        if (!StringUtils.hasText(message)) {
            // 抛出非法参数异常，明确告知调用方 message 是必填项
            throw new IllegalArgumentException("message 不能为空");
        }

        // 确定使用的会话 ID：若传入的 chatId 有效则使用它，否则使用默认会话 ID（如 "default"）
        // 这样即使不传 chatId 也能实现对话记忆，只是所有匿名对话共享同一记忆
        String conversationId = StringUtils.hasText(chatId)
                ? chatId
                : ChatMemory.DEFAULT_CONVERSATION_ID;

        // 注意：原来基于本地向量库的 QuestionAnswerAdvisor 已不再需要，
        // 而是直接使用注入的 RetrievalAugmentationAdvisor，它内部已配置好云知识库检索器

        // 通过 ChatClient 构建并发送提示词，依次应用多个 Advisor（顾问）处理器
        String content = this.chatClient
                .prompt()                                       // 创建一个提示词构建器
                .user(message)                                  // 设置用户输入的消息内容
                .advisors(spec -> spec.param(                   // 添加第一个 Advisor：对话记忆顾问
                        ChatMemory.CONVERSATION_ID, conversationId  // 指定当前会话 ID，实现多轮对话记忆
                ))
                .advisors(loveAppRagCloudAdvisor)               // 添加第二个 Advisor：云知识库增强顾问
                .call()                                         // 发送请求并获取模型响应
                .content();                                     // 提取响应中的文本内容

        // 防御性处理：若模型返回的文本为 null，则置为空字符串，避免上层出现 NullPointerException
        if (content == null) {
            content = "";
        }

        // 记录对话日志，方便追踪不同会话和所用知识库信息
        log.info("chatId={}, using cloud knowledge base index: 恋爱大师", conversationId);
        // 返回模型生成的回答文本
        return content;
    }


    // 定义 Java 记录类：不可变数据载体，自动生成构造器、访问器、toString 等
    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * AI 对话并且生成恋爱报告
     *
     * @param message
     * @param chatId
     * @param username
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId, String username) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message 不能为空");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("username 不能为空");
        }

        // 判断 chatId 是否有效（非空且非全空白）
        // 如果有效，就使用调用方传入的 chatId，保持对话连续性
        // 如果无效，回退到默认会话ID，保证程序仍可运行
        String conversationId = StringUtils.hasText(chatId)
                ? chatId
                : ChatMemory.DEFAULT_CONVERSATION_ID;

        // 使用当前类中已初始化的 ChatClient 开始构建请求
        LoveReport loveReport = this.chatClient
                .prompt()// 进入一次新的提示词构建流程
                // 通过 Lambda 配置系统消息对象 s，支持模板变量注入
                .system(s -> s
                        .text(SYSTEM_PROMPT
                                + "每次对话后都要生成恋爱报告。"
                                + "标题为《{username}的恋爱报告》，内容为建议列表 suggestions。"
                                + "请按结构化形式返回：title 为字符串，suggestions 为字符串数组。")
                        // 给模板变量 username 赋真实值，确保标题用真实用户名
                        .param("username", username))
                .user(message)// 注入用户本轮输入内容
                // 传入会话ID上下文，让记忆 Advisor 归档到正确会话
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                // 执行同步调用（非流式）
                .call()
                // 将模型响应按结构化方式映射为 LoveReport 对象
                .entity(LoveReport.class);
        // 记录关键日志：会话、用户名、结构化结果
        log.info("chatId={}, username={}, loveReport={}", conversationId, username, loveReport);
        return loveReport;
    }
}