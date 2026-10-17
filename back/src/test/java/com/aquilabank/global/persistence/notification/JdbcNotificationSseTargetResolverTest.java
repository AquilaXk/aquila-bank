package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcNotificationSseTargetResolverTest {

  @Test
  void queriesActiveUsersExcludingDisabledInAppTransactionalPreference() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    when(jdbcTemplate.queryForList(
            anyString(),
            org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
            eq(Long.class)))
        .thenReturn(List.of(11L, 22L));
    JdbcNotificationSseTargetResolver resolver =
        new JdbcNotificationSseTargetResolver(jdbcTemplate);

    List<Long> userIds = resolver.findActiveUserIdsByAccountId(101L);

    assertThat(userIds).containsExactly(11L, 22L);
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<MapSqlParameterSource> paramCaptor =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbcTemplate).queryForList(sqlCaptor.capture(), paramCaptor.capture(), eq(Long.class));
    assertThat(sqlCaptor.getValue())
        .contains("FROM notification_preference np")
        .contains("np.category = 'TRANSACTIONAL'")
        .contains("np.channel = 'IN_APP'")
        .contains("np.enabled = FALSE");
    assertThat(paramCaptor.getValue().getValue("accountId")).isEqualTo(101L);
  }
}
