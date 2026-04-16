package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.aquilabank.support.PostgresContainerTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "DB_STATEMENT_TIMEOUT_MS=10000",
      "spring.jdbc.template.query-timeout=1"
    })
class TransactionJdbcQueryTimeoutIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void jdbcQueryTimeoutCancelsSlowQueryBeforeDatasourceStatementTimeout() {
    long startedAt = System.nanoTime();
    Throwable thrown = catchThrowable(() -> executeSlowQuery(4.0d));
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(thrown).isInstanceOf(QueryTimeoutException.class);
    assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
    assertThat(rootCauseMessage(thrown)).doesNotContain("statement timeout");
  }

  private void executeSlowQuery(double sleepSeconds) {
    PreparedStatementCreator statementCreator =
        connection -> {
          var statement = connection.prepareStatement("SELECT pg_sleep(?)");
          statement.setDouble(1, sleepSeconds);
          return statement;
        };
    jdbcTemplate.execute(
        statementCreator,
        preparedStatement -> {
          preparedStatement.execute();
          return null;
        });
  }

  private String rootCauseMessage(Throwable thrown) {
    Throwable rootCause = thrown;
    while (rootCause.getCause() != null) {
      rootCause = rootCause.getCause();
    }
    return rootCause.getMessage();
  }
}
