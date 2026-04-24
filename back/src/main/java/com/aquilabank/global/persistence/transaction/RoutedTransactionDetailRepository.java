package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.port.TransactionDetailReadPort;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RoutedTransactionDetailRepository implements TransactionDetailReadPort {

  private final JdbcTransactionDetailRepository delegate;
  private final TransactionReadRoutingPolicy routingPolicy;

  public RoutedTransactionDetailRepository(
      JdbcTransactionDetailRepository delegate, TransactionReadRoutingPolicy routingPolicy) {
    this.delegate = delegate;
    this.routingPolicy = routingPolicy;
  }

  @Override
  public Optional<TransactionDetail> find(TransactionDetailQuery query) {
    TransactionReadRouteDecision decision = routingPolicy.routeDetail();
    return TransactionReadRouteContext.call(decision.route(), () -> delegate.find(query));
  }
}
