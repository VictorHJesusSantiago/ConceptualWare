package com.conceptualware.infrastructure.database;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.geo.Distance;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    @Profile("!test")
    public LettuceConnectionFactory redisConnectionFactory(
            org.springframework.boot.autoconfigure.data.redis.RedisProperties props) {
        return new LettuceConnectionFactory(props.getHost(), props.getPort());
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        var jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

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
                "leaderboard",  defaults.entryTtl(Duration.ofSeconds(30)),
                "challenges",   defaults.entryTtl(Duration.ofHours(1))
            ))
            .build();
    }

    public static class RedisDataStructureDemo {

        private final RedisTemplate<String, Object> redis;

        public RedisDataStructureDemo(RedisTemplate<String, Object> redis) {
            this.redis = redis;
        }

        public void stringDemo() {
            redis.opsForValue().set("counter", 0, Duration.ofMinutes(5));
            redis.opsForValue().increment("counter");
            redis.opsForValue().increment("counter", 5);
            Object val = redis.opsForValue().get("counter");
        }

        public void hashDemo(String userId, Map<String, Object> fields) {
            redis.opsForHash().putAll("user:" + userId, fields);
            Object email = redis.opsForHash().get("user:" + userId, "email");
            Map<Object, Object> all = redis.opsForHash().entries("user:" + userId);
        }

        public void leaderboardDemo() {
            redis.opsForZSet().add("leaderboard:global", "alice", 9500);
            redis.opsForZSet().add("leaderboard:global", "bob",   8200);
            redis.opsForZSet().add("leaderboard:global", "carol", 9100);
            redis.opsForZSet().incrementScore("leaderboard:global", "alice", 100);

            var top3 = redis.opsForZSet().reverseRangeWithScores("leaderboard:global", 0, 2);
            Long rank = redis.opsForZSet().reverseRank("leaderboard:global", "alice");
        }

        public void queueDemo() {
            redis.opsForList().rightPush("queue:notifications", "notify-user-1");
            redis.opsForList().rightPush("queue:notifications", "notify-user-2");
            Object next = redis.opsForList().leftPop("queue:notifications");
        }

        public void setDemo() {
            redis.opsForSet().add("online-users", "alice", "bob", "carol");
            Boolean isOnline = redis.opsForSet().isMember("online-users", "alice");
            redis.opsForSet().remove("online-users", "alice");
        }

        public long uniqueVisitorsDemo(String day, String... visitorIds) {
            String key = "visitors:hll:" + day;
            redis.opsForHyperLogLog().add(key, (Object[]) visitorIds);
            return redis.opsForHyperLogLog().size(key);
        }

        public long mergeUniqueVisitors(String destKey, String... sourceKeys) {
            redis.opsForHyperLogLog().union(destKey, sourceKeys);
            return redis.opsForHyperLogLog().size(destKey);
        }

        public void geoDemo() {
            var geo = redis.opsForGeo();
            geo.add("locations:offices", new org.springframework.data.geo.Point(-46.6333, -23.5505), "sao-paulo");
            geo.add("locations:offices", new org.springframework.data.geo.Point(-43.1729, -22.9068), "rio-de-janeiro");

            Distance dist = geo.distance("locations:offices", "sao-paulo", "rio-de-janeiro",
                org.springframework.data.geo.Metrics.KILOMETERS);

            var nearby = geo.radius("locations:offices", "sao-paulo",
                new Distance(500, org.springframework.data.geo.Metrics.KILOMETERS));
        }

        public void publishDemo(String channel, Object message) {
            redis.convertAndSend(channel, message);
        }

        public boolean isRateLimited(String userId, int maxRequests, Duration window) {
            String key = "rate:" + userId;
            long now = System.currentTimeMillis();
            long windowStart = now - window.toMillis();

            redis.opsForZSet().removeRangeByScore(key, 0, windowStart);

            Long count = redis.opsForZSet().zCard(key);
            if (count != null && count >= maxRequests) return true;

            redis.opsForZSet().add(key, String.valueOf(now), now);
            redis.expire(key, window);
            return false;
        }
    }

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
