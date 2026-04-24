package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.model.VerifiedContactUpsertCommand;
import com.aquilabank.domain.auth.port.VerifiedContactPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** internal admin upsert는 trusted path라 요청 시각을 verified_at으로 고정합니다. */
public final class VerifiedContactAdminService implements VerifiedContactAdminUseCase {

  private final VerifiedContactPort verifiedContactPort;
  private final Clock clock;

  public VerifiedContactAdminService(VerifiedContactPort verifiedContactPort, Clock clock) {
    this.verifiedContactPort = verifiedContactPort;
    this.clock = clock;
  }

  @Override
  public List<VerifiedContact> findByUserId(long userId) {
    return verifiedContactPort.findByUserId(userId);
  }

  @Override
  public VerifiedContact upsert(VerifiedContactUpsertCommand command) {
    Instant verifiedAt = Instant.now(clock);
    return verifiedContactPort.upsert(
        new VerifiedContactUpsertCommand(
            command.userId(), command.channel(), command.providerDestination(), verifiedAt));
  }

  @Override
  public boolean delete(long userId, VerifiedContactChannel channel) {
    return verifiedContactPort.delete(userId, channel);
  }
}
