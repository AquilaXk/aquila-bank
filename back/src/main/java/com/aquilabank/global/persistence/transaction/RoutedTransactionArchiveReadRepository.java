package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionArchiveReadPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RoutedTransactionArchiveReadRepository implements TransactionArchiveReadPort {

  private final JdbcTransactionArchiveReadRepository delegate;
  private final TransactionReadRoutingPolicy routingPolicy;

  public RoutedTransactionArchiveReadRepository(
      JdbcTransactionArchiveReadRepository delegate, TransactionReadRoutingPolicy routingPolicy) {
    this.delegate = delegate;
    this.routingPolicy = routingPolicy;
  }

  @Override
  public TransactionSlice fetchArchived(TransactionQuery query) {
    TransactionReadRouteDecision decision = routingPolicy.routeArchive(query);
    return TransactionReadRouteContext.call(decision.route(), () -> delegate.fetchArchived(query));
  }
}
