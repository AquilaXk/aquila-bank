package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "outbox.ops.enabled=true",
      "outbox.ops.token-header=X-Outbox-Ops-Token",
      "outbox.ops.token=test-outbox-ops-token",
      "outbox.ops.failed-list-limit=2",
      "outbox.ops.health.max-lag-seconds=15",
      "outbox.ops.health.max-failed-count=1",
      "outbox.ops.health.max-stale-sending-count=0"
    })
class OutboxOpsApiIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void returnsSummaryAndFailedEventsWithInternalServiceToken() throws Exception {
    Instant base = Instant.now();
    commit(
        transactionManager,
        () -> {
          insertOutboxEvent(
              "evt-pending-old", "PENDING", 0, null, base.minusSeconds(20), base.minusSeconds(40));
          insertOutboxEvent(
              "evt-failed-1",
              "FAILED",
              1,
              "broker down",
              base.minusSeconds(5),
              base.minusSeconds(15));
          insertOutboxEvent(
              "evt-failed-2", "FAILED", 2, "timeout", base.plusSeconds(30), base.minusSeconds(10));
          insertOutboxEvent(
              "evt-quarantined",
              "QUARANTINED",
              4,
              "invalid payload",
              base.minusSeconds(30),
              base.minusSeconds(5));
        });

    mockMvc
        .perform(
            get("/internal/api/v1/outbox/summary")
                .header("Authorization", outboxOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failedCount").value(2))
        .andExpect(jsonPath("$.quarantinedCount").value(1))
        .andExpect(jsonPath("$.staleSendingCount").value(0))
        .andExpect(jsonPath("$.lagSeconds", greaterThanOrEqualTo(20)));

    mockMvc
        .perform(
            get("/internal/api/v1/outbox/failed-events")
                .header("Authorization", outboxOpsAuthorization())
                .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].eventKey").value("evt-failed-1"))
        .andExpect(jsonPath("$.items[1].eventKey").value("evt-failed-2"));
  }

  @Test
  void recoversStaleSendingRows() throws Exception {
    Instant base = Instant.now();
    long[] ids = new long[2];
    commit(
        transactionManager,
        () -> {
          ids[0] =
              insertOutboxEvent(
                  "evt-sending-stale",
                  "SENDING",
                  1,
                  null,
                  base.minusSeconds(5),
                  base.minusSeconds(50));
          ids[1] =
              insertOutboxEvent(
                  "evt-sending-fresh",
                  "SENDING",
                  1,
                  null,
                  base.minusSeconds(5),
                  base.minusSeconds(10));
        });

    mockMvc
        .perform(
            post("/internal/api/v1/outbox/recovery/stale-sending")
                .header("Authorization", outboxOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recoveredCount").value(1))
        .andExpect(jsonPath("$.staleAfterSeconds").value(30));

    assertThat(findStatus(ids[0])).isEqualTo("PENDING");
    assertThat(findStatus(ids[1])).isEqualTo("SENDING");
  }

  @Test
  void rejectsMissingInternalServiceToken() throws Exception {
    mockMvc
        .perform(get("/internal/api/v1/outbox/summary"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  @Test
  void degradesActuatorHealthWhenOutboxThresholdIsExceeded() throws Exception {
    Instant base = Instant.now();
    commit(
        transactionManager,
        () -> {
          insertOutboxEvent(
              "evt-pending-old", "PENDING", 0, null, base.minusSeconds(20), base.minusSeconds(40));
          insertOutboxEvent(
              "evt-sending-stale", "SENDING", 1, null, base.minusSeconds(5), base.minusSeconds(50));
        });

    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
  }

  @Test
  void keepsReadinessUpWhenOutboxThresholdIsExceeded() throws Exception {
    Instant base = Instant.now();
    commit(
        transactionManager,
        () -> {
          insertOutboxEvent(
              "evt-readiness-pending-old",
              "PENDING",
              0,
              null,
              base.minusSeconds(20),
              base.minusSeconds(40));
          insertOutboxEvent(
              "evt-readiness-sending-stale",
              "SENDING",
              1,
              null,
              base.minusSeconds(5),
              base.minusSeconds(50));
        });

    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  private long insertOutboxEvent(
      String eventKey,
      String publishStatus,
      int retryCount,
      String lastError,
      Instant availableAt,
      Instant updatedAt) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO outbox_event (
                aggregate_type,
                aggregate_id,
                event_type,
                event_key,
                payload,
                publish_status,
                available_at,
                retry_count,
                last_error,
                created_at,
                updated_at
            )
            VALUES (
                'TRANSFER',
                'TRX-' || right(:eventKey, 3),
                'TransferBooked',
                :eventKey,
                '{"transactionReference":"seed"}'::jsonb,
                :publishStatus,
                :availableAt,
                :retryCount,
                :lastError,
                :updatedAt,
                :updatedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("eventKey", eventKey)
                .addValue("publishStatus", publishStatus)
                .addValue("availableAt", Timestamp.from(availableAt))
                .addValue("retryCount", retryCount)
                .addValue("lastError", lastError)
                .addValue("updatedAt", Timestamp.from(updatedAt)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("outbox_event insert did not return id");
    }
    return id;
  }

  private String findStatus(long id) {
    return jdbcTemplate.queryForObject(
        "SELECT publish_status FROM outbox_event WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id),
        String.class);
  }

  private String outboxOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "outbox-ops", java.util.Set.of(InternalServiceScope.OUTBOX_OPS));
  }
}
