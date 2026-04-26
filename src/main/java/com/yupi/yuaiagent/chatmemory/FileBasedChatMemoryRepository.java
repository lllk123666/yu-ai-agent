package com.yupi.yuaiagent.chatmemory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 基于文件系统的对话记忆持久化实现。
 * 每个 conversationId 对应一个 JSON 文件，存储该会话的全部消息。
 */
@Slf4j
public class FileBasedChatMemoryRepository implements ChatMemoryRepository {

    private final Path storageDir;

    private final ObjectMapper objectMapper;

    public FileBasedChatMemoryRepository(String storagePath) {
        this.storageDir = Path.of(storagePath);
        this.objectMapper = new ObjectMapper();
        try {
            Files.createDirectories(this.storageDir);
            log.info("对话记忆存储目录: {}", this.storageDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("无法创建对话记忆存储目录: " + storagePath, e);
        }
    }

    @Override
    public List<String> findConversationIds() {
        try (Stream<Path> paths = Files.list(storageDir)) {
            return paths.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.substring(0, fileName.length() - ".json".length());
                    })
                    .toList();
        } catch (IOException e) {
            log.error("读取对话ID列表失败", e);
            return List.of();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId 不能为空");
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return List.of();
        }
        try {
            List<MessageDto> dtos = objectMapper.readValue(file, new TypeReference<>() {});
            return dtos.stream().map(MessageDto::toMessage).toList();
        } catch (IOException e) {
            log.error("读取对话记录失败, conversationId={}", conversationId, e);
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId 不能为空");
        Assert.notNull(messages, "messages 不能为 null");
        File file = getConversationFile(conversationId);
        try {
            List<MessageDto> dtos = messages.stream().map(MessageDto::fromMessage).toList();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, dtos);
        } catch (IOException e) {
            log.error("保存对话记录失败, conversationId={}", conversationId, e);
            throw new RuntimeException("保存对话记录失败", e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId 不能为空");
        File file = getConversationFile(conversationId);
        if (file.exists() && !file.delete()) {
            log.warn("删除对话记录文件失败, conversationId={}", conversationId);
        }
    }

    private File getConversationFile(String conversationId) {
        String safeFileName = conversationId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return storageDir.resolve(safeFileName + ".json").toFile();
    }

    /**
     * 消息的序列化/反序列化中间对象
     */
    static class MessageDto {

        @JsonProperty("type")
        private String type;

        @JsonProperty("content")
        private String content;

        public MessageDto() {
        }

        public MessageDto(String type, String content) {
            this.type = type;
            this.content = content;
        }

        public String getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        static MessageDto fromMessage(Message message) {
            return new MessageDto(
                    message.getMessageType().getValue(),
                    message.getText()
            );
        }

        Message toMessage() {
            MessageType messageType = MessageType.fromValue(this.type);
            return switch (messageType) {
                case USER -> new UserMessage(this.content);
                case ASSISTANT -> new AssistantMessage(this.content);
                case SYSTEM -> new SystemMessage(this.content);
                case TOOL -> new SystemMessage(this.content);
            };
        }
    }
}
