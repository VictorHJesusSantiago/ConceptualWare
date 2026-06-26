package com.conceptualware.infrastructure.database;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Concept #11 — NoSQL Databases: Cassandra, Neo4j, InfluxDB (documentação e modelos):
 *
 *   CASSANDRA — Wide-Column Store:
 *     - Data model: keyspace → table → partition key → clustering columns → columns.
 *     - CQL (Cassandra Query Language): SQL-like but fundamentally different semantics.
 *     - Partitioning: data distributed by partition key via consistent hashing.
 *     - Replication: configurable RF (Replication Factor), multi-datacenter.
 *     - No JOINs: denormalization is required — design tables around query patterns.
 *     - Tunable consistency: ONE, QUORUM, ALL per read/write.
 *     - Best for: time-series, IoT, write-heavy, geo-distributed (Netflix, Twitter).
 *
 *   NEO4J — Graph Database:
 *     - Data model: Nodes (vertices) + Relationships (edges) + Properties.
 *     - Cypher query language: MATCH (n:Person)-[:KNOWS]->(m:Person) RETURN n,m.
 *     - ACID transactions, native graph storage (no adjacency matrix).
 *     - Best for: social networks, recommendation engines, fraud detection, knowledge graphs.
 *
 *   INFLUXDB — Time-Series Database:
 *     - Data model: measurement → tags (indexed) → fields (values) → timestamp.
 *     - Flux query language (v2+): functional, pipeline-based.
 *     - Automatic data retention policies and downsampling.
 *     - Best for: metrics, monitoring, IoT sensor data, financial tick data.
 *
 * Concept #11 — NoSQL data models, query languages, consistency models
 */
@Service
public class NoSQLConceptsService {

    // ── Cassandra Schema (CQL) ─────────────────────────────────────────────────

    /**
     * CQL schema for ConceptualWare's algorithm execution logs.
     * Design principle: ONE TABLE PER QUERY PATTERN (denormalize for read performance).
     */
    public static class CassandraSchema {

        public static final String CREATE_KEYSPACE = """
            CREATE KEYSPACE IF NOT EXISTS conceptualware
            WITH REPLICATION = {
                'class': 'NetworkTopologyStrategy',
                'datacenter1': 3
            }
            AND DURABLE_WRITES = true;
            """;

        // Partition by user_id: all executions for a user on same node
        // Clustering by executed_at DESC: most recent first without ALLOW FILTERING
        public static final String CREATE_ALGORITHM_EXECUTIONS = """
            CREATE TABLE IF NOT EXISTS conceptualware.algorithm_executions (
                user_id       UUID,
                executed_at   TIMESTAMP,
                algorithm_id  UUID,
                algorithm_name TEXT,
                input_size    INT,
                duration_ms   BIGINT,
                complexity    TEXT,
                success       BOOLEAN,
                PRIMARY KEY ((user_id), executed_at, algorithm_id)
            ) WITH CLUSTERING ORDER BY (executed_at DESC)
              AND gc_grace_seconds = 864000
              AND compaction = {'class': 'TimeWindowCompactionStrategy',
                                'compaction_window_unit': 'DAYS',
                                'compaction_window_size': 1};
            """;

        // Secondary table for querying by algorithm (different partition key)
        public static final String CREATE_ALGORITHM_STATS = """
            CREATE TABLE IF NOT EXISTS conceptualware.algorithm_stats_by_name (
                algorithm_name TEXT,
                date           DATE,
                hour           INT,
                total_runs     COUNTER,
                total_duration COUNTER,
                PRIMARY KEY ((algorithm_name, date), hour)
            );
            """;

        // CQL queries (prepared statement style)
        public static final String INSERT_EXECUTION = """
            INSERT INTO conceptualware.algorithm_executions
                (user_id, executed_at, algorithm_id, algorithm_name, input_size, duration_ms, complexity, success)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            USING TTL 7776000;
            """; // TTL: 90 days

        public static final String SELECT_USER_RECENT = """
            SELECT * FROM conceptualware.algorithm_executions
            WHERE user_id = ?
            LIMIT 50;
            """; // Uses partition key → no ALLOW FILTERING needed

        public static final String UPDATE_COUNTER = """
            UPDATE conceptualware.algorithm_stats_by_name
            SET total_runs = total_runs + 1,
                total_duration = total_duration + ?
            WHERE algorithm_name = ? AND date = ? AND hour = ?;
            """;
    }

    // ── Cassandra concepts ─────────────────────────────────────────────────────

