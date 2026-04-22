package com.aquilabank.global.config;

import com.aquilabank.global.ops.ApiAdmissionControl;
import com.aquilabank.global.ops.ApiAdmissionControlProperties;
import com.aquilabank.global.web.ApiAdmissionControlInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** endpoint group별 admission control을 MVC 진입부에 연결합니다. */
@Configuration
@EnableConfigurationProperties(ApiAdmissionControlProperties.class)
public class ApiAdmissionControlConfiguration {

  @Bean
  ApiAdmissionControl apiAdmissionControl(
      ApiAdmissionControlProperties properties, MeterRegistry meterRegistry) {
    return new ApiAdmissionControl(properties, meterRegistry);
  }

  @Bean
  ApiAdmissionControlInterceptor apiAdmissionControlInterceptor(
      ApiAdmissionControl apiAdmissionControl) {
    return new ApiAdmissionControlInterceptor(apiAdmissionControl);
  }

  @Bean
  WebMvcConfigurer apiAdmissionControlWebMvcConfigurer(
      ApiAdmissionControlInterceptor apiAdmissionControlInterceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAdmissionControlInterceptor).addPathPatterns("/**");
      }
    };
  }
}
