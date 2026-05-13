package com.aquilabank.domain.customerapplication.port;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import java.util.List;
import java.util.Optional;

/** 고객 본인 신청 조회는 user_id index를 타는 bounded read path로 분리합니다. */
public interface CustomerApplicationReadPort {

  List<CustomerApplicationDetails> findByUserId(long userId, int limit);

  Optional<CustomerApplicationDetails> findByUserIdAndReference(
      long userId, String applicationReference);
}
