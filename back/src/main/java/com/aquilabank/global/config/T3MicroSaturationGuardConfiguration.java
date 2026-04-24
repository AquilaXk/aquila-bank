package com.aquilabank.global.config;

import com.aquilabank.global.ops.DbPoolSaturationProbe;
import com.aquilabank.global.ops.DbPoolSaturationSnapshot;
import com.aquilabank.global.ops.JvmPressureProbe;
import com.aquilabank.global.ops.JvmPressureSnapshot;
import com.aquilabank.global.ops.ServletThreadSaturationProbe;
import com.aquilabank.global.ops.ServletThreadSaturationSnapshot;
import com.aquilabank.global.ops.T3MicroQueryTimeoutSignal;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import com.aquilabank.global.ops.T3MicroSaturationGuardProperties;
import com.aquilabank.global.web.T3MicroSaturationInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
  JvmPressureProbe jvmPressureProbe() {
    return new JmxJvmPressureProbe(
        ManagementFactory.getMemoryMXBean(),
        ManagementFactory.getGarbageCollectorMXBeans(),
        Clock.systemUTC());
  }

  @Bean
  T3MicroSaturationGuard t3MicroSaturationGuard(
      T3MicroSaturationGuardProperties properties,
      DbPoolSaturationProbe dbPoolSaturationProbe,
      ServletThreadSaturationProbe servletThreadSaturationProbe,
      JvmPressureProbe jvmPressureProbe,
      T3MicroQueryTimeoutSignal t3MicroQueryTimeoutSignal,
      MeterRegistry meterRegistry) {
    return new T3MicroSaturationGuard(
        properties,
        dbPoolSaturationProbe,
        servletThreadSaturationProbe,
        jvmPressureProbe,
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

  private static final class JmxJvmPressureProbe implements JvmPressureProbe {

    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> garbageCollectors;
    private final Clock clock;
    private volatile GcSample previousSample;

    private JmxJvmPressureProbe(
        MemoryMXBean memoryMXBean, List<GarbageCollectorMXBean> garbageCollectors, Clock clock) {
      this.memoryMXBean = memoryMXBean;
      this.garbageCollectors = List.copyOf(garbageCollectors);
      this.clock = clock;
    }

    @Override
    public JvmPressureSnapshot snapshot(Duration gcWindow) {
      MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
      long heapUsed = Math.max(heap.getUsed(), 0L);
      long heapMax = Math.max(heap.getMax(), 0L);
      GcSample currentSample = sampleGc();
      GcSample previous = previousSample;
      previousSample = currentSample;
      if (previous == null
          || currentSample.observedAt().minus(gcWindow).isAfter(previous.observedAt())) {
        return new JvmPressureSnapshot(heapUsed, heapMax, 0L, 0L);
      }
      return new JvmPressureSnapshot(
          heapUsed,
          heapMax,
          currentSample.collectionCount() - previous.collectionCount(),
          currentSample.collectionTimeMs() - previous.collectionTimeMs());
    }

    private GcSample sampleGc() {
      long collectionCount = 0L;
      long collectionTimeMs = 0L;
      for (GarbageCollectorMXBean garbageCollector : garbageCollectors) {
        collectionCount += Math.max(garbageCollector.getCollectionCount(), 0L);
        collectionTimeMs += Math.max(garbageCollector.getCollectionTime(), 0L);
      }
      return new GcSample(clock.instant(), collectionCount, collectionTimeMs);
    }

    private record GcSample(Instant observedAt, long collectionCount, long collectionTimeMs) {}
  }
}
