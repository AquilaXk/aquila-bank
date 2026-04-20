package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonNormalizer;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenLookupView;
import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipStatusUpdateUseCase;
import com.aquilabank.domain.auth.usecase.UserStatusUpdateUseCase;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalAuthAdminController(
      AuthUserQueryUseCase authUserQueryUseCase,
      UserStatusUpdateUseCase userStatusUpdateUseCase,
      UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase,
      UserAccountMembershipStatusUpdateUseCase userAccountMembershipStatusUpdateUseCase,
      PasswordRecoveryTokenQueryUseCase passwordRecoveryTokenQueryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.authUserQueryUseCase = authUserQueryUseCase;
    this.userStatusUpdateUseCase = userStatusUpdateUseCase;
    this.userAccountMembershipQueryUseCase = userAccountMembershipQueryUseCase;
    this.userAccountMembershipStatusUpdateUseCase = userAccountMembershipStatusUpdateUseCase;
    this.passwordRecoveryTokenQueryUseCase = passwordRecoveryTokenQueryUseCase;
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
}
