package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RoutedTransactionReadRepository implements TransactionReadPort {

  private final JdbcTransactionReadRepository delegate;
  private final TransactionReadRoutingPolicy routingPolicy;

  public RoutedTransactionReadRepository(
      JdbcTransactionReadRepository delegate, TransactionReadRoutingPolicy routingPolicy) {
    this.delegate = delegate;
    this.routingPolicy = routingPolicy;
  }

  @Override
  public TransactionSlice fetch(TransactionQuery query) {
    TransactionReadRouteDecision decision = routingPolicy.routeTimeline(query);
    return TransactionReadRouteContext.call(decision.route(), () -> delegate.fetch(query));
  }
}
