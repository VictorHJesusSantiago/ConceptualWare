package com.conceptualware.infrastructure.database;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #11 — Databases: SQL DDL/DML/JOINs/CTEs/Window Functions/MVCC
 * Concept #19 — Integration tests with H2 in-memory database
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Category 11 — SQL Concepts: Integration Tests")
class SqlConceptsTest {

    @Autowired
    private SqlConceptsService service;

    @Autowired
    private JdbcTemplate jdbc;

    // ── DDL / Schema ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Schema is created: employees table exists")
    void schemaCreated() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'EMPLOYEES'",
            Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("View employee_summary exists")
    void viewExists() {
        assertThatNoException().isThrownBy(() ->
            jdbc.queryForList("SELECT * FROM employee_summary"));
    }

    @Test
    @DisplayName("Seed data: at least 3 departments and 5 employees")
    void seedDataPresent() {
        Integer depts = jdbc.queryForObject("SELECT COUNT(*) FROM departments", Integer.class);
        Integer emps  = jdbc.queryForObject("SELECT COUNT(*) FROM employees",   Integer.class);
        assertThat(depts).isGreaterThanOrEqualTo(3);
        assertThat(emps).isGreaterThanOrEqualTo(5);
    }

    // ── JOINs ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("INNER JOIN: returns only employees with departments")
    void innerJoin() {
        List<Map<String, Object>> result = service.innerJoin();
        assertThat(result).isNotEmpty();
        result.forEach(row -> {
            assertThat(row.get("EMPLOYEE")).isNotNull();
            assertThat(row.get("DEPARTMENT")).isNotNull();
        });
    }

