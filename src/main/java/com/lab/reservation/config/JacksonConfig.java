package com.lab.reservation.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局序列化配置
 * <p>
 * MyBatis-Plus 默认使用雪花算法生成 Long 型主键（19 位），而 JavaScript 的 Number
 * 只能精确表示 -2^53+1 ~ 2^53-1 之间的整数。若直接以数字返回，前端 JSON.parse 会静默丢失精度，
 * 再把 ID 回传给后端时便查不到记录（表现为"预约单不存在"）。
 * <p>
 * 因此对超出安全整数范围的 Long 输出字符串；普通计数等小数值仍保持数字类型，
 * 避免影响前端图表与统计逻辑。
 *
 * @author LabWise
 * @since 1.0.0
 */
@Configuration
public class JacksonConfig {

    /** JavaScript Number 可安全表示的最大整数（2^53 - 1） */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        LongToStringSerializer serializer = new LongToStringSerializer();
        return builder -> builder
                .serializerByType(Long.class, serializer)
                .serializerByType(Long.TYPE, serializer);
    }

    /**
     * 超长整型序列化器：仅当数值可能在前端丢失精度时输出为字符串
     */
    public static class LongToStringSerializer extends JsonSerializer<Long> {

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
            } else if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }
    }
}