package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import com.aquilabank.domain.transaction.model.TransactionCategory;
import com.aquilabank.domain.transaction.port.TransactionAnnotationPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 거래 내역 분류·메모 배치 조회 및 저장 JDBC 저장소입니다. */
@Repository
public class JdbcTransactionAnnotationRepository implements TransactionAnnotationPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransactionAnnotationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TransactionAnnotation> findByReferences(
      long userId, long accountId, List<String> transactionReferences) {
    return jdbcTemplate.query(
        """
        SELECT user_id,
               account_id,
               transaction_reference,
               category,
               memo,
               updated_at
        FROM user_transaction_annotation
        WHERE user_id = :userId
          AND account_id = :accountId
          AND transaction_reference IN (:references)
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("references", transactionReferences),
        (rs, rowNum) -> mapRow(rs));
  }

  @Override
  @Transactional
  public TransactionAnnotation upsert(SaveTransactionAnnotationCommand command, Instant now) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO user_transaction_annotation (
            user_id,
            account_id,
            transaction_reference,
            category,
            memo,
            updated_at
        ) VALUES (
            :userId,
            :accountId,
            :transactionReference,
            :category,
            :memo,
            :updatedAt
        )
        ON CONFLICT (user_id, account_id, transaction_reference) DO UPDATE
        SET category = EXCLUDED.category,
            memo = EXCLUDED.memo,
            updated_at = EXCLUDED.updated_at
        RETURNING user_id,
                  account_id,
                  transaction_reference,
                  category,
                  memo,
                  updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("accountId", command.accountId())
            .addValue("transactionReference", command.transactionReference())
            .addValue("category", command.category().name())
            .addValue("memo", command.memo())
            .addValue("updatedAt", Timestamp.from(now)),
        (rs, rowNum) -> mapRow(rs));
  }

  private static TransactionAnnotation mapRow(ResultSet rs) throws SQLException {
    return new TransactionAnnotation(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        rs.getString("transaction_reference"),
        TransactionCategory.valueOf(rs.getString("category")),
        rs.getString("memo"),
        rs.getTimestamp("updated_at").toInstant());
  }
}