    public record CassandraConsistencyLevel(
        String level, String description, int nodesRequired, boolean strong
    ) {
        public static CassandraConsistencyLevel[] all() {
            return new CassandraConsistencyLevel[]{
                new CassandraConsistencyLevel("ONE",         "Any single replica responds",                1,    false),
                new CassandraConsistencyLevel("TWO",         "Any two replicas respond",                   2,    false),
                new CassandraConsistencyLevel("THREE",       "Any three replicas respond",                 3,    false),
                new CassandraConsistencyLevel("QUORUM",      "(RF/2)+1 replicas must respond",             -1,   true),
                new CassandraConsistencyLevel("ALL",         "All replicas must respond",                  -1,   true),
                new CassandraConsistencyLevel("LOCAL_QUORUM","Quorum in local datacenter only",            -1,   true),
                new CassandraConsistencyLevel("EACH_QUORUM", "Quorum in every datacenter",                 -1,   true),
                new CassandraConsistencyLevel("SERIAL",      "Lightweight transactions (Paxos-based)",     -1,   true),
            };
        }
    }

    // ── Neo4j Graph Model (Cypher) ─────────────────────────────────────────────

    public static class Neo4jSchema {

        // Node labels and relationship types for ConceptualWare's concept graph
        public static final String CREATE_CONSTRAINTS = """
            CREATE CONSTRAINT user_id_unique IF NOT EXISTS
                FOR (u:User) REQUIRE u.id IS UNIQUE;

            CREATE CONSTRAINT concept_id_unique IF NOT EXISTS
                FOR (c:Concept) REQUIRE c.id IS UNIQUE;

            CREATE INDEX concept_name IF NOT EXISTS
                FOR (c:Concept) ON (c.name);
            """;

        // Cypher: Create knowledge graph
        public static final String CREATE_CONCEPT_GRAPH = """
            // Create concepts (nodes)
            CREATE (ds:Concept {id: 'data-structures', name: 'Data Structures', category: 4})
            CREATE (algo:Concept {id: 'algorithms', name: 'Algorithms', category: 5})
            CREATE (rbt:Concept {id: 'red-black-tree', name: 'Red-Black Tree', category: 4})
            CREATE (skip:Concept {id: 'skip-list', name: 'Skip List', category: 4})
            CREATE (dp:Concept {id: 'dynamic-programming', name: 'Dynamic Programming', category: 5})

            // Create relationships (edges)
            CREATE (rbt)-[:IS_A]->(ds)
            CREATE (skip)-[:IS_A]->(ds)
            CREATE (dp)-[:IS_A]->(algo)
            CREATE (rbt)-[:RELATED_TO]->(skip)
            """;

        // Cypher: Find all concepts a user has learned with their prerequisites
        public static final String FIND_LEARNING_PATH = """
            MATCH (user:User {id: $userId})-[:LEARNED]->(known:Concept)
            MATCH (target:Concept {id: $targetId})
            MATCH path = shortestPath((known)-[:PREREQUISITE_FOR*]->(target))
            RETURN path, [node in nodes(path) | node.name] AS learning_path
            ORDER BY length(path)
            LIMIT 1
            """;

        // Cypher: Recommendation — users who learned X also learned Y
        public static final String COLLABORATIVE_FILTER = """
            MATCH (me:User {id: $userId})-[:LEARNED]->(c:Concept)
            MATCH (similar:User)-[:LEARNED]->(c)
            WHERE similar <> me
            MATCH (similar)-[:LEARNED]->(recommended:Concept)
            WHERE NOT (me)-[:LEARNED]->(recommended)
            RETURN recommended.name AS concept, COUNT(*) AS score
            ORDER BY score DESC
            LIMIT 10
            """;

        // Cypher: Shortest path between two concepts
        public static final String CONCEPT_RELATIONSHIP_PATH = """
            MATCH p = shortestPath(
                (start:Concept {name: $startName})-[*]-(end:Concept {name: $endName})
            )
            RETURN p, length(p) AS distance
            """;

        // Cypher: PageRank-like concept importance
        public static final String CONCEPT_CENTRALITY = """
            CALL gds.pageRank.stream('concept-graph')
            YIELD nodeId, score
            RETURN gds.util.asNode(nodeId).name AS concept, score
            ORDER BY score DESC
            LIMIT 20
            """;
    }

    // ── InfluxDB Time-Series Model (Flux) ─────────────────────────────────────

    public static class InfluxDBSchema {

        // Flux: Write algorithm execution metrics
        public static final String WRITE_POINT_FORMAT =
            "algorithm_runs,algorithm=%s,user=%s,status=%s duration_ms=%d,input_size=%d %d";

        // Flux: Query last 1 hour of algorithm performance
        public static final String QUERY_RECENT_PERFORMANCE = """
            from(bucket: "metrics")
              |> range(start: -1h)
              |> filter(fn: (r) => r["_measurement"] == "algorithm_runs")
              |> filter(fn: (r) => r["_field"] == "duration_ms")
              |> group(columns: ["algorithm"])
              |> mean()
              |> sort(columns: ["_value"], desc: true)
            """;

