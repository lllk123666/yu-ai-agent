package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Slf4j
public class LoveApp {

    private static final int MAX_MEMORY_MESSAGES = 10;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。"
            + "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；"
            + "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。"
            + "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    private final ChatClient chatClient;

    /**
     * 初始化 ChatClient，设置系统提示和记忆顾问，使用文件持久化对话记忆
     */
    public LoveApp(ChatModel dashscopeChatModel,
                   @Value("${chat-memory.storage-path:./chat-memory}") String storagePath) {
        // 使用基于文件的对话记忆仓库，替代默认的内存存储
        ChatMemoryRepository chatMemoryRepository = new FileBasedChatMemoryRepository(storagePath);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
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
                // 创建一次新的请求上下文
                .prompt()
                // 设置用户消息
                .user(message)
                // Lambda：给本次调用注入会话ID参数
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


    // 定义 Java 记录类：不可变数据载体，自动生成构造器、访问器、toString 等
    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * AI 对话并且生成恋爱报告
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