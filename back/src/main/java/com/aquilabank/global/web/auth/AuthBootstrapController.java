package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;
import com.aquilabank.domain.auth.model.UserBootstrapCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertUseCase;
import com.aquilabank.domain.auth.usecase.UserBootstrapUseCase;
import com.aquilabank.global.security.AuthBootstrapApiProperties;
import com.aquilabank.global.security.BootstrapApiAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내부 운영/테스트가 user bootstrap과 membership upsert를 같은 경로 규칙으로 호출합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/auth")
@ConditionalOnProperty(name = "security.auth-bootstrap-api.enabled", havingValue = "true")
public class AuthBootstrapController {

  private final UserBootstrapUseCase userBootstrapUseCase;
  private final UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase;
  private final AuthBootstrapApiProperties authBootstrapApiProperties;

  public AuthBootstrapController(
      UserBootstrapUseCase userBootstrapUseCase,
      UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase,
      AuthBootstrapApiProperties authBootstrapApiProperties) {
    this.userBootstrapUseCase = userBootstrapUseCase;
    this.userAccountMembershipUpsertUseCase = userAccountMembershipUpsertUseCase;
    this.authBootstrapApiProperties = authBootstrapApiProperties;
  }

  @PostMapping("/users/bootstrap")
  public UserBootstrapResponse bootstrapUser(
      HttpServletRequest httpServletRequest, @Valid @RequestBody UserBootstrapRequest request) {
    validateBootstrapToken(httpServletRequest);

    UserBootstrapResult result =
        userBootstrapUseCase.bootstrap(
            new UserBootstrapCommand(request.loginId(), request.password(), request.displayName()));
    return UserBootstrapResponse.from(result);
  }

  @PutMapping("/users/{userId}/memberships/{accountId}")
  public UserAccountMembershipResponse upsertMembership(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "userId must be positive") long userId,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @Valid @RequestBody UserAccountMembershipRequest request) {
    validateBootstrapToken(httpServletRequest);

    UserAccountMembership membership =
        userAccountMembershipUpsertUseCase.upsert(
            new UserAccountMembershipUpsertCommand(
                userId, accountId, request.membershipRole(), request.membershipStatus()));
    return UserAccountMembershipResponse.from(membership);
  }

  private void validateBootstrapToken(HttpServletRequest httpServletRequest) {
    String bootstrapToken = httpServletRequest.getHeader(authBootstrapApiProperties.tokenHeader());

    // permitAll endpoint라도 shared token 검증이 없으면 내부 bootstrap이 외부 요청에 그대로 열립니다.
    if (bootstrapToken == null
        || bootstrapToken.isBlank()
        || !authBootstrapApiProperties.token().equals(bootstrapToken)) {
      throw new BootstrapApiAccessDeniedException("bootstrap token is invalid");
    }
  }

  /** 내부 사용자 bootstrap 요청 body */
  public record UserBootstrapRequest(
      @NotBlank(message = "loginId is required") @Size(max = 80, message = "loginId must be 80 characters or less") String loginId,
      @NotBlank(message = "password is required") @Size(max = 120, message = "password must be 120 characters or less") String password,
      @NotBlank(message = "displayName is required") @Size(max = 80, message = "displayName must be 80 characters or less") String displayName) {}

  /** 내부 membership upsert 요청 body */
  public record UserAccountMembershipRequest(
      @NotNull(message = "membershipRole is required") MembershipRole membershipRole,
      @NotNull(message = "membershipStatus is required") MembershipStatus membershipStatus) {}

  /** 내부 사용자 bootstrap 완료 응답 */
  public record UserBootstrapResponse(
      long userId,
      String loginId,
      String displayName,
      String userStatus,
      java.time.Instant createdAt) {

    static UserBootstrapResponse from(UserBootstrapResult result) {
      return new UserBootstrapResponse(
          result.userId(),
          result.loginId(),
          result.displayName(),
          result.status().name(),
          result.createdAt());
    }
  }

  /** 내부 membership upsert 완료 응답 */
  public record UserAccountMembershipResponse(
      long userId, long accountId, String membershipRole, String membershipStatus) {

    static UserAccountMembershipResponse from(UserAccountMembership membership) {
      return new UserAccountMembershipResponse(
          membership.userId(),
          membership.accountId(),
          membership.role().name(),
          membership.status().name());
    }
  }
}
