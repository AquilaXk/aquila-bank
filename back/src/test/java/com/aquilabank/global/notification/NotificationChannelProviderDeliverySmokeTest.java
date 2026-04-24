package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationChannelDeliverySkipReason;
import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerService;
import com.aquilabank.global.config.NotificationChannelProviderDeliveryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class NotificationChannelProviderDeliverySmokeTest {

  private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void dispatchesImmediateEmailAndMarksSlowSmsForRetryBackoff() throws Exception {
    AtomicInteger emailRequests = new AtomicInteger();
    AtomicInteger smsRequests = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext(
        "/email",
        exchange -> {
          emailRequests.incrementAndGet();
          writeAccepted(exchange);
        });
    server.createContext(
        "/sms",
        exchange -> {
          smsRequests.incrementAndGet();
          try {
            Thread.sleep(200L);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          try {
            writeAccepted(exchange);
          } catch (IOException ignored) {
            // read timeout 이후 client가 먼저 연결을 닫을 수 있어 서버 응답 write 실패는 무시합니다.
          }
        });
    server.start();

    int port = server.getAddress().getPort();
    NotificationChannelProviderDeliveryProperties properties =
        new NotificationChannelProviderDeliveryProperties(
            true,
            "Authorization",
            "Bearer smoke-secret",
            500,
            50,
            new NotificationChannelProviderDeliveryProperties.ChannelProperties(
                "http://127.0.0.1:" + port + "/email"),
            new NotificationChannelProviderDeliveryProperties.ChannelProperties(
                "http://127.0.0.1:" + port + "/sms"));
    NotificationChannelRecipientLookupPort recipientLookupPort =
        (userId, channel) ->
            switch ((int) userId) {
              case 101 -> Optional.of("alice@example.com");
              case 202 -> Optional.of("+821012345678");
              default -> Optional.empty();
            };
    WebhookNotificationChannelProvider provider =
        new WebhookNotificationChannelProvider(
            restClient(properties),
            recipientLookupPort,
            properties,
            new ObjectMapper().findAndRegisterModules());
    RecordingDispatchPort dispatchPort =
        new RecordingDispatchPort(List.of(emailItem(), smsItem()), NOW);
    NotificationChannelProviderWorkerService service =
        new NotificationChannelProviderWorkerService(
            dispatchPort,
            provider,
            Clock.fixed(NOW, ZoneOffset.UTC),
            2,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            10);

    int claimed = service.dispatchDueDeliveries();

    assertThat(claimed).isEqualTo(2);
    assertThat(emailRequests.get()).isEqualTo(1);
    assertThat(smsRequests.get()).isEqualTo(1);
    assertThat(dispatchPort.sentIds()).containsExactly(1L);
    assertThat(dispatchPort.failedItems()).containsOnlyKeys(2L);
    assertThat(dispatchPort.failedItems().get(2L).nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(dispatchPort.failedItems().get(2L).errorMessage()).isNotBlank();
  }

  private RestClient restClient(NotificationChannelProviderDeliveryProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  private void writeAccepted(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(202, -1);
    exchange.close();
  }

  private NotificationChannelOutboxItem emailItem() {
    return item(1L, 101L, NotificationPreferenceChannel.EMAIL, "evt-provider-email");
  }

  private NotificationChannelOutboxItem smsItem() {
    return item(2L, 202L, NotificationPreferenceChannel.SMS, "evt-provider-sms");
  }

  private NotificationChannelOutboxItem item(
      long id, long userId, NotificationPreferenceChannel channel, String eventKey) {
    return new NotificationChannelOutboxItem(
        id,
        10L + id,
        userId,
        30L + id,
        NotificationPreferenceCategory.TRANSACTIONAL,
        channel,
        "TransferBooked",
        eventKey,
        "{\"title\":\"Transfer complete\",\"message\":\"10,000 won sent\"}",
        NotificationChannelDeliveryStatus.SENDING,
        NOW.minusSeconds(1),
        null,
        0,
        null,
        NOW.minusSeconds(10),
        NOW);
  }

  private record FailedItem(Instant nextAttemptAt, String errorMessage) {}

  private static final class RecordingDispatchPort
      implements NotificationChannelOutboxDispatchPort {

    private final List<NotificationChannelOutboxItem> items;
    private final Instant expectedNow;
    private final java.util.List<Long> sentIds = new java.util.ArrayList<>();
    private final Map<Long, FailedItem> failedItems = new ConcurrentHashMap<>();

    private RecordingDispatchPort(List<NotificationChannelOutboxItem> items, Instant expectedNow) {
      this.items = items;
      this.expectedNow = expectedNow;
    }

    @Override
    public List<NotificationChannelOutboxItem> claimPending(int limit, Instant now) {
      assertThat(limit).isEqualTo(2);
      assertThat(now).isEqualTo(expectedNow);
      return items;
    }

    @Override
    public void markSent(long id, Instant sentAt) {
      assertThat(sentAt).isEqualTo(expectedNow);
      sentIds.add(id);
    }

    @Override
    public void markSkipped(
        long id, Instant skippedAt, NotificationChannelDeliverySkipReason skipReason) {
      throw new AssertionError("skip is not expected in smoke");
    }

    @Override
    public void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage) {
      assertThat(failedAt).isEqualTo(expectedNow);
      failedItems.put(id, new FailedItem(nextAttemptAt, errorMessage));
    }

    @Override
    public void markQuarantined(long id, Instant quarantinedAt, String errorMessage) {
      throw new AssertionError("quarantine is not expected in smoke");
    }

    private java.util.List<Long> sentIds() {
      return sentIds;
    }

    private Map<Long, FailedItem> failedItems() {
      return failedItems;
    }
  }
}
