package com.aquilabank.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API 직렬화에서 공통으로 쓰는 Jackson 기본 설정 */
@Configuration
public class JacksonConfiguration {

  @Bean
  ObjectMapper objectMapper() {
    return JsonMapper.builder()
        // Java time 타입을 문자열 기반 ISO-8601 형식으로 안정적으로 직렬화합니다.
        .addModule(new JavaTimeModule())
        .findAndAddModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
  }
}
