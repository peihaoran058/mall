package com.example.mall.config;

import com.example.mall.common.Constants;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache + Redis 整合配置（第7课时）。
 *
 * <p><b>课上重点：这一段配置不写会出两个大问题</b></p>
 * <ol>
 *     <li><b>缓存永不过期</b>：SpringBoot 默认的 RedisCacheConfiguration 是
 *         {@code ttl = -1}（永不过期）。商品改了价格，用户刷新页面还是旧价，
 *         只能等运维手动删 key。必须显式配 TTL。</li>
 *     <li><b>Redis 里看到乱码</b>：默认用 JDK 序列化，redis-cli 里是一堆
 *         {@code \xac\xed} 二进制。换成 JSON 后可以直接看懂。</li>
 * </ol>
 *
 * <p><b>还有一个隐藏坑：</b>{@code @Cacheable} 默认 key 生成器会把方法的
 * 参数对象 {@code toString()}，如果参数是自定义对象且没重写 toString，
 * key 会长成 {@code com.example.Foo@1a2b3c} —— 这种 key 一旦换了 JVM 就命中不了。
 * 所以本项目统一在注解里显式写 key（见 ProductServiceImpl#detail）。</p>
 */
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final MallProperties mallProperties;

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> {
            // 默认配置：所有缓存 10 分钟过期
            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(10))
                    // key 用字符串，方便 redis-cli 里 keys mall:cache:*
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    // value 用 JSON
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper())))
                    // 允许缓存 null：这是"缓存穿透"的一种兜底（缓存空对象）
                    .computePrefixWith(cacheName -> cacheName + "::");

            // 商品详情：单独配置 TTL，从 yml 读
            Map<String, RedisCacheConfiguration> configs = new HashMap<>();
            configs.put(Constants.CACHE_PRODUCT_DETAIL, defaultConfig
                    .entryTtl(Duration.ofSeconds(mallProperties.getCache().getProductDetailTtl())));

            builder.cacheDefaults(defaultConfig).withInitialCacheConfigurations(configs);
        };
    }

    private ObjectMapper cacheMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
