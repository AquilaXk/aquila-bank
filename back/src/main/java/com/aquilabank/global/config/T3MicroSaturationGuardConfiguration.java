package com.aquilabank.global.config;

import com.aquilabank.global.ops.DbPoolSaturationProbe;
import com.aquilabank.global.ops.DbPoolSaturationSnapshot;
import com.aquilabank.global.ops.ServletThreadSaturationProbe;
import com.aquilabank.global.ops.ServletThreadSaturationSnapshot;
import com.aquilabank.global.ops.T3MicroQueryTimeoutSignal;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import com.aquilabank.global.ops.T3MicroSaturationGuardProperties;
import com.aquilabank.global.web.T3MicroSaturationInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.sql.SQLException;
import java.time.Clock;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** t3.micro 포화 guard는 global runtime 지표만 읽고 domain 경계 밖에서 fail-fast 처리합니다. */
@Configuration
@EnableConfigurationProperties(T3MicroSaturationGuardProperties.class)
public class T3MicroSaturationGuardConfiguration {

  @Bean
  T3MicroQueryTimeoutSignal t3MicroQueryTimeoutSignal(MeterRegistry meterRegistry) {
    return new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry);
  }

  @Bean
  DbPoolSaturationProbe dbPoolSaturationProbe(ObjectProvider<DataSource> dataSourceProvider) {
    return new HikariDbPoolSaturationProbe(dataSourceProvider);
  }

  @Bean
  ServletThreadSaturationProbe servletThreadSaturationProbe() {
    return new JmxServletThreadSaturationProbe();
  }

  @Bean
  T3MicroSaturationGuard t3MicroSaturationGuard(
      T3MicroSaturationGuardProperties properties,
      DbPoolSaturationProbe dbPoolSaturationProbe,
      ServletThreadSaturationProbe servletThreadSaturationProbe,
      T3MicroQueryTimeoutSignal t3MicroQueryTimeoutSignal,
      MeterRegistry meterRegistry) {
    return new T3MicroSaturationGuard(
        properties,
        dbPoolSaturationProbe,
        servletThreadSaturationProbe,
        t3MicroQueryTimeoutSignal,
        meterRegistry);
  }

  @Bean
  T3MicroSaturationInterceptor t3MicroSaturationInterceptor(
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    return new T3MicroSaturationInterceptor(t3MicroSaturationGuard);
  }

  @Bean
  WebMvcConfigurer t3MicroSaturationGuardWebMvcConfigurer(
      T3MicroSaturationInterceptor t3MicroSaturationInterceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(t3MicroSaturationInterceptor).addPathPatterns("/**");
      }
    };
  }

  private static final class HikariDbPoolSaturationProbe implements DbPoolSaturationProbe {

    private final ObjectProvider<DataSource> dataSourceProvider;

    private HikariDbPoolSaturationProbe(ObjectProvider<DataSource> dataSourceProvider) {
      this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public DbPoolSaturationSnapshot snapshot() {
      DataSource dataSource = dataSourceProvider.getIfAvailable();
      HikariDataSource hikariDataSource = unwrap(dataSource);
      if (hikariDataSource == null || hikariDataSource.getHikariPoolMXBean() == null) {
        return DbPoolSaturationSnapshot.empty();
      }
      HikariPoolMXBean mxBean = hikariDataSource.getHikariPoolMXBean();
      return new DbPoolSaturationSnapshot(
          mxBean.getActiveConnections(),
          mxBean.getTotalConnections(),
          mxBean.getThreadsAwaitingConnection());
    }

    private HikariDataSource unwrap(DataSource dataSource) {
      if (dataSource instanceof HikariDataSource hikariDataSource) {
        return hikariDataSource;
      }
      if (dataSource == null) {
        return null;
      }
      try {
        return dataSource.unwrap(HikariDataSource.class);
      } catch (SQLException ex) {
        return null;
      }
    }
  }

  private static final class JmxServletThreadSaturationProbe
      implements ServletThreadSaturationProbe {

    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    private volatile ObjectName threadPoolName;

    @Override
    public ServletThreadSaturationSnapshot snapshot() {
      ObjectName name = threadPoolName;
      if (name == null) {
        name = findThreadPoolName();
      }
      if (name == null) {
        return ServletThreadSaturationSnapshot.empty();
      }
      try {
        Integer busyThreads = (Integer) mBeanServer.getAttribute(name, "currentThreadsBusy");
        Integer maxThreads = (Integer) mBeanServer.getAttribute(name, "maxThreads");
        return new ServletThreadSaturationSnapshot(busyThreads, maxThreads);
      } catch (Exception ex) {
        threadPoolName = null;
        return ServletThreadSaturationSnapshot.empty();
      }
    }

    private ObjectName findThreadPoolName() {
      try {
        for (ObjectName name :
            mBeanServer.queryNames(new ObjectName("*:type=ThreadPool,*"), null)) {
          if (mBeanServer.isRegistered(name)) {
            threadPoolName = name;
            return name;
          }
        }
      } catch (Exception ex) {
        return null;
      }
      return null;
    }
  }
}
