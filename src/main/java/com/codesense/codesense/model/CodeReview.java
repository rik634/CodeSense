package com.codesense.codesense.model;

import java.util.List;

/**
 * Structured output from the AI code reviewer.
 * Spring AI serializes/deserializes this automatically via BeanOutputConverter.
 */
public record CodeReview(

        /**
         * Overall severity: CRITICAL, HIGH, MEDIUM, LOW, NONE
         */
        String severity,

        /**
         * List of specific issues found
         */
        List<Issue> issues,

        /**
         * 2-sentence overall assessment
         */
        String summary,

        /**
         * Whether the code is production-ready
         */
        boolean productionReady

) {
    public record Issue(
            int    lineNumber,   // approximate line where issue occurs
            String category,     // BUG | SECURITY | PERFORMANCE | STYLE
            String description,  // what the issue is
            String suggestion    // how to fix it
    ) {}
}