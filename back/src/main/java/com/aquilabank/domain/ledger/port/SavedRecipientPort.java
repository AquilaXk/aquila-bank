package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import java.time.Instant;
import java.util.List;

public interface SavedRecipientPort {

  List<SavedRecipient> findByUserId(long userId, int limit);

  int countByUserId(long userId);

  boolean existsByUserAndTarget(long userId, String institutionCode, String accountNumber);

  SavedRecipient upsert(SaveSavedRecipientCommand command, Instant now);

  boolean deleteByUserAndId(long userId, long recipientId);
}
