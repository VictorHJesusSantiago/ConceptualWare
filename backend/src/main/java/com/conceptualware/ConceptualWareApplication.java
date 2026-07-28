package com.conceptualware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ConceptualWare — Developer Intelligence Platform
 *
 * A full-stack application demonstrating all core CS concepts through a real,
 * useful product: an interactive algorithm explorer, data structure playground,
 * coding challenge system, and learning tracker for software engineers.
 *
 * Stack: Java 21 + Spring Boot 3 | TypeScript/Node.js Gateway | React Frontend | MongoDB
 */
@SpringBootApplication
@EnableCaching          // Cache-Aside Pattern (Concept 26)
// @EnableMongoAuditing vive em config/MongoConfig.java — declará-la aqui também
// registrava o bean 'mongoAuditingHandler' duas vezes (BeanDefinitionOverrideException).
@EnableAsync            // Async processing (Concept 18)
@EnableScheduling       // Scheduled tasks (Concept 23 — DevOps automation)
public class ConceptualWareApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConceptualWareApplication.class, args);
    }
}
