package com.example.wepay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
public class RedisConfig {

    /**
     * 문자열 전용 RedisTemplate.
     *
     * key/value 모두 String으로 고정되어 있어 별도 직렬화 설정이 필요 없다.
     * 주로 INCR, EXPIRE 같은 카운터·단순 문자열 연산에 사용한다.
     *
     * RedisConnectionFactory는 Spring Boot가 application.yml의
     * spring.data.redis.host/port 설정을 읽어 LettuceConnectionFactory로
     * 자동 생성해 주입해 준다.
     */
    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * 객체 직렬화용 RedisTemplate.
     *
     * key는 String, value는 임의 객체(Object)를 JSON으로 저장한다.
     * 주로 Account 같은 도메인 객체를 캐싱할 때 사용한다.
     */
    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

        // --- ObjectMapper 설정 ---
        // BasicPolymorphicTypeValidator: 역직렬화 시 허용할 타입을 명시적으로 제한한다.
        // allowIfBaseType(Object.class) → Object를 상속한 모든 클래스 허용.
        // (보안상 와일드카드 허용은 위험하므로 실제 운영에서는 도메인 패키지로 좁히는 것이 좋다)
        //
        // activateDefaultTyping: JSON에 클래스 타입 정보(@class 필드)를 함께 저장하도록 설정.
        // 이 정보가 없으면 역직렬화 시 어떤 클래스로 복원할지 알 수 없다.
        //
        // DefaultTyping.NON_FINAL: final이 아닌 모든 타입에 타입 정보를 포함시킨다.
        ObjectMapper mapper = JsonMapper.builder()
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build(),
                        DefaultTyping.NON_FINAL)
                .build();

        // ObjectMapper를 받아 JSON 직렬화/역직렬화를 수행하는 Serializer.
        // Redis에 저장되는 값의 형태: {"@class":"com.example.wepay.domain.Account", "id":1, ...}
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(mapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key는 사람이 읽기 쉽도록 단순 문자열로 직렬화 (예: "account:1")
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // value는 JSON으로 직렬화하여 타입 정보와 함께 저장
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // 위 설정을 적용하기 위해 명시적으로 초기화 (setConnectionFactory 후 필수)
        template.afterPropertiesSet();
        return template;
    }
}
