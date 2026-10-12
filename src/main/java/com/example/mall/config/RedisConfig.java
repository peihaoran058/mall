package com.example.mall.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置（笔记第六章）。
 *
 * <p><b>课上重点：为什么必须自己写这个类？</b><br>
 * SpringBoot 默认的 RedisTemplate 用 JdkSerializationRedisSerializer，
 * 往 Redis 里存对象会看到一堆乱码 {@code \xac\xed\x00\x05t\x00...}，
 * 命令行完全没法排查。换成 JSON 序列化后，redis-cli 里能直接看懂。</p>
 *
 * <p>注意：{@link org.springframework.data.redis.core.StringRedisTemplate} 是
 * SpringBoot 自动装配好的，纯字符串场景（比如分布式锁）直接注入它即可，
 * 不要重复定义。</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用 String 序列化，保证 redis-cli 里能按 key 直接查
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        // value 用 JSON 序列化
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(buildMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 打开"类型信息写入"，否则反序列化只能拿到 LinkedHashMap，强转会抛 ClassCastException。
     * 这是缓存对象时最常见的报错来源。
     */
    private ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
