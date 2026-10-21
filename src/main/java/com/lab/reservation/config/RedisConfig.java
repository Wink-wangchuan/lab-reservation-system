package com.lab.reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 *
 * <p>自定义 {@link RedisTemplate}：key 使用 String 序列化，value 使用
 * JSON 序列化，避免默认 JdkSerialization 产生的乱码与无法跨端读取问题。
 * 用于热门实验室缓存、预约数据缓存等场景。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        // key / hash key 走 String
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value / hash value 走 JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}