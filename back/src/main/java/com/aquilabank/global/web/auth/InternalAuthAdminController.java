package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonNormalizer;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.ExternalIdentityLinkCommand;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.ExternalIdentityUnlinkCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenLookupView;
import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;
import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.model.VerifiedContactUpsertCommand;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
import com.aquilabank.domain.auth.usecase.ExternalIdentityMappingLinkUseCase;
import com.aquilabank.domain.auth.usecase.ExternalIdentityMappingUnlinkUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipStatusUpdateUseCase;
import com.aquilabank.domain.auth.usecase.UserStatusUpdateUseCase;
import com.aquilabank.domain.auth.usecase.VerifiedContactAdminUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenClaims;
import com.aquilabank.global.web.RequestTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내부 auth 관리 exact lookup과 revoke/update를 bootstrap surface와 같은 보호 규칙으로 노출합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/auth")
@ConditionalOnProperty(name = "security.auth-bootstrap-api.enabled", havingValue = "true")
public class InternalAuthAdminController {

  private final AuthUserQueryUseCase authUserQueryUseCase;
  private final UserStatusUpdateUseCase userStatusUpdateUseCase;
  private final UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase;
  private final UserAccountMembershipStatusUpdateUseCase userAccountMembershipStatusUpdateUseCase;
  private final PasswordRecoveryTokenQueryUseCase passwordRecoveryTokenQueryUseCase;
  private final ExternalIdentityMappingLinkUseCase externalIdentityMappingLinkUseCase;
  private final ExternalIdentityMappingUnlinkUseCase externalIdentityMappingUnlinkUseCase;
  private final VerifiedContactAdminUseCase verifiedContactAdminUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalAuthAdminController(
      AuthUserQueryUseCase authUserQueryUseCase,
      UserStatusUpdateUseCase userStatusUpdateUseCase,
      UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase,
      UserAccountMembershipStatusUpdateUseCase userAccountMembershipStatusUpdateUseCase,
      PasswordRecoveryTokenQueryUseCase passwordRecoveryTokenQueryUseCase,
      ExternalIdentityMappingLinkUseCase externalIdentityMappingLinkUseCase,
      ExternalIdentityMappingUnlinkUseCase externalIdentityMappingUnlinkUseCase,
      VerifiedContactAdminUseCase verifiedContactAdminUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.authUserQueryUseCase = authUserQueryUseCase;
    this.userStatusUpdateUseCase = userStatusUpdateUseCase;
    this.userAccountMembershipQueryUseCase = userAccountMembershipQueryUseCase;
    this.userAccountMembershipStatusUpdateUseCase = userAccountMembershipStatusUpdateUseCase;
    this.passwordRecoveryTokenQueryUseCase = passwordRecoveryTokenQueryUseCase;
    this.externalIdentityMappingLinkUseCase = externalIdentityMappingLinkUseCase;
    this.externalIdentityMappingUnlinkUseCase = externalIdentityMappingUnlinkUseCase;
    this.verifiedContactAdminUseCase = verifiedContactAdminUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @GetMapping("/users/{userId}")
  public AuthUserResponse getUser(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return AuthUserResponse.from(authUserQueryUseCase.getByUserId(userId));
  }

  @GetMapping("/users/by-login-id")
  public AuthUserResponse getUserByLoginId(
      HttpServletRequest httpServletRequest,
      @RequestParam @NotBlank(message = "loginId is required") String loginId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return AuthUserResponse.from(authUserQueryUseCase.getByLoginId(loginId));
  }

  @GetMapping("/users/{userId}/memberships/{accountId}")
  public UserAccountMembershipResponse getMembership(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @PathVariable @Positive(message = "accountId must be positive") long accountId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return UserAccountMembershipResponse.from(
        userAccountMembershipQueryUseCase.getByUserIdAndAccountId(userId, accountId));
  }

  /** 내부 운영용 recovery handoff requestId exact lookup endpoint입니다. */
  @GetMapping("/password-recovery-tokens/by-request-id")
  public PasswordRecoveryTokenResponse getPasswordRecoveryToken(
      HttpServletRequest httpServletRequest,
      @RequestParam("requestId")
          @NotBlank(message = "requestId is required") @Size(max = 64, message = "requestId must be 64 characters or less") String handoffRequestId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return PasswordRecoveryTokenResponse.from(
        passwordRecoveryTokenQueryUseCase.getByHandoffRequestId(handoffRequestId));
  }

  @GetMapping("/users/{userId}/verified-contacts")
  public List<VerifiedContactResponse> getVerifiedContacts(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return verifiedContactAdminUseCase.findByUserId(userId).stream()
        .map(VerifiedContactResponse::from)
        .toList();
  }

  @PutMapping("/users/{userId}/verified-contacts/{contactChannel}")
  public VerifiedContactResponse upsertVerifiedContact(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @PathVariable VerifiedContactChannel contactChannel,
      @Valid @RequestBody VerifiedContactRequest request) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return VerifiedContactResponse.from(
        verifiedContactAdminUseCase.upsert(
            new VerifiedContactUpsertCommand(
                userId, contactChannel, request.providerDestination(), Instant.now())));
  }

