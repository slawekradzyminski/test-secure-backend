package com.awesome.testing.service.prompt;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PromptInjector {

    private final UserService userService;

    public ChatRequestDto augmentChatRequest(String username, ChatRequestDto original) {
        return augment(username, original, false);
    }

    public ChatRequestDto augmentToolRequest(String username, ChatRequestDto original) {
        return augment(username, original, true);
    }

    private ChatRequestDto augment(String username, ChatRequestDto original, boolean includeToolPrompt) {
        Objects.requireNonNull(original.getMessages(), "messages must not be null");
        String chatPrompt = userService.getChatSystemPrompt(username);
        String toolPrompt = includeToolPrompt ? userService.getToolSystemPrompt(username) : null;

        List<ChatMessageDto> cleanedHistory = stripExistingPrompts(original.getMessages(), chatPrompt, toolPrompt);

        List<ChatMessageDto> augmentedHistory = new ArrayList<>();
        augmentedHistory.add(systemMessage(chatPrompt));
        if (includeToolPrompt) {
            augmentedHistory.add(systemMessage(toolPrompt));
        }
        augmentedHistory.addAll(cleanedHistory);

        return ChatRequestDto.builder()
                .model(original.getModel())
                .messages(List.copyOf(augmentedHistory))
                .options(original.getOptions())
                .tools(original.getTools())
                .stream(original.getStream())
                .keepAlive(original.getKeepAlive())
                .think(original.getThink())
                .build();
    }

    private static List<ChatMessageDto> stripExistingPrompts(List<ChatMessageDto> history,
                                                            String chatPrompt,
                                                            String toolPrompt) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<ChatMessageDto> remaining = new ArrayList<>(history);
        if (!remaining.isEmpty() && isSamePrompt(remaining.get(0), chatPrompt)) {
            remaining.remove(0);
        }
        if (toolPrompt != null && !remaining.isEmpty() && isSamePrompt(remaining.get(0), toolPrompt)) {
            remaining.remove(0);
        }
        return remaining;
    }

    private static boolean isSamePrompt(ChatMessageDto message, String prompt) {
        return "system".equals(message.getRole())
                && message.getContent() != null
                && message.getContent().strip().equals(prompt.strip());
    }

    private static ChatMessageDto systemMessage(String content) {
        return ChatMessageDto.builder()
                .role("system")
                .content(content)
                .build();
    }
}
