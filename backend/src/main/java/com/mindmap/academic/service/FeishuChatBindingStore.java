package com.mindmap.academic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class FeishuChatBindingStore {

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final CopyOnWriteArrayList<FeishuChatBinding> bindings;

    public FeishuChatBindingStore(
            ObjectMapper objectMapper,
            @Value("${app.feishu.user-chat-path:${user.dir}/data/feishu-user-chats.json}") String storagePath
    ) {
        this.objectMapper = objectMapper;
        this.storagePath = Path.of(storagePath);
        this.bindings = new CopyOnWriteArrayList<>(loadBindings());
    }

    public void remember(String userKey, String chatId, String displayName) {
        if (isBlank(userKey) || isBlank(chatId)) {
            return;
        }

        FeishuChatBinding binding = new FeishuChatBinding(
                userKey.trim(),
                chatId.trim(),
                isBlank(displayName) ? "" : displayName.trim(),
                OffsetDateTime.now()
        );

        bindings.removeIf(item -> item.userKey().equals(binding.userKey()));
        bindings.add(binding);
        saveBindings();
    }

    public Optional<String> findChatId(String userKey) {
        if (isBlank(userKey)) {
            return Optional.empty();
        }

        return bindings.stream()
                .filter(binding -> userKey.trim().equals(binding.userKey()))
                .max(Comparator.comparing(FeishuChatBinding::updatedAt))
                .map(FeishuChatBinding::chatId);
    }

    public int size() {
        return bindings.size();
    }

    public List<FeishuChatBinding> snapshot() {
        return new ArrayList<>(bindings).stream()
                .sorted(Comparator.comparing(FeishuChatBinding::updatedAt).reversed())
                .toList();
    }

    private List<FeishuChatBinding> loadBindings() {
        if (!Files.exists(storagePath)) {
            return List.of();
        }

        try {
            return objectMapper.readerForListOf(FeishuChatBinding.class).readValue(storagePath.toFile());
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void saveBindings() {
        try {
            Files.createDirectories(storagePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), snapshot());
        } catch (IOException ex) {
            throw new IllegalArgumentException("保存飞书用户会话失败：" + ex.getMessage(), ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @PreDestroy
    public void shutdown() {
        saveBindings();
    }

    public record FeishuChatBinding(
            String userKey,
            String chatId,
            String displayName,
            OffsetDateTime updatedAt
    ) {
    }
}