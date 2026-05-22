package com.codesense.codesense.model;

/**
 * Represents a single ingested code chunk stored in pgvector.
 * Returned by the ingestion API to confirm what was stored.
 */
public record CodeChunk(
        String project,
        String filePath,
        String className,
        String packageName,
        String fileType,    // SERVICE | CONTROLLER | REPOSITORY | ENTITY | CONFIG | UTIL | OTHER
        String language,
        int    chunkIndex,  // position of this chunk within the file (0-based)
        String content      // the actual code text of this chunk
) {}
