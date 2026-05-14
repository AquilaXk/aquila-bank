package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.model.RecipientPreviewThrottleDecision;
import java.sql.ResultSet;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcRecipientPreviewThrottleRepositoryTest {

  @Test
  void consumesPreviewAttemptAndAllowsWithinWindow() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcRecipientPreviewThrottleRepository repository =
        new JdbcRecipientPreviewThrottleRepository(jdbcTemplate);
    when(jdbcTemplate.queryForObject(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<JdbcRecipientPreviewThrottleRepository.ThrottleRow>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<JdbcRecipientPreviewThrottleRepository.ThrottleRow> mapper =
                  invocation.getArgument(2);
              return mapper.mapRow(row(Instant.parse("2026-05-14T00:00:00Z"), 3), 0);
            });

    RecipientPreviewThrottleDecision result =
        repository.consume(7L, Instant.parse("2026-05-14T00:00:10Z"), 20, 60L);

    assertThat(result.permitted()).isTrue();
    assertThat(result.retryAfterSeconds()).isZero();
  }

  @Test
  void returnsRetryAfterWhenAttemptExceedsLimit() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcRecipientPreviewThrottleRepository repository =
        new JdbcRecipientPreviewThrottleRepository(jdbcTemplate);
    when(jdbcTemplate.queryForObject(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<JdbcRecipientPreviewThrottleRepository.ThrottleRow>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<JdbcRecipientPreviewThrottleRepository.ThrottleRow> mapper =
                  invocation.getArgument(2);
              return mapper.mapRow(row(Instant.parse("2026-05-14T00:00:00Z"), 21), 0);
            });

    RecipientPreviewThrottleDecision result =
        repository.consume(7L, Instant.parse("2026-05-14T00:00:15Z"), 20, 60L);

    assertThat(result.permitted()).isFalse();
    assertThat(result.retryAfterSeconds()).isEqualTo(45L);
  }

  @Test
  void rejectsInvalidThrottleBoundary() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcRecipientPreviewThrottleRepository repository =
        new JdbcRecipientPreviewThrottleRepository(jdbcTemplate);
    Instant now = Instant.parse("2026-05-14T00:00:15Z");

    assertThatThrownBy(() -> repository.consume(0L, now, 20, 60L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId must be positive");
    assertThatThrownBy(() -> repository.consume(7L, now, 0, 60L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxAttempts must be positive");
    assertThatThrownBy(() -> repository.consume(7L, now, 20, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("windowSeconds must be positive");
  }

  private static ResultSet row(Instant windowStart, int attempts) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong("window_start_epoch_second")).thenReturn(windowStart.getEpochSecond());
    when(rs.getInt("attempts")).thenReturn(attempts);
    return rs;
  }
}