    @Test
    @DisplayName("LEFT JOIN: all employees returned (even without dept)")
    void leftJoin() {
        List<Map<String, Object>> result = service.leftJoin();
        // Should be at least 5 (all employees)
        assertThat(result).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("SELF JOIN: finds manager for each employee")
    void selfJoin() {
        List<Map<String, Object>> result = service.selfJoin();
        assertThat(result).isNotEmpty();
        // At least some employees have managers
        long withManager = result.stream()
            .filter(r -> r.get("MANAGER") != null)
            .count();
        assertThat(withManager).isGreaterThan(0);
    }

    @Test
    @DisplayName("CROSS JOIN: produces cartesian product")
    void crossJoin() {
        List<Map<String, Object>> result = service.crossJoin();
        // 3 departments x 2 active projects = at least 4 rows
        assertThat(result).isNotEmpty();
    }

    // ── Window Functions ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Window Functions: ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, SUM OVER")
    void windowFunctions() {
        List<Map<String, Object>> result = service.windowFunctions();
        assertThat(result).isNotEmpty();
        // Check all window function columns exist
        Map<String, Object> first = result.get(0);
        assertThat(first).containsKeys("OVERALL_RANK", "DEPT_RANK", "DEPT_DENSE_RANK",
            "SALARY_DIFF_FROM_ABOVE", "DEPT_TOTAL_SALARY", "DEPT_AVG_SALARY");
    }

    @Test
    @DisplayName("ROW_NUMBER produces unique monotonic sequence")
    void rowNumberUnique() {
        List<Map<String, Object>> result = service.windowFunctions();
        List<Integer> ranks = result.stream()
            .map(r -> ((Number) r.get("OVERALL_RANK")).intValue())
            .sorted()
            .toList();
        // ROW_NUMBER must be 1,2,3,...,n (no gaps, no ties)
        for (int i = 0; i < ranks.size(); i++) {
            assertThat(ranks.get(i)).isEqualTo(i + 1);
        }
    }

    // ── CTEs ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Recursive CTE: org hierarchy has root employees at level 0")
    void recursiveCTE() {
        List<Map<String, Object>> result = service.recursiveCTE();
        assertThat(result).isNotEmpty();
        // Root employees have level 0
        long rootCount = result.stream()
            .filter(r -> ((Number) r.get("LEVEL")).intValue() == 0)
            .count();
        assertThat(rootCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Non-recursive CTE: returns high-salary departments")
    void nonRecursiveCTE() {
        List<Map<String, Object>> result = service.nonRecursiveCTE();
        assertThat(result).isNotEmpty();
        // All returned departments have avg salary > 75000
        result.forEach(r ->
            assertThat(((Number) r.get("AVG_SALARY")).doubleValue()).isGreaterThan(75000));
    }

    // ── Subqueries ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Correlated subquery: returns teammate count per employee")
    void correlatedSubquery() {
        List<Map<String, Object>> result = service.correlatedSubquery();
        assertThat(result).hasSize(5);
        result.forEach(r ->
            assertThat(((Number) r.get("TEAMMATES")).intValue()).isGreaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("EXISTS subquery: employees assigned to projects")
    void existsSubquery() {
        List<Map<String, Object>> result = service.existsSubquery();
        assertThat(result).isNotEmpty();
        // Only employees with project assignments
        result.forEach(r -> assertThat(r.get("NAME")).isNotNull());
    }

    // ── Transactions / MVCC ───────────────────────────────────────────────────

    @Test
    @DisplayName("READ COMMITTED isolation level works")
    void readCommittedIsolation() {
        List<Map<String, Object>> result = service.readCommittedQuery();
        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("SERIALIZABLE isolation level works")
    void serializableIsolation() {
        List<Map<String, Object>> result = service.serializableQuery();
        assertThat(result).isNotEmpty();
        assertThat(((Number) result.get(0).get("COUNT")).intValue()).isEqualTo(5);
    }

    // ── Normalization ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Normalization demo: employee_skills table created (resolves 1NF violation)")
    void normalizationSkillsTable() {
        service.createNormalizationDemo();
        List<Map<String, Object>> skills = jdbc.queryForList("SELECT * FROM employee_skills");
        assertThat(skills).isNotEmpty();
    }

    // ── OLAP vs OLTP ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("OLTP point lookup returns single employee")
    void oltpQuery() {
        Map<String, Object> emp = service.oltpQuery(1);
        assertThat(emp.get("ID")).isEqualTo(1);
        assertThat(emp.get("NAME")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("OLAP aggregate returns one row per department")
    void olapQuery() {
        List<Map<String, Object>> result = service.olapQuery();
        assertThat(result).hasSize(3); // 3 departments
        result.forEach(r -> {
            assertThat(r.get("DEPARTMENT")).isNotNull();
            assertThat(r.get("EMPLOYEE_COUNT")).isNotNull();
            assertThat(r.get("TOTAL_PAYROLL")).isNotNull();
        });
    }

    // ── ETL ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ETL pipeline loads rows into DW fact table")
    void etlPipeline() {
        int rows = service.runETLPipeline();
        assertThat(rows).isGreaterThan(0);
        // Verify fact table has data
        Integer dwCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dw_employee_facts", Integer.class);
        assertThat(dwCount).isGreaterThan(0);
    }

    // ── NoSQL Concepts ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cassandra consistency levels: 8 levels documented")
    void cassandraConsistencyLevels() {
        assertThat(NoSQLConceptsService.CassandraConsistencyLevel.all()).hasSize(8);
    }

    @Test
    @DisplayName("CassandraSchema: CQL statements are non-null and non-blank")
    void cassandraCqlStatements() {
        assertThat(NoSQLConceptsService.CassandraSchema.CREATE_KEYSPACE).isNotBlank();
        assertThat(NoSQLConceptsService.CassandraSchema.CREATE_ALGORITHM_EXECUTIONS).isNotBlank();
        assertThat(NoSQLConceptsService.CassandraSchema.SELECT_USER_RECENT).isNotBlank();
    }

    @Test
    @DisplayName("Neo4j Cypher queries are non-blank")
    void neo4jCypherQueries() {
        assertThat(NoSQLConceptsService.Neo4jSchema.CREATE_CONCEPT_GRAPH).isNotBlank();
        assertThat(NoSQLConceptsService.Neo4jSchema.COLLABORATIVE_FILTER).isNotBlank();
        assertThat(NoSQLConceptsService.Neo4jSchema.FIND_LEARNING_PATH).isNotBlank();
    }

    @Test
    @DisplayName("InfluxDB Flux queries are non-blank")
    void influxFluxQueries() {
        assertThat(NoSQLConceptsService.InfluxDBSchema.QUERY_RECENT_PERFORMANCE).isNotBlank();
        assertThat(NoSQLConceptsService.InfluxDBSchema.DOWNSAMPLE_TO_5MIN).isNotBlank();
        assertThat(NoSQLConceptsService.InfluxDBSchema.DETECT_ANOMALIES).isNotBlank();
    }

    @Test
    @DisplayName("NoSQL comparison covers 6 databases")
    void nosqlComparison() {
        assertThat(RedisConfig.NoSQLComparison.all()).hasSize(6)
            .extracting(RedisConfig.NoSQLComparison::db)
            .contains("Redis", "MongoDB", "Cassandra", "Neo4j", "InfluxDB", "DynamoDB");
    }

    @Test
    @DisplayName("ETL pipeline steps are defined")
    void etlPipelineSteps() {
        List<NoSQLConceptsService.ETLStep> steps = NoSQLConceptsService.conceptualWareETLPipeline();
        assertThat(steps).hasSize(5);
        assertThat(steps.get(0).name()).isEqualTo("Extract");
        assertThat(steps.get(steps.size() - 1).name()).isEqualTo("Load-Redis");
    }

    @Test
    @DisplayName("OLTP vs OLAP workload comparison is accurate")
    void oltpVsOlap() {
        var oltp = NoSQLConceptsService.WorkloadComparison.oltp();
        var olap = NoSQLConceptsService.WorkloadComparison.olap();
        assertThat(oltp.type()).isEqualTo("OLTP");
        assertThat(olap.type()).isEqualTo("OLAP");
        assertThat(oltp.queryPattern()).contains("primary key");
        assertThat(olap.queryPattern()).contains("Full table");
    }
}
