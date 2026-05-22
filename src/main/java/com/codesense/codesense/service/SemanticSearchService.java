package com.codesense.codesense.service;

import com.codesense.codesense.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Feature 4: Semantic code search.
 *
 * "Find all places where we handle authentication errors"
 * → returns ranked code snippets matching the MEANING, not just keywords.
 *
 * Unlike grep/IntelliJ search (keyword matching), this uses embedding
 * similarity — "auth error" matches "catch (AuthenticationException e)"
 * even with no shared keywords.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final VectorStore vectorStore;

    /**
     * Search for code semantically similar to the query.
     *
     * @param projectName search only within this project
     * @param query       natural language description of what you're looking for
     * @param topK        how many results to return (default: 10)
     * @param fileType    optional filter: SERVICE, CONTROLLER, REPOSITORY, etc.
     */
    @Cacheable(
            value  = "search-results",
            key    = "#projectName + ':' + #query.hashCode() + ':' + #topK + ':' + #fileType"
    )
    public List<SearchResult> search(
            String projectName,
            String query,
            int    topK,
            String fileType   // null = no filter
    ) {
        log.debug("Semantic search: project={}, query={}, topK={}", projectName, query, topK);

        // Build filter: always scope to project, optionally narrow by fileType
        String filterExpr = (fileType != null && !fileType.isBlank())
                ? "project == '" + projectName + "' && fileType == '" + fileType + "'"
                : "project == '" + projectName + "'";

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.55)   // looser than RAG — search returns more results
                        .filterExpression(filterExpr)
                        .build()
        );

        // Map Document → SearchResult (our clean API model)
        return docs.stream().map(this::toSearchResult).toList();
    }

    /**
     * Find all implementations of a specific concept.
     * E.g., "exception handling patterns", "database transaction usage"
     */
    public List<SearchResult> findPattern(String projectName, String pattern) {
        return search(projectName, pattern, 15, null);
    }

    /**
     * Find code similar to a given snippet.
     * Useful for "find other places that do the same thing as this code".
     */
    public List<SearchResult> findSimilarCode(String projectName, String codeSnippet) {
        // Embed the code snippet directly as the query
        return search(projectName, codeSnippet, 10, null);
    }

    private SearchResult toSearchResult(Document doc) {
        Map<String, Object> meta = doc.getMetadata();

        return new SearchResult(
                (String) meta.getOrDefault("filePath",  "unknown"),
                (String) meta.getOrDefault("className", "unknown"),
                (String) meta.getOrDefault("methodName", null),
                doc.getText(),
                // getScore() returns cosine similarity (0.0 to 1.0)
                doc.getScore() != null ? doc.getScore() : 0.0
        );
    }
}