  @DeleteMapping("/users/{userId}/verified-contacts/{contactChannel}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteVerifiedContact(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @PathVariable VerifiedContactChannel contactChannel) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    verifiedContactAdminUseCase.delete(userId, contactChannel);
  }

  @PostMapping("/users/{userId}/external-identities")
  public ExternalIdentityMappingResponse linkExternalIdentity(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @Valid @RequestBody ExternalIdentityMappingRequest request) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(
            httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return ExternalIdentityMappingResponse.from(
        externalIdentityMappingLinkUseCase.link(
            new ExternalIdentityLinkCommand(
                userId,
                request.providerId(),
                request.subject(),
                resolveReason(request.reasonCode(), request.reasonDetail(), null),
                claims.subject(),
                resolveRequestId(httpServletRequest))));
  }

  @DeleteMapping("/users/{userId}/external-identities")
  public ExternalIdentityMappingResponse unlinkExternalIdentity(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @Valid @RequestBody ExternalIdentityMappingRequest request) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(
            httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return ExternalIdentityMappingResponse.from(
        externalIdentityMappingUnlinkUseCase.unlink(
            new ExternalIdentityUnlinkCommand(
                userId,
                request.providerId(),
                request.subject(),
                resolveReason(request.reasonCode(), request.reasonDetail(), null),
                claims.subject(),
                resolveRequestId(httpServletRequest))));
  }

  @PutMapping("/users/{userId}/status")
  public AuthUserResponse updateUserStatus(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @Valid @RequestBody UserStatusRequest request) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(
            httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return AuthUserResponse.from(
        userStatusUpdateUseCase.update(
            new UserStatusUpdateCommand(
                userId,
                request.userStatus(),
                resolveReason(request.reasonCode(), request.reasonDetail(), request.reason()),
                claims.subject(),
                resolveRequestId(httpServletRequest))));
  }

  @PutMapping("/users/{userId}/memberships/{accountId}/status")
  public UserAccountMembershipResponse updateMembershipStatus(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @Valid @RequestBody UserAccountMembershipStatusRequest request) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(
            httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return UserAccountMembershipResponse.from(
        userAccountMembershipStatusUpdateUseCase.update(
            new UserAccountMembershipStatusUpdateCommand(
                userId,
                accountId,
                request.membershipStatus(),
                resolveReason(request.reasonCode(), request.reasonDetail(), request.reason()),
                claims.subject(),
                resolveRequestId(httpServletRequest))));
  }

  /** 내부 user status update 요청 body */
  public record UserStatusRequest(
      @NotNull(message = "userStatus is required") UserStatus userStatus,
      AuthStatusChangeReasonCode reasonCode,
      @Size(
              max = AuthStatusChangeReason.MAX_REASON_DETAIL_LENGTH,
              message = "reasonDetail must be 200 characters or less")
          String reasonDetail,
      @Size(
              max = AuthStatusChangeReason.MAX_REASON_DETAIL_LENGTH,
              message = "reason must be 200 characters or less")
          String reason) {}

  /** 내부 membership status update 요청 body */
  public record UserAccountMembershipStatusRequest(
      @NotNull(message = "membershipStatus is required") com.aquilabank.domain.auth.model.MembershipStatus membershipStatus,
      AuthStatusChangeReasonCode reasonCode,
      @Size(
              max = AuthStatusChangeReason.MAX_REASON_DETAIL_LENGTH,
              message = "reasonDetail must be 200 characters or less")
          String reasonDetail,
      @Size(
              max = AuthStatusChangeReason.MAX_REASON_DETAIL_LENGTH,
              message = "reason must be 200 characters or less")
          String reason) {}

  /** 내부 external identity 매핑 생성/삭제 요청 body */
  public record ExternalIdentityMappingRequest(
      @NotBlank(message = "providerId is required") @Size(max = 64, message = "providerId must be 64 characters or less") String providerId,
      @NotBlank(message = "subject is required") @Size(max = 255, message = "subject must be 255 characters or less") String subject,
      @NotNull(message = "reasonCode is required") AuthStatusChangeReasonCode reasonCode,
      @NotBlank(message = "reasonDetail is required") @Size(
              max = AuthStatusChangeReason.MAX_REASON_DETAIL_LENGTH,
              message = "reasonDetail must be 200 characters or less")
          String reasonDetail) {}

  /** internal admin verified contact upsert 요청 body */
  public record VerifiedContactRequest(
      @NotBlank(message = "providerDestination is required") @Size(max = 255, message = "providerDestination must be 255 characters or less") String providerDestination) {}

  private String resolveRequestId(HttpServletRequest httpServletRequest) {
    return RequestTraceContext.currentRequestId()
        .orElseGet(
            () -> {
              String requestId =
                  httpServletRequest.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
              if (requestId == null || requestId.isBlank()) {
                throw new IllegalStateException("requestId is not initialized");
              }
              return requestId;
            });
  }

  private AuthStatusChangeReason resolveReason(
      AuthStatusChangeReasonCode reasonCode, String reasonDetail, String reason) {
    return AuthStatusChangeReasonNormalizer.normalize(reasonCode, reasonDetail, reason);
  }

  /** 내부 auth user exact lookup 응답 */
  public record AuthUserResponse(
      long userId,
      String loginId,
      String displayName,
      String userStatus,
      Instant createdAt,
      Instant updatedAt) {

    static AuthUserResponse from(AuthUserSummary summary) {
      return new AuthUserResponse(
          summary.userId(),
          summary.loginId(),
          summary.displayName(),
          summary.status().name(),
          summary.createdAt(),
          summary.updatedAt());
    }
  }

  /** 내부 auth membership exact lookup 응답 */
  public record UserAccountMembershipResponse(
      long userId,
      long accountId,
      String membershipRole,
      String membershipStatus,
      Instant createdAt,
      Instant updatedAt) {

    static UserAccountMembershipResponse from(UserAccountMembershipSummary summary) {
      return new UserAccountMembershipResponse(
          summary.userId(),
          summary.accountId(),
          summary.role().name(),
          summary.status().name(),
          summary.createdAt(),
          summary.updatedAt());
    }
  }

  /** 내부 password recovery handoff requestId exact lookup 응답 */
  public record PasswordRecoveryTokenResponse(
      String requestId,
      long userId,
      String loginId,
      String recoveryToken,
      String tokenStatus,
      Instant expiresAt,
      Instant usedAt,
      Instant createdAt) {

    static PasswordRecoveryTokenResponse from(PasswordRecoveryTokenLookupView view) {
      return new PasswordRecoveryTokenResponse(
          view.requestId(),
          view.userId(),
          view.loginId(),
          view.recoveryToken(),
          view.tokenStatus().name(),
          view.expiresAt(),
          view.usedAt(),
          view.createdAt());
    }
  }

  /** 내부 external identity 매핑 생성/삭제 응답 */
  public record ExternalIdentityMappingResponse(
      long userId, String providerId, String subject, Instant createdAt, Instant updatedAt) {

    static ExternalIdentityMappingResponse from(ExternalIdentityMapping mapping) {
      return new ExternalIdentityMappingResponse(
          mapping.userId(),
          mapping.providerId(),
          mapping.subject(),
          mapping.createdAt(),
          mapping.updatedAt());
    }
  }

  /** internal admin verified contact 응답 */
  public record VerifiedContactResponse(
      long userId,
      String contactChannel,
      String providerDestination,
      Instant verifiedAt,
      Instant createdAt,
      Instant updatedAt) {

    static VerifiedContactResponse from(VerifiedContact contact) {
      return new VerifiedContactResponse(
          contact.userId(),
          contact.channel().name(),
          contact.providerDestination(),
          contact.verifiedAt(),
          contact.createdAt(),
          contact.updatedAt());
    }
  }
}
