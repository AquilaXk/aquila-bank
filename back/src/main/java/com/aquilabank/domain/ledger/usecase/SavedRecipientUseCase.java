package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import java.util.List;

public interface SavedRecipientUseCase {

  List<SavedRecipient> list(long userId);

  SavedRecipient save(SaveSavedRecipientCommand command);

  void delete(long userId, long recipientId);
}
