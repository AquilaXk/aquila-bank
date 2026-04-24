package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.model.VerifiedContactUpsertCommand;
import java.util.List;
import java.util.Optional;

public interface VerifiedContactPort {

  List<VerifiedContact> findByUserId(long userId);

  Optional<VerifiedContact> findByUserIdAndChannel(long userId, VerifiedContactChannel channel);

  Optional<VerifiedContact> findPreferredForPasswordRecovery(long userId);

  VerifiedContact upsert(VerifiedContactUpsertCommand command);

  boolean delete(long userId, VerifiedContactChannel channel);
}
