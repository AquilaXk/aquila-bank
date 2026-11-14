package com.aquilabank.global.web.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
  void encodesBinaryHeaderAndRejectsOversizedOrMalformedPayloads() {
    String fingerprint = "UNREAD|TransferBooked|2026-03-21T00:00:00Z|2026-04-21T00:00:00Z";
    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T09:00:00.123456789Z"),
            41L,
            Instant.parse("2026-03-21T00:00:00.000000001Z"),
            Instant.parse("2026-04-21T00:00:00.999999999Z"),
            fingerprint);

    String encoded = NotificationSearchCursorCodec.encode(cursor);

    assertThat(Base64.getUrlDecoder().decode(encoded))
        .hasSize(44 + fingerprint.getBytes(StandardCharsets.UTF_8).length);
    assertThat(NotificationSearchCursorCodec.decode(encoded)).isEqualTo(cursor);

    NotificationSearchCursor oversizedFingerprintCursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T09:00:00Z"),
            41L,
            Instant.parse("2026-03-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:00:00Z"),
            "f".repeat(321));
    assertThatThrownBy(() -> NotificationSearchCursorCodec.encode(oversizedFingerprintCursor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("filterFingerprint exceeds maximum length");

    String emptyFingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[44]);
    String oversizedDecodedFingerprint =
        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[44 + 321]);
    byte[] invalidNano =
        ByteBuffer.allocate(46)
            .putLong(1_700_000_000L)
            .putInt(1_000_000_000)
            .putLong(1L)
            .putLong(1_700_000_000L)
            .putInt(0)
            .putLong(1_700_000_100L)
            .putInt(0)
            .put((byte) 'f')
            .put((byte) 'p')
            .array();
    String badNanoEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(invalidNano);

    assertThatThrownBy(() -> NotificationSearchCursorCodec.encode(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode("A".repeat(513)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode(emptyFingerprint))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode(oversizedDecodedFingerprint))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cursor format is invalid");
    assertThatThrownBy(() -> NotificationSearchCursorCodec.decode(badNanoEncoded))
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
  void rejectsSearchSliceWhenNextCursorWindowDiffers() {
    NotificationSearchCursor nextCursor =
        new NotificationSearchCursor(
            Instant.parse("2026-04-20T00:00:00Z"),
            41L,
            Instant.parse("2026-03-20T00:00:00Z"),
            Instant.parse("2026-04-20T00:00:00Z"),
            "UNREAD|TransferBooked|2026-03-20T00:00:00Z|2026-04-20T00:00:00Z");

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
                            Instant.parse("2026-04-20T00:10:00Z"),
                            null)),
                    nextCursor,
                    true,
                    20,
                    Instant.parse("2026-03-21T00:00:00Z"),
                    Instant.parse("2026-04-21T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("nextCursor window must match slice window");
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
