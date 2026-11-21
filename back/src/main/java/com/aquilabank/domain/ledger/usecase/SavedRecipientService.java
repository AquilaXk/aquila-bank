package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.exception.SavedRecipientNotFoundException;
import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import com.aquilabank.domain.ledger.port.SavedRecipientPort;
import com.aquilabank.standard.util.DomainGuards;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 자주 쓰는 입금계좌(수취인 주소록) 저장 및 상한 검증 유스케이스입니다. */
public final class SavedRecipientService implements SavedRecipientUseCase {

  public static final int MAX_RECIPIENTS_PER_USER = 100;

  private final SavedRecipientPort savedRecipientPort;
  private final Clock clock;

  public SavedRecipientService(SavedRecipientPort savedRecipientPort, Clock clock) {
    this.savedRecipientPort = Objects.requireNonNull(savedRecipientPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public List<SavedRecipient> list(long userId) {
    DomainGuards.requirePositive(userId, "userId");
    return savedRecipientPort.findByUserId(userId, MAX_RECIPIENTS_PER_USER);
  }

  @Override
  public SavedRecipient save(SaveSavedRecipientCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    boolean alreadySaved =
        savedRecipientPort.existsByUserAndTarget(
            command.userId(), command.institutionCode(), command.accountNumber());
    if (!alreadySaved
        && savedRecipientPort.countByUserId(command.userId()) >= MAX_RECIPIENTS_PER_USER) {
      throw new IllegalArgumentException("saved recipient limit exceeded");
    }
    return savedRecipientPort.upsert(command, Instant.now(clock));
  }

  @Override
  public void delete(long userId, long recipientId) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(recipientId, "recipientId");
    if (!savedRecipientPort.deleteByUserAndId(userId, recipientId)) {
      throw new SavedRecipientNotFoundException("saved recipient not found");
    }
  }
}