        // Flux: Downsampling — aggregate to 5-minute windows
        public static final String DOWNSAMPLE_TO_5MIN = """
            from(bucket: "metrics")
              |> range(start: -24h)
              |> filter(fn: (r) => r._measurement == "algorithm_runs")
              |> aggregateWindow(every: 5m, fn: mean, createEmpty: false)
              |> to(bucket: "metrics-downsampled")
            """;

        // Flux: Anomaly detection — flag runs > 2 stddev from mean
        public static final String DETECT_ANOMALIES = """
            data = from(bucket: "metrics")
              |> range(start: -1h)
              |> filter(fn: (r) => r._measurement == "algorithm_runs")
              |> filter(fn: (r) => r._field == "duration_ms")

            mean = data |> mean()
            stddev = data |> stddev()

            join(tables: {mean: mean, stddev: stddev}, on: ["algorithm"])
              |> map(fn: (r) => ({r with threshold: r._value_mean + 2.0 * r._value_stddev}))
            """;

        // Flux: Count executions per algorithm per day (time histogram)
        public static final String DAILY_HISTOGRAM = """
            from(bucket: "metrics")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "algorithm_runs")
              |> filter(fn: (r) => r._field == "duration_ms")
              |> aggregateWindow(every: 1d, fn: count, createEmpty: false)
              |> group(columns: ["algorithm"])
            """;
    }

    // ── ETL Pipeline (Extract → Transform → Load) ─────────────────────────────

    public record ETLStep(String name, String description, String tool, String outputFormat) {}

    public static List<ETLStep> conceptualWareETLPipeline() {
        return List.of(
            new ETLStep("Extract",      "Read algorithm execution logs from MongoDB",
                "Spring Data MongoDB + MQL aggregation",  "BSON documents"),
            new ETLStep("Transform",    "Normalize timestamps, fill nulls, compute derived metrics",
                "Java Stream API + MapReduce logic",      "Java POJOs"),
            new ETLStep("Load-SQL",     "Insert into H2 DW fact table for OLAP queries",
                "Spring JDBC + batch INSERT",             "Relational rows"),
            new ETLStep("Load-Influx",  "Write time-series metrics to InfluxDB",
                "InfluxDB Flux write API",                "Line Protocol points"),
            new ETLStep("Load-Redis",   "Cache aggregated results for dashboard",
                "Spring Data Redis + TTL",                "JSON strings")
        );
    }

    // ── OLTP vs OLAP comparison ───────────────────────────────────────────────

    public record WorkloadComparison(
        String type, String operations, String queryPattern,
        String indexStrategy, String optimizedFor, String examples
    ) {
        public static WorkloadComparison oltp() {
            return new WorkloadComparison("OLTP",
                "INSERT, UPDATE, DELETE, single-row SELECT",
                "Point lookups by primary key",
                "B-Tree indexes on frequently queried columns",
                "Low latency (< 10ms), high concurrency, many small transactions",
                "MongoDB user lookups, Redis rate limiting, H2 employee CRUD");
        }

        public static WorkloadComparison olap() {
            return new WorkloadComparison("OLAP",
                "Complex SELECT with aggregations, GROUP BY, JOIN",
                "Full table scans, range queries, data cube analysis",
                "Columnar storage, bitmap indexes, partitioning",
                "High throughput, complex aggregations, analytics over large data",
                "H2 Window Functions, InfluxDB time aggregations, data warehouse queries");
        }
    }

    // ── Data Warehouse star schema ────────────────────────────────────────────

    /**
     * Star Schema: one central fact table surrounded by dimension tables.
     * Optimized for OLAP: denormalized, pre-joined dimensions, fast aggregations.
     */
    public static class DataWarehouseSchema {

        // Fact table: one row per algorithm execution event
        public static final String FACT_TABLE = """
            CREATE TABLE IF NOT EXISTS fact_algorithm_executions (
                execution_id   BIGINT PRIMARY KEY,
                date_id        INT,        -- FK → dim_date
                user_id        INT,        -- FK → dim_user
                algorithm_id   INT,        -- FK → dim_algorithm
                duration_ms    BIGINT,
                input_size     INT,
                success        BOOLEAN
            );
            """;

        // Slowly Changing Dimension (SCD Type 2): tracks history
        public static final String DIM_USER = """
            CREATE TABLE IF NOT EXISTS dim_user (
                user_sk        INT PRIMARY KEY,    -- surrogate key
                user_id        VARCHAR(36),        -- natural key
                username       VARCHAR(100),
                tier           VARCHAR(20),
                valid_from     DATE,
                valid_to       DATE,               -- NULL = current record
                is_current     BOOLEAN
            );
            """;

        public static final String DIM_DATE = """
            CREATE TABLE IF NOT EXISTS dim_date (
                date_id   INT PRIMARY KEY,         -- YYYYMMDD format
                full_date DATE,
                year      INT, quarter INT, month INT, week INT, day_of_week INT,
                is_weekend BOOLEAN, is_holiday BOOLEAN
            );
            """;
    }
}
