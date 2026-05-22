package com.codesense.codesense.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    public static final String RAG_SYSTEM_PROMPT = """
            You are CodeSense, an expert AI assistant that helps engineers
            understand Java codebases.

            You have access to the user's actual source code, which is provided
            in the context below each question.

            Rules:
            - Answer ONLY from the provided code context.
            - Always mention which class or file your answer comes from.
            - If the answer is not in the context, say:
              "I couldn't find relevant code for that. Try re-phrasing or
              check if the file was ingested."
            - For code questions, show the relevant code snippet.
            - Be concise. Engineers want direct answers.
            """;

    // ── RAG Chat Client ────────────────────────────────────────────────────────
    // Used by: RagChatService and MultiTurnChatService
    // Has QuestionAnswerAdvisor wired in → every call automatically retrieves
    // relevant code chunks from pgvector before sending to the LLM.
    @Bean
    public ChatClient ragChatClient(ChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultSystem(RAG_SYSTEM_PROMPT)
                .defaultAdvisors(
                        // This single advisor wires the entire RAG pipeline:
                        // 1. Embeds incoming question
                        // 2. Runs similarity search in pgvector
                        // 3. Appends top-5 chunks to the prompt as context
                        // 4. Model answers from the retrieved code
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(5)
                                        .similarityThreshold(0.65)
                                        .build())
                                .build()
                )
                .build();
    }

    // ── Code Review Client ─────────────────────────────────────────────────────
    // Used by: CodeReviewService
    // NO QuestionAnswerAdvisor — code review gets the raw snippet, not RAG.
    // Temperature is 0 for deterministic, consistent reviews.
    @Bean
    public ChatClient reviewChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                You are a senior Java engineer performing a thorough code review.

                For each review, examine the code for:
                1. BUGS — null pointer risks, resource leaks, incorrect logic,
                          unhandled exceptions, concurrency issues
                2. SECURITY — SQL injection, XSS, insecure deserialization,
                              hardcoded credentials, OWASP Top 10
                3. PERFORMANCE — N+1 queries, missing indexes, O(n²) loops,
                                 unnecessary object creation, blocking I/O
                4. STYLE — Spring Boot best practices, naming conventions,
                           missing tests, unclear variable names

                Be specific. Reference line numbers where possible.
                If the code is clean, say so clearly.
                """)
                .build();
    }

    // ── Memory for multi-turn conversations ───────────────────────────────────
    // InMemoryChatMemory stores messages per session ID in a ConcurrentHashMap.
    // For production: swap with a Redis-backed or DB-backed implementation.
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }
}