package com.aquilabank.global.web.ledger;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class LedgerAuditLookupApiIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-21T00:00:00Z");

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;
  private long firstEntryId;
  private long secondEntryId;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    commit(
        transactionManager,
        () -> {
          long accountId = insertAccount("audit api account");
          firstEntryId =
              insertLedgerEntry(
                  accountId,
                  "TRX-audit-api-001",
                  "ENT-audit-api-001",
                  "DEBIT",
                  "BOOKED",
                  1_000L,
                  "audit api first",
                  "audit-api-request-001",
                  BASE);
          secondEntryId =
              insertLedgerEntry(
                  accountId,
                  "TRX-audit-api-001",
                  "ENT-audit-api-002",
                  "CREDIT",
                  "BOOKED",
                  1_000L,
                  "audit api second",
                  "audit-api-request-001",
                  BASE.plusSeconds(1));
          insertLedgerEntry(
              accountId,
              "TRX-audit-api-002",
              "ENT-audit-api-003",
              "DEBIT",
              "BOOKED",
              500L,
              "audit api other",
              "audit-api-request-002",
              BASE.plusSeconds(2));
        });
  }

  @Test
  void findsLedgerEntriesByRequestIdWithLimitCap() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/ledger/audit/request-ids/audit-api-request-001/entries")
                .header("Authorization", ledgerOpsAuthorization())
                .param("limit", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lookupType").value("REQUEST_ID"))
        .andExpect(jsonPath("$.lookupValue").value("audit-api-request-001"))
        .andExpect(jsonPath("$.afterEntryId").value(0))
        .andExpect(jsonPath("$.limit").value(100))
        .andExpect(jsonPath("$.nextAfterEntryId").value(secondEntryId))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(firstEntryId))
        .andExpect(jsonPath("$.items[0].entryReference").value("ENT-audit-api-001"))
        .andExpect(jsonPath("$.items[0].metadata").doesNotExist());
  }

  @Test
  void findsLedgerEntriesByTransactionReferenceWithCursor() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/ledger/audit/transactions/TRX-audit-api-001/entries")
                .header("Authorization", ledgerOpsAuthorization())
                .param("afterEntryId", String.valueOf(firstEntryId))
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lookupType").value("TRANSACTION_REFERENCE"))
        .andExpect(jsonPath("$.lookupValue").value("TRX-audit-api-001"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(secondEntryId))
        .andExpect(jsonPath("$.items[0].transactionReference").value("TRX-audit-api-001"));
  }

  @Test
  void findsLedgerEntryByEntryReference() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/ledger/audit/entries/ENT-audit-api-001")
                .header("Authorization", ledgerOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstEntryId))
        .andExpect(jsonPath("$.entryReference").value("ENT-audit-api-001"))
        .andExpect(jsonPath("$.transactionReference").value("TRX-audit-api-001"))
        .andExpect(jsonPath("$.traceId").value("audit-api-request-001"))
        .andExpect(jsonPath("$.metadata").doesNotExist());
  }

  @Test
  void returnsNotFoundWhenEntryReferenceIsMissing() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/ledger/audit/entries/ENT-audit-api-missing")
                .header("Authorization", ledgerOpsAuthorization()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("ledger audit entry is not found"));
  }

  @Test
  void rejectsWithoutLedgerOpsScope() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/ledger/audit/request-ids/audit-api-request-001/entries")
                .header("Authorization", accountAdminAuthorization()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  private long insertAccount(String displayName) {
    Long id =
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
                :now,
                :now
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("now", Timestamp.from(BASE)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return id;
  }

  private long insertLedgerEntry(
      long accountId,
      String transactionReference,
      String entryReference,
      String direction,
      String entryStatus,
      long amountMinor,
      String description,
      String traceId,
      Instant bookedAt) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger_entry (
                account_id,
                transaction_reference,
                entry_reference,
                direction,
                entry_status,
                amount_minor,
                currency_code,
                booked_at,
                occurred_at,
                description,
                trace_id,
                metadata,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                :transactionReference,
                :entryReference,
                :direction,
                :entryStatus,
                :amountMinor,
                'KRW',
                :bookedAt,
                :bookedAt,
                :description,
                :traceId,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("transactionReference", transactionReference)
                .addValue("entryReference", entryReference)
                .addValue("direction", direction)
                .addValue("entryStatus", entryStatus)
                .addValue("amountMinor", amountMinor)
                .addValue("description", description)
                .addValue("traceId", traceId)
                .addValue("bookedAt", Timestamp.from(bookedAt)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger_entry insert did not return id");
    }
    return id;
  }

  private String ledgerOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "ledger-ops", java.util.Set.of(InternalServiceScope.LEDGER_OPS));
  }

  private String accountAdminAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "account-admin", java.util.Set.of(InternalServiceScope.ACCOUNT_ADMIN));
  }
}
