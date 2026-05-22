package com.codesense.codesense.service;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


/**
 * Feature 5: Multi-turn conversations with memory.
 *
 * Users can ask follow-up questions without repeating context:
 *   Q1: "How does the UserService authenticate users?"
 *   Q2: "What happens if the password is wrong?"  ← references Q1 context
 *   Q3: "Show me the test for that method"        ← references Q2 context
 *
 * Memory is stored per sessionId in InMemoryChatMemory (ConcurrentHashMap).
 * Each session maintains its own conversation history.
 */
@Service
@Slf4j
public class MultiTurnChatService {

    private final ChatClient ragChatClient;
    private final ChatMemory chatMemory;

    public MultiTurnChatService(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            ChatMemory chatMemory
    ) {
        this.ragChatClient = ragChatClient;
        this.chatMemory    = chatMemory;
    }

    /**
     * Send a message within a conversation session.
     * The sessionId ties all messages in a conversation together.
     *
     * @param sessionId  unique ID per user conversation (e.g. UUID from frontend)
     * @param projectName  which codebase to search
     * @param message    the user's latest message
     * @return streaming response (Flux<String>) for real-time display
     */
    @RateLimiter(name = "llm-api")
    public Flux<String> chat(String sessionId, String projectName, String message) {
        log.debug("Multi-turn chat: session={}, project={}", sessionId, projectName);

        return ragChatClient.prompt()
                .system("Project context: " + projectName)
                .user(message)
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(sessionId)
                                .build()
                )
                .stream()
                .content();
    }

    /**
     * Clear conversation history for a session.
     * Call this when the user clicks "New conversation".
     */
    public void clearSession(String sessionId) {
        chatMemory.clear(sessionId);
        log.info("Cleared chat session: {}", sessionId);
    }
}