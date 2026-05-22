package com.codesense.codesense.service;

import com.codesense.codesense.model.ChatMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import com.codesense.codesense.config.AiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * Feature 2: Natural language Q&A over your codebase using RAG.
 *
 * How each call works (QuestionAnswerAdvisor does this automatically):
 *   1. User asks: "How does the payment service calculate tax?"
 *   2. Spring AI embeds the question → 1536-dim vector
 *   3. Searches pgvector for the 5 most similar code chunks
 *   4. Appends those chunks to the prompt as context
 *   5. LLM generates an answer grounded in the actual code
 *   6. Returns answer (with source file references)
 */
@Service
@Slf4j
public class RagChatService {

    private final ChatClient ragChatClient;
    private final MeterRegistry meterRegistry;

    public RagChatService(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            MeterRegistry meterRegistry
    ) {
        this.ragChatClient = ragChatClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Synchronous RAG answer.
     * Cached by question hash — identical questions return cached response.
     *
     * @param projectName filter retrieval to this project's code
     * @param question    the user's natural language question
     */
    @Cacheable(
            value      = "rag-answers",
            key         = "#projectName + ':' + #question.toLowerCase().trim().hashCode()",
            unless      = "#result == null"
    )
    @RateLimiter(name = "llm-api", fallbackMethod = "fallbackAnswer")
    @CircuitBreaker(name = "llm-api", fallbackMethod = "fallbackAnswer")
    public ChatMessage ask(String projectName, String question) {
        long start = System.currentTimeMillis();

        ChatResponse response = ragChatClient.prompt()
                // Dynamically append the project filter to the system prompt.
                // QuestionAnswerAdvisor will use this to filter pgvector results
                // so you only get code from THIS project, not everything in the DB.
                .system(AiConfig.RAG_SYSTEM_PROMPT + "\nOnly reference code from project: " + projectName)
                .user(question)
                .call()
                .chatResponse();

        long elapsed = System.currentTimeMillis() - start;
        Usage usage  = response.getMetadata().getUsage();

        // Track token usage as metrics (visible in Prometheus/Grafana)
        recordMetrics(usage, elapsed, "rag-chat");

        List<String> sources = extractSources(response);

        return new ChatMessage(
                null,
                question,
                response.getResult().getOutput().getText(),
                elapsed,
                (int) usage.getPromptTokens(),
                (int) usage.getCompletionTokens(),
                sources
        );
    }

    /**
     * Streaming RAG answer — for responsive UIs.
     * Returns a Flux<String> where each item is a token chunk.
     * Wire this to a Server-Sent Events endpoint for real-time streaming.
     *
     * IMPORTANT: Streaming responses are NOT cached (can't cache a stream).
     * Use the sync ask() for cacheable responses.
     */
    @RateLimiter(name = "llm-api")
    @CircuitBreaker(name = "llm-api")
    public Flux<String> stream(String projectName, String question) {
        return ragChatClient.prompt()
                .system(AiConfig.RAG_SYSTEM_PROMPT + "\nOnly reference code from project: " + projectName)
                .user(question)
                .stream()
                .content();  // Flux<String> — one item per token
    }

    /**
     * Fallback when OpenAI is rate-limited or down.
     * Resilience4j calls this automatically when the real method fails.
     */
    public ChatMessage fallbackAnswer(String projectName, String question, Exception e) {
        log.warn("LLM unavailable for project {}: {}", projectName, e.getMessage());
        return new ChatMessage(
                null, question,
                "CodeSense is temporarily unavailable. Please try again in a moment.",
                0, 0, 0, Collections.emptyList()
        );
    }

    /**
     * QuestionAnswerAdvisor stores retrieved documents in response metadata.
     * Key: QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS
     * Each Document has a "source" metadata entry = the file path set at ingestion time.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractSources(ChatResponse response) {
        try {
            Object raw = response.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
            if (raw instanceof List<?> docs) {
                return ((List<Document>) docs).stream()
                        .map(d -> (String) d.getMetadata().getOrDefault("source", "unknown"))
                        .distinct()
                        .toList();
            }
        } catch (Exception e) {
            log.debug("Could not extract sources from response metadata", e);
        }
        return Collections.emptyList();
    }

    private void recordMetrics(Usage usage, long elapsedMs, String operation) {
        Tags tags = Tags.of("operation", operation, "model", "gpt-4o-mini");

        meterRegistry.counter("ai.tokens.input",  tags)
                .increment(usage.getPromptTokens());
        meterRegistry.counter("ai.tokens.output", tags)
                .increment(usage.getCompletionTokens());
        meterRegistry.timer("ai.latency", tags)
                .record(java.time.Duration.ofMillis(elapsedMs));
    }
}