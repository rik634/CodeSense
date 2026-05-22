package com.codesense.codesense.model;

import java.util.List;

public record ChatMessage(
        String sessionId,
        String question,
        String answer,
        long   processingTimeMs,
        int    inputTokens,
        int    outputTokens,
        List<String> sources   // file paths cited by QuestionAnswerAdvisor
) {}