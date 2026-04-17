package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.notification.TransferBookedNotificationConsumer;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "server.shutdown=immediate",
      "notification.inbox.consumer.enabled=true",
      "notification.inbox.consumer.auto-startup=false",
      "notification.inbox.consumer.bootstrap-servers=localhost:9092",
      "notification.inbox.consumer.transfer-booked.topic=bank.transfer.booked.test"
    })
class NotificationSseIntegrationTest extends PostgresContainerTestSupport {

  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";

  @LocalServerPort private int port;

  @Autowired private TransferBookedNotificationConsumer transferBookedNotificationConsumer;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  @Timeout(15)
  void pushesNotificationToJwtUserStreamAfterConsumerIngest() throws Exception {
    long[] userId = new long[1];
    long[] accountIds = new long[2];
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("stream-user");
          accountIds[0] = insertAccount("visible account");
          accountIds[1] = insertAccount("hidden account");
          insertMembership(userId[0], accountIds[0], "OWNER", "ACTIVE");
          insertMembership(userId[0], accountIds[1], "VIEWER", "REVOKED");
        });

    String token = issueToken("stream-user-subject", userId[0]);
    try (NotificationSseStream stream = openJwtStream(token)) {
      assertThat(stream.awaitEvent("connected", Duration.ofSeconds(3)).name())
          .isEqualTo("connected");

      transferBookedNotificationConsumer.consume(
          "transfer-booked:TRX-SSE-100",
          transferBookedPayload(accountIds[0], accountIds[1], 1500L, "rent"));

      SseEvent notification = stream.awaitEvent("notification", Duration.ofSeconds(3));
      JsonNode payload = objectMapper.readTree(notification.data());
      assertThat(payload.get("accountId").asLong()).isEqualTo(accountIds[0]);
      assertThat(payload.get("eventType").asText()).isEqualTo("TransferBooked");
      assertThat(payload.get("title").asText()).isEqualTo("이체 완료");
      assertThat(payload.get("message").asText()).contains("출금").contains("rent");
      assertThat(payload.get("read").asBoolean()).isFalse();
    }
  }

  @Test
  @Timeout(15)
  void doesNotPushDuplicateNotificationWhenInboxInsertIsNoOp() throws Exception {
    long[] accountIds = new long[2];
    commit(
        transactionManager,
        () -> {
          accountIds[0] = insertAccount("source account");
          accountIds[1] = insertAccount("target account");
        });

    try (NotificationSseStream stream = openAccountStream(accountIds[0])) {
      assertThat(stream.awaitEvent("connected", Duration.ofSeconds(3)).name())
          .isEqualTo("connected");

      String eventKey = "transfer-booked:TRX-SSE-200";
      String payload = transferBookedPayload(accountIds[0], accountIds[1], 2200L, "salary");
      transferBookedNotificationConsumer.consume(eventKey, payload);

      SseEvent firstNotification = stream.awaitEvent("notification", Duration.ofSeconds(3));
      JsonNode firstPayload = objectMapper.readTree(firstNotification.data());
      assertThat(firstPayload.get("accountId").asLong()).isEqualTo(accountIds[0]);
      assertThat(countNotificationsByEventPrefix(eventKey)).isEqualTo(2L);

      transferBookedNotificationConsumer.consume(eventKey, payload);

      stream.assertNoEvent("notification", Duration.ofMillis(800));
      assertThat(countNotificationsByEventPrefix(eventKey)).isEqualTo(2L);
    }
  }

  private NotificationSseStream openJwtStream(String token) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:%d/api/v1/notifications/stream".formatted(port)))
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return openStream(request);
  }

  private NotificationSseStream openAccountStream(long accountId) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:%d/api/v1/notifications/stream".formatted(port)))
            .header("Accept", "text/event-stream")
            .header("X-Account-Id", Long.toString(accountId))
            .header("X-Subject", "sse-test")
            .GET()
            .build();
    return openStream(request);
  }

  private NotificationSseStream openStream(HttpRequest request) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<InputStream> response =
        client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("content-type"))
        .hasValueSatisfying(value -> assertThat(value).contains("text/event-stream"));
    return new NotificationSseStream(response.body());
  }

  private String transferBookedPayload(
      long sourceAccountId, long targetAccountId, long amountMinor, String summary) {
    return """
        {
          "transactionReference": "TRX-SSE",
          "sourceAccountId": %d,
          "targetAccountId": %d,
          "amountMinor": %d,
          "currencyCode": "KRW",
          "summary": "%s",
          "bookedAt": "2026-04-17T00:00:00Z"
        }
        """
        .formatted(sourceAccountId, targetAccountId, amountMinor, summary);
  }

  private long countNotificationsByEventPrefix(String eventKey) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM notification_inbox
            WHERE event_key LIKE :eventPrefix
            """,
            new MapSqlParameterSource().addValue("eventPrefix", eventKey + "%"),
            Long.class);
    return count == null ? 0L : count;
  }

  private long insertUser(String loginId) {
    Long userId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_user (
                login_id,
                password_hash,
                display_name,
                user_status,
                created_at,
                updated_at
            )
            VALUES (
                :loginId,
                '$2a$10$abcdefghijklmnopqrstuv',
                :loginId,
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            Long.class);
    if (userId == null) {
      throw new IllegalStateException("bank_user insert did not return id");
    }
    return userId;
  }

  private long insertAccount(String displayName) {
    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("displayName", displayName),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return accountId;
  }

  private void insertMembership(long userId, long accountId, String role, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :accountId,
            :role,
            :status,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("role", role)
            .addValue("status", status));
  }

  private String issueToken(String subject, long userId) throws JOSEException {
    Instant now = Instant.now();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("user_id", userId)
            .build();

    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet);
    signedJwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
    return signedJwt.serialize();
  }

  private static final class NotificationSseStream implements AutoCloseable {

    private final BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
    private final BufferedReader reader;
    private final Thread readerThread;
    private volatile boolean closed;

    private NotificationSseStream(InputStream inputStream) {
      this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      this.readerThread = Thread.ofPlatform().daemon(true).start(this::readLoop);
    }

    private SseEvent awaitEvent(String eventName, Duration timeout) throws InterruptedException {
      long deadlineNanos = System.nanoTime() + timeout.toNanos();
      while (true) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
          throw new AssertionError("SSE event timed out: " + eventName);
        }
        SseEvent event = events.poll(remainingNanos, TimeUnit.NANOSECONDS);
        if (event == null) {
          throw new AssertionError("SSE event timed out: " + eventName);
        }
        if (eventName.equals(event.name())) {
          return event;
        }
      }
    }

    private void assertNoEvent(String eventName, Duration duration) throws InterruptedException {
      long deadlineNanos = System.nanoTime() + duration.toNanos();
      while (true) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
          return;
        }
        SseEvent event = events.poll(remainingNanos, TimeUnit.NANOSECONDS);
        if (event == null) {
          return;
        }
        if (eventName.equals(event.name())) {
          throw new AssertionError("Unexpected SSE event: " + eventName);
        }
      }
    }

    private void readLoop() {
      String eventName = "message";
      String eventId = null;
      StringBuilder data = new StringBuilder();
      try {
        String line;
        while (!closed && (line = reader.readLine()) != null) {
          if (line.isEmpty()) {
            if (data.length() > 0) {
              events.offer(new SseEvent(eventId, eventName, data.toString()));
            }
            eventName = "message";
            eventId = null;
            data = new StringBuilder();
            continue;
          }
          if (line.startsWith("event:")) {
            eventName = line.substring("event:".length()).trim();
            continue;
          }
          if (line.startsWith("id:")) {
            eventId = line.substring("id:".length()).trim();
            continue;
          }
          if (line.startsWith("data:")) {
            if (data.length() > 0) {
              data.append('\n');
            }
            data.append(line.substring("data:".length()).trim());
          }
        }
      } catch (IOException ignored) {
        if (!closed) {
          events.offer(new SseEvent(null, "stream-error", ignored.getMessage()));
        }
      }
    }

    @Override
    public void close() throws Exception {
      closed = true;
      reader.close();
      readerThread.join(1000L);
    }
  }

  private record SseEvent(String id, String name, String data) {}
}
