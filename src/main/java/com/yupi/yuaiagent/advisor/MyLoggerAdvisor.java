package com.yupi.yuaiagent.advisor;

import lombok.extern.slf4j.Slf4j; // 导入 Lombok 的 @Slf4j 注解：编译期自动生成 log 日志对象（通常是 private static final Logger log）
import org.springframework.ai.chat.client.ChatClientMessageAggregator; // 导入流式响应聚合器：用于把 stream 的多段响应聚合后再做统一处理
import org.springframework.ai.chat.client.ChatClientRequest; // 导入新版 Advisor 请求对象（Spring AI 1.0+）
import org.springframework.ai.chat.client.ChatClientResponse; // 导入新版 Advisor 响应对象（Spring AI 1.0+）
import org.springframework.ai.chat.client.advisor.api.CallAdvisor; // 导入同步调用 Advisor 接口：实现 adviseCall
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain; // 导入同步 Advisor 链：通过 nextCall 调下一个 Advisor/模型
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor; // 导入流式调用 Advisor 接口：实现 adviseStream
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain; // 导入流式 Advisor 链：通过 nextStream 继续处理
import org.springframework.ai.chat.messages.UserMessage; // 导入用户消息类型：用于安全提取用户文本
import reactor.core.publisher.Flux; // 导入 Reactor 的 Flux：表示 0~N 个异步流式响应

@Slf4j // 在类上加注解：自动生成 log，省去手写 LoggerFactory.getLogger(...)
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor { // 同时实现同步和流式两个 Advisor 接口

    @Override // 表示重写接口方法，编译器会校验方法签名是否匹配
    public String getName() { // Advisor 名称方法：框架可用于标识/日志/追踪
        return this.getClass().getSimpleName(); // 返回当前类名（不带包名），如 "MyLoggerAdvisor"
    }

    @Override // 重写接口中的 getOrder
    public int getOrder() { // Advisor 执行顺序：数字越小通常越先执行
        return 0; // 返回 0：表示默认优先级（不是最高也不是最低的极端值）
    }

    private ChatClientRequest before(ChatClientRequest request) { // 前置处理：读取并打印本次用户输入，返回（可变更后的）请求
        String userText = ""; // 先给默认空串，避免后续空指针导致日志打印失败
        UserMessage userMessage = request.prompt().getUserMessage(); // 从请求的 Prompt 里取用户消息对象
        if (userMessage != null && userMessage.getText() != null) { // 双重判空：用户消息对象和消息文本都非空才读取
            userText = userMessage.getText(); // 提取用户输入文本
        }
        log.info("AI Request: {}", userText); // 记录 info 日志；{} 是参数占位符，避免字符串拼接开销
        return request; // 返回请求对象；当前逻辑只观察不改写请求
    }

    private void observeAfter(ChatClientResponse response) { // 后置观察：提取并打印 AI 最终文本响应
        String answerText = ""; // 默认空串，保证任何情况下日志都可输出
        if (response != null // 第 1 层判空：响应对象本身
                && response.chatResponse() != null // 第 2 层判空：底层 ChatResponse
                && response.chatResponse().getResult() != null // 第 3 层判空：主结果对象
                && response.chatResponse().getResult().getOutput() != null // 第 4 层判空：输出对象
                && response.chatResponse().getResult().getOutput().getText() != null) { // 第 5 层判空：输出文本
            answerText = response.chatResponse().getResult().getOutput().getText(); // 全链路安全后，提取 AI 文本内容
        }
        log.info("AI Response: {}", answerText); // 记录 AI 响应文本
    }

    @Override // 重写同步 Advisor 方法
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) { // 同步调用入口：每次 call() 会走这里
        ChatClientRequest processedRequest = this.before(request); // 执行前置日志逻辑，得到要继续传递的请求
        ChatClientResponse response = chain.nextCall(processedRequest); // 调用链中下一个节点（可能是下一个 Advisor 或最终模型调用）
        this.observeAfter(response); // 调用完成后记录响应日志
        return response; // 必须返回响应给上游链路，否则调用结果会丢失
    }

    @Override // 重写流式 Advisor 方法
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) { // 流式调用入口：每次 stream() 会走这里
        ChatClientRequest processedRequest = this.before(request); // 先记录请求日志（通常仅一次）
        Flux<ChatClientResponse> responses = chain.nextStream(processedRequest); // 获取下游返回的响应流（多段）
        return new ChatClientMessageAggregator().aggregateChatClientResponse(responses, this::observeAfter); // 聚合流式片段为完整响应后回调 observeAfter；this::observeAfter 是方法引用
    }
}