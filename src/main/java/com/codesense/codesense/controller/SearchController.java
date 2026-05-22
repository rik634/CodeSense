package com.codesense.codesense.controller;

import com.codesense.codesense.model.SearchResult;
import com.codesense.codesense.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SemanticSearchService searchService;

    /**
     * Semantic search across your codebase.
     *
     * curl "http://localhost:8080/api/search?project=my-app&q=exception+handling&topK=5"
     * curl "http://localhost:8080/api/search?project=my-app&q=database+queries&fileType=REPOSITORY"
     */
    @GetMapping
    public ResponseEntity<List<SearchResult>> search(
            @RequestParam("project") String projectName,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int topK,
            @RequestParam(required = false) String fileType
    ) {
        List<SearchResult> results = searchService.search(projectName, query, topK, fileType);
        return ResponseEntity.ok(results);
    }

    /**
     * Find code similar to a given snippet.
     */
    @PostMapping("/similar")
    public ResponseEntity<List<SearchResult>> findSimilar(
            @RequestParam("project") String projectName,
            @RequestBody String codeSnippet
    ) {
        List<SearchResult> results = searchService.findSimilarCode(projectName, codeSnippet);
        return ResponseEntity.ok(results);
    }
}