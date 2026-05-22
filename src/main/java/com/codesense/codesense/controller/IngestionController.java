package com.codesense.codesense.controller;

import com.codesense.codesense.service.CodeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final CodeIngestionService ingestionService;

    /**
     * Upload a ZIP of Java source files.
     *
     * curl -X POST http://localhost:8080/api/ingest/upload \
     *   -F "projectName=my-spring-app" \
     *   -F "file=@my-project.zip"
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadProject(
            @RequestParam String projectName,
            @RequestParam MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }
        if (!file.getOriginalFilename().endsWith(".zip")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only ZIP files are accepted"));
        }

        log.info("Received upload for project: {}, size: {} bytes",
                projectName, file.getSize());

        // Kick off async ingestion — returns immediately
        CompletableFuture<Integer> future =
                ingestionService.ingestZip(projectName, file);

        // Non-blocking: client gets 202 Accepted immediately
        // Ingestion runs in the background
        future.thenAccept(count ->
                log.info("Ingestion complete: {} chunks for project {}", count, projectName)
        );

        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "Ingestion started for project: " + projectName,
                        "status",  "PROCESSING"
                ));
    }
}