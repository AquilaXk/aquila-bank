package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.ExternalIdentityLinkCommand;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.ExternalIdentityUnlinkCommand;

/** external identity 매핑 변경과 감사 기록을 같은 저장소 경로로 처리합니다. */
public interface ExternalIdentityMappingWritePort {

  ExternalIdentityMapping link(ExternalIdentityLinkCommand command);

  ExternalIdentityMapping unlink(ExternalIdentityUnlinkCommand command);
}
