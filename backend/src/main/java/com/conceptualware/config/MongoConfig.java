package com.conceptualware.config;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

/**
 * Concept #11 — Databases: MongoDB configuration, transactions, auditing
 * Concept #12 — Architecture: Infrastructure layer config
 * Concept #13 — Design Patterns: Template Method (MongoTemplate)
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    /**
     * Remove the "_class" field from all documents (cleaner documents, less coupling).
     * Concept #11 — Document modeling, schema design
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MappingMongoConverter converter) {
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return new MongoTemplate(factory, converter);
    }

    /**
     * Multi-document ACID transactions (requires replica set).
     * Concept #11 — ACID, transactions, isolation levels
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
