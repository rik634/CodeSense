package com.codesense.codesense.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.*;

import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableAutoConfiguration(exclude = PgVectorStoreAutoConfiguration.class)
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}")
    private int dimensions;

    /**
     * PgVectorStore backed by PostgreSQL with the pgvector extension.
     *
     * Spring AI auto-creates the vector_store table on startup
     * (initialize-schema: true in application.yml).
     *
     * The EmbeddingModel bean is auto-configured by
     * spring-ai-openai-spring-boot-starter using the key + model
     * defined in application.yml (text-embedding-3-small, 1536 dims).
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .initializeSchema(true)                    // creates table + index if absent
                .build();
    }
}
