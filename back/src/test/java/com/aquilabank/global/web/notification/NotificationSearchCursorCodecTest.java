package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationSearchCursorCodecTest {

  @Test
  void encodesAndDecodesSearchCursor() {
    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T09:00:00Z"),
            41L,
            Instant.parse("2026-03-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"),
            "UNREAD|TransferBooked|2026-03-21T00:00:00Z|2026-04-21T00:00:00Z");

    String encoded = NotificationSearchCursorCodec.encode(cursor);

    assertThat(NotificationSearchCursorCodec.decode(encoded)).isEqualTo(cursor);
  }

  @Test
  void rejectsBrokenCursorPayload() {
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode("broken"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
  }

  @Test
  void rejectsCursorOutsideAppliedWindow() {
    assertThatThrownBy(
            () ->
                new NotificationSearchCursor(
                    Instant.parse("2026-03-20T23:59:59Z"),
                    41L,
                    Instant.parse("2026-03-21T00:00:00Z"),
                    Instant.parse("2026-04-21T00:00:00Z"),
                    "UNREAD|TransferBooked|2026-03-21T00:00:00Z|2026-04-21T00:00:00Z"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSearchSliceWithClosedResultAndNextCursor() {
    NotificationSearchCursor nextCursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T09:00:00Z"),
            41L,
            Instant.parse("2026-03-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"),
            "UNREAD|TransferBooked|2026-03-21T00:00:00Z|2026-04-21T00:00:00Z");

    assertThatThrownBy(
            () ->
                new NotificationSearchSlice(
                    List.of(
                        new NotificationSummary(
                            10L,
                            101L,
                            "TransferBooked",
                            "입금 완료",
                            "급여가 입금되었습니다.",
                            Instant.parse("2026-04-21T00:10:00Z"),
                            null)),
                    nextCursor,
                    false,
                    20,
                    Instant.parse("2026-03-21T00:00:00Z"),
                    Instant.parse("2026-04-21T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mapsSearchResponseWithNextCursorAndWindow() {
    NotificationSearchCursor nextCursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T09:00:00Z"),
            41L,
            Instant.parse("2026-03-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"),
            "UNREAD|TransferBooked|2026-03-21T00:00:00Z|2026-04-21T00:00:00Z");
    NotificationSearchSlice slice =
        new NotificationSearchSlice(
            List.of(
                new NotificationSummary(
                    10L,
                    101L,
                    "TransferBooked",
                    "입금 완료",
                    "급여가 입금되었습니다.",
                    Instant.parse("2026-04-21T00:10:00Z"),
                    null)),
            nextCursor,
            true,
            20,
            Instant.parse("2026-03-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"));

    NotificationSearchResponse response = NotificationSearchResponse.from(slice);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).notificationId()).isEqualTo(10L);
    assertThat(response.items().get(0).accountId()).isEqualTo(101L);
    assertThat(response.items().get(0).title()).isEqualTo("입금 완료");
    assertThat(response.hasNext()).isTrue();
    assertThat(response.limit()).isEqualTo(20);
    assertThat(response.appliedFrom()).isEqualTo(Instant.parse("2026-03-21T00:00:00Z"));
    assertThat(response.appliedTo()).isEqualTo(Instant.parse("2026-04-21T00:00:00Z"));
    assertThat(response.nextCursor()).isEqualTo(NotificationSearchCursorCodec.encode(nextCursor));
  }
}
