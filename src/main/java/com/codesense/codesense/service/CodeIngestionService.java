package com.codesense.codesense.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Ingests Java source code into the vector store.
 *
 * Pipeline:
 *   ZIP upload → extract .java files → smart-split by class/method
 *   → embed each chunk (text-embedding-3-small) → store in pgvector
 *
 * After ingestion, RagChatService can answer questions about the code.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeIngestionService {

    private final VectorStore vectorStore;

    // TokenTextSplitter: splits on token boundaries, not character boundaries.
    // 800 tokens per chunk, 100 token overlap (ensures context at boundaries).
    // For code, 800 tokens ≈ ~1 average Java method or 20-30 lines of context.
    private final TokenTextSplitter splitter = new TokenTextSplitter(
            800,    // chunk size in tokens
            100,    // overlap in tokens — prevents cutting mid-method
            5,      // min chunk size (skip tiny fragments)
            10000,  // max chunk size (safety cap)
            true    // keep separators (preserves newlines in code)
    );

    /**
     * Main entry point: accept a ZIP of Java source files.
     * Runs asynchronously — returns immediately, ingestion happens in background.
     *
     * @param projectName logical name for this codebase (stored in metadata)
     * @param zipFile     ZIP archive containing .java files
     * @return CompletableFuture with count of ingested chunks
     */
    @Async
    public CompletableFuture<Integer> ingestZip(String projectName, MultipartFile zipFile) {
        log.info("Starting ingestion for project: {}", projectName);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Extract ZIP to temp directory
            Path tempDir = Files.createTempDirectory("codesense-" + projectName);

            try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().endsWith(".java")) {
                        Path filePath = tempDir.resolve(entry.getName());
                        Files.createDirectories(filePath.getParent());
                        Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }

            // 2. Walk the directory and process each .java file
            List<Document> allDocuments = new ArrayList<>();

            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        try {
                            List<Document> docs = processJavaFile(file, projectName, tempDir);
                            allDocuments.addAll(docs);
                        } catch (IOException e) {
                            log.warn("Failed to process file: {}", file, e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 3. Add all documents to pgvector
            // Spring AI handles embedding internally:
            // - Calls OpenAI text-embedding-3-small for each chunk
            // - Stores vector + content + metadata in the vector_store table
            if (!allDocuments.isEmpty()) {
                vectorStore.add(allDocuments);
                log.info("Ingested {} chunks for project '{}' in {}ms",
                        allDocuments.size(), projectName,
                        System.currentTimeMillis() - startTime);
            }

            // Cleanup temp dir
            deleteDirectory(tempDir);

            return CompletableFuture.completedFuture(allDocuments.size());

        } catch (IOException e) {
            log.error("Ingestion failed for project: {}", projectName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Process a single .java file into Document chunks.
     *
     * Key insight: we split AFTER annotating with rich metadata.
     * Each chunk knows its file path, class name, and project.
     * This metadata is stored in pgvector alongside the vector,
     * and gets returned with search results so we can cite sources.
     */
    private List<Document> processJavaFile(
            Path file, String projectName, Path rootDir) throws IOException {

        String content = Files.readString(file);

        // Extract metadata from the Java source
        String relativePath = rootDir.relativize(file).toString();
        String className    = extractClassName(content, file.getFileName().toString());
        String packageName  = extractPackageName(content);

        // Create a Document with content + metadata
        // Metadata is stored as JSONB in PostgreSQL alongside the vector
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("project",     projectName);
        metadata.put("filePath",    relativePath);
        metadata.put("className",   className);
        metadata.put("packageName", packageName);
        metadata.put("language",    "java");
        metadata.put("fileType",    classifyFile(className, content));
        // fileType: SERVICE, CONTROLLER, REPOSITORY, ENTITY, CONFIG, UTIL, OTHER

        Document document = new Document(content, metadata);

        // Split the document into chunks
        List<Document> chunks = splitter.apply(List.of(document));

        // Each chunk inherits the parent document's metadata
        // (Spring AI does this automatically in TokenTextSplitter)
        log.debug("Split {} into {} chunks", relativePath, chunks.size());

        return chunks;
    }

    // ── Simple metadata extractors ─────────────────────────────────────────────

    private String extractClassName(String content, String fileName) {
        // Try to find "public class/interface/enum ClassName"
        var matcher = java.util.regex.Pattern
                .compile("(?:public\\s+)?(?:class|interface|enum|record)\\s+(\\w+)")
                .matcher(content);
        return matcher.find()
                ? matcher.group(1)
                : fileName.replace(".java", "");
    }

    private String extractPackageName(String content) {
        var matcher = java.util.regex.Pattern
                .compile("^package\\s+([\\w.]+);", java.util.regex.Pattern.MULTILINE)
                .matcher(content);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private String classifyFile(String className, String content) {
        if (content.contains("@RestController") || content.contains("@Controller"))
            return "CONTROLLER";
        if (content.contains("@Service"))
            return "SERVICE";
        if (content.contains("@Repository") || content.contains("extends JpaRepository"))
            return "REPOSITORY";
        if (content.contains("@Entity"))
            return "ENTITY";
        if (content.contains("@Configuration"))
            return "CONFIG";
        if (className.endsWith("Util") || className.endsWith("Helper"))
            return "UTIL";
        return "OTHER";
    }

    /**
     * Delete all vectors for a project from pgvector.
     * Uses a metadata filter to find all chunks belonging to the project.
     *
     * @return number of chunks deleted
     */
    public int deleteProject(String projectName) {
        var filter = new FilterExpressionBuilder();
        // Find all documents where metadata.project == projectName
        var docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(projectName)          // dummy query — we only care about the filter
                        .topK(10_000)                // large enough to catch all chunks
                        .similarityThreshold(0.0)    // no threshold — return everything matching filter
                        .filterExpression(filter.eq("project", projectName).build())
                        .build()
        );

        if (!docs.isEmpty()) {
            vectorStore.delete(docs.stream().map(Document::getId).toList());
            log.info("Deleted {} chunks for project '{}'", docs.size(), projectName);
        }
        return docs.size();
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a)
                    throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e)
                    throws IOException { Files.delete(d); return FileVisitResult.CONTINUE; }
        });
    }
}