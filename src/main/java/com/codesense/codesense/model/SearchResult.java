package com.codesense.codesense.model;

/**
 * A single result from semantic code search.
 */
public record SearchResult(
        String filePath,      // e.g. "src/main/java/UserService.java"
        String className,     // e.g. "UserService"
        String methodName,    // e.g. "findByEmail" (null if class-level)
        String codeSnippet,   // the actual code
        double similarity     // 0.0 - 1.0, how relevant this is to the query
) {}