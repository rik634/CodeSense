package com.codesense.codesense.service;

import com.codesense.codesense.model.CodeReview;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CodeReviewService {

    private final ChatClient reviewChatClient;
    // BeanOutputConverter inspects CodeReview's structure, generates a JSON schema,
    // appends it to the prompt, and parses the LLM's JSON response into a typed object.
    private final BeanOutputConverter<CodeReview> converter = new BeanOutputConverter<>(CodeReview.class);

    public CodeReviewService(@Qualifier("reviewChatClient") ChatClient reviewChatClient) {
        this.reviewChatClient = reviewChatClient;
    }

    /**
     * @Tool — exposes this method to the LLM as a callable function.
     * During a review the model can invoke classifySeverity(issues) to
     * determine the overall severity before composing the final JSON.
     * This teaches the model to reason in steps rather than guess severity.
     */
    @Tool(description = "Classify overall severity from a comma-separated list of issue categories (BUG,SECURITY,PERFORMANCE,STYLE). Returns CRITICAL, HIGH, MEDIUM, LOW, or NONE.")
    public String classifySeverity(String issueCategories) {
        if (issueCategories == null || issueCategories.isBlank()) return "NONE";
        String upper = issueCategories.toUpperCase();
        if (upper.contains("SECURITY")) return "CRITICAL";
        if (upper.contains("BUG"))      return "HIGH";
        if (upper.contains("PERFORMANCE")) return "MEDIUM";
        return "LOW";
    }

    @RateLimiter(name = "llm-api")
    public CodeReview review(String code, String context) {
        String contextNote = (context != null && !context.isBlank()) ? "Context: " + context + "\n\n" : "";

        // converter.getFormat() returns the JSON schema instruction that is
        // appended to the prompt so the LLM knows exactly what shape to return.
        String formatInstructions = converter.getFormat();

        String response = reviewChatClient.prompt()
                .user("""
                %sReview the following Java code thoroughly:

```java
%s
```

                Use the classifySeverity tool to determine overall severity.
                Then return a complete JSON review. %s
                """.formatted(contextNote, code, formatInstructions))
                // Register this service's @Tool methods so the LLM can call classifySeverity
                .tools(this)
                .call()
                .content();

        log.debug("Raw review response: {}", response);
        return converter.convert(response);
    }

    @RateLimiter(name = "llm-api")
    public String quickCheck(String code) {
        return reviewChatClient.prompt()
                .user("""
                Look at this Java code and respond with ONE line only:
                SAFE_TO_MERGE, NEEDS_CHANGES, or CRITICAL_ISSUES

                Code:
```java
%s
```
                """.formatted(code))
                .call()
                .content()
                .trim();
    }
}