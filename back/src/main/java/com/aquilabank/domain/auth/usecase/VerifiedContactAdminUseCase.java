package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.model.VerifiedContactUpsertCommand;
import java.util.List;

public interface VerifiedContactAdminUseCase {

  List<VerifiedContact> findByUserId(long userId);

  VerifiedContact upsert(VerifiedContactUpsertCommand command);

  boolean delete(long userId, VerifiedContactChannel channel);
}
