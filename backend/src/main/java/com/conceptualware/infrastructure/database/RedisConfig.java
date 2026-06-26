package com.conceptualware.infrastructure.database;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.Map;

/**
 * Concept #11 — Redis (Key-Value Store):
 *
 *   Redis data structures:
 *     STRING:  simple key→value (most common, atomic counters, rate limiting)
 *     HASH:    key→{field:value} map (user sessions, object cache)
 *     LIST:    key→[ordered elements] (message queues, activity feeds)
 *     SET:     key→{unordered unique} (tags, friend lists, bloom filter approx)
 *     SORTED SET: key→{score:member} (leaderboards, priority queues)
 *     STREAM:  append-only log (event sourcing, message broker like Kafka-lite)
 *     PUBSUB:  publish/subscribe (real-time notifications)
 *     HyperLogLog: cardinality estimation (unique visitors, ±0.81% error)
 *     GEO:    geospatial index (nearby search via sorted set under the hood)
 *
 *   Redis vs Memcached:
 *     Redis:      Persistent, data structures, pub/sub, Lua scripting, cluster mode
 *     Memcached:  Simple k/v only, multi-threaded, no persistence
 *
 *   Redis use cases in ConceptualWare:
 *     - Algorithm result cache (STRING, TTL 5min)
 *     - Rate limiting (INCR + EXPIRE, sliding window)
 *     - Leaderboard (SORTED SET with ZADD/ZRANK/ZREVRANGE)
 *     - Session store (HASH with HSET/HGETALL)
 *     - Real-time notifications (PUBLISH/SUBSCRIBE)
 *
 * Concept #11 — NoSQL: key-value model, eventual consistency, TTL semantics
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // ── Redis Connection ──────────────────────────────────────────────────────

    @Bean
    @Profile("!test")
    public LettuceConnectionFactory redisConnectionFactory(
            org.springframework.boot.autoconfigure.data.redis.RedisProperties props) {
        return new LettuceConnectionFactory(props.getHost(), props.getPort());
    }

    // ── RedisTemplate — strongly-typed operations ─────────────────────────────

    /**
     * Generic RedisTemplate<String, Object> using JSON serialization.
     * JSON (vs Java serialization): human-readable, cross-language, no ClassCastException on class rename.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key: plain string (readable in redis-cli)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value: JSON (Jackson2JsonRedisSerializer)
        var jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    // ── RedisCacheManager — Spring Cache abstraction ──────────────────────────

    /**
     * Cache manager with per-cache TTLs.
     * Annotations: @Cacheable, @CachePut, @CacheEvict on service methods.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(Map.of(
                "algorithms",   defaults.entryTtl(Duration.ofMinutes(10)),
                "users",        defaults.entryTtl(Duration.ofMinutes(30)),
                "leaderboard",  defaults.entryTtl(Duration.ofSeconds(30)), // high churn
                "challenges",   defaults.entryTtl(Duration.ofHours(1))
            ))
            .build();
    }

    // ── Redis Data Structure Demonstrations ───────────────────────────────────

    /**
     * Demonstrates all major Redis data structures using Spring Data Redis.
     * Each operation maps to a Redis command.
     */
    public static class RedisDataStructureDemo {

        private final RedisTemplate<String, Object> redis;

        public RedisDataStructureDemo(RedisTemplate<String, Object> redis) {
            this.redis = redis;
        }

        // STRING operations (INCR, DECR, SETEX, GETSET)
        public void stringDemo() {
            redis.opsForValue().set("counter", 0, Duration.ofMinutes(5));
            redis.opsForValue().increment("counter");         // INCR — atomic
            redis.opsForValue().increment("counter", 5);     // INCRBY
            Object val = redis.opsForValue().get("counter"); // GET
        }

        // HASH operations (HSET, HGET, HGETALL, HINCRBY)
        public void hashDemo(String userId, Map<String, Object> fields) {
            redis.opsForHash().putAll("user:" + userId, fields);            // HMSET
            Object email = redis.opsForHash().get("user:" + userId, "email"); // HGET
            Map<Object, Object> all = redis.opsForHash().entries("user:" + userId); // HGETALL
        }

        // SORTED SET — Leaderboard (ZADD, ZREVRANGE, ZRANK)
        public void leaderboardDemo() {
            redis.opsForZSet().add("leaderboard:global", "alice", 9500);
            redis.opsForZSet().add("leaderboard:global", "bob",   8200);
            redis.opsForZSet().add("leaderboard:global", "carol", 9100);
            redis.opsForZSet().incrementScore("leaderboard:global", "alice", 100); // ZINCRBY

            // Top 3 (ZREVRANGE 0 2 WITHSCORES)
            var top3 = redis.opsForZSet().reverseRangeWithScores("leaderboard:global", 0, 2);
            Long rank = redis.opsForZSet().reverseRank("leaderboard:global", "alice"); // ZREVRANK
        }

        // LIST — Message Queue (LPUSH/RPOP, BLPOP)
        public void queueDemo() {
            redis.opsForList().rightPush("queue:notifications", "notify-user-1");
            redis.opsForList().rightPush("queue:notifications", "notify-user-2");
            Object next = redis.opsForList().leftPop("queue:notifications"); // RPOPLPUSH pattern
        }

        // SET — Unique tracking (SADD, SISMEMBER, SMEMBERS)
        public void setDemo() {
            redis.opsForSet().add("online-users", "alice", "bob", "carol");
            Boolean isOnline = redis.opsForSet().isMember("online-users", "alice"); // SISMEMBER
            redis.opsForSet().remove("online-users", "alice");
        }

        // Sliding window rate limiter using ZSET
        public boolean isRateLimited(String userId, int maxRequests, Duration window) {
            String key = "rate:" + userId;
            long now = System.currentTimeMillis();
            long windowStart = now - window.toMillis();

            // Remove expired entries
            redis.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // Count requests in window
            Long count = redis.opsForZSet().zCard(key);
            if (count != null && count >= maxRequests) return true; // rate limited

            // Add current request
            redis.opsForZSet().add(key, String.valueOf(now), now);
            redis.expire(key, window);
            return false;
        }
    }

    // ── Redis Comparison with other NoSQL ──────────────────────────────────────

    public record NoSQLComparison(
        String db, String model, String consistency,
        String durability, String bestFor
    ) {
        public static NoSQLComparison[] all() {
            return new NoSQLComparison[]{
                new NoSQLComparison("Redis",     "Key-Value / Multi-structure", "Strong (single-node)",
                    "RDB + AOF optional",            "Cache, sessions, leaderboards, rate limiting"),
                new NoSQLComparison("MongoDB",   "Document (BSON/JSON)",        "Eventual (default) / Strong (transactions)",
                    "Write-ahead journal",           "Flexible schema, rich queries, aggregations"),
                new NoSQLComparison("Cassandra", "Wide-column",                 "Eventual (tunable)",
                    "Commit log + SSTables",         "Write-heavy, time-series, geo-distributed"),
                new NoSQLComparison("Neo4j",     "Graph (nodes + edges)",       "ACID",
                    "Write-ahead log",               "Relationship queries, recommendations, fraud detection"),
                new NoSQLComparison("InfluxDB",  "Time-Series",                 "Eventual",
                    "WAL + TSM files",               "Metrics, IoT sensor data, monitoring"),
                new NoSQLComparison("DynamoDB",  "Key-Value / Document",        "Eventual / Strong (ConsistentRead)",
                    "Multi-AZ replication",          "Serverless, predictable latency, massive scale"),
            };
        }
    }
}
