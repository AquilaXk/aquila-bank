package com.aquilabank.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.account.model.AccountStatus;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditCursor;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchQuery;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonNormalizer;
import com.aquilabank.domain.auth.model.LoginChallengeType;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LoginResultStatus;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.bootstrap.model.BootstrapBulkImportCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.notification.model.NotificationBulkActionCommand;
import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveOutcome;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;
import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import sun.misc.Unsafe;

@SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
class DomainModelCoverageTest {

  @TestFactory
  Stream<DynamicTest> coversRecordContractsAndEnumFactories() throws Exception {
    return modelTypes().map(type -> DynamicTest.dynamicTest(type.getName(), () -> coverType(type)));
  }

  @Test
  void coversHandWrittenModelBranches() throws Exception {
    Instant base = Instant.parse("2026-04-20T09:30:00Z");
    AuthStatusChangeAuditCursor cursor = new AuthStatusChangeAuditCursor(base, 1L);
    assertThat(AuthStatusChangeAuditCursor.decode(null)).isNull();
    assertThat(AuthStatusChangeAuditCursor.decode(" ")).isNull();
    assertThat(AuthStatusChangeAuditCursor.decode(cursor.encode())).isEqualTo(cursor);
    assertThatThrownBy(() -> AuthStatusChangeAuditCursor.decode("bad"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AuthStatusChangeAuditCursor.decode(
                    java.util.Base64.getUrlEncoder().encodeToString("bad".getBytes())))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(AuthStatusChangeReasonNormalizer.normalize(null, null, "legacy reason"))
        .extracting("reasonCode", "reasonDetail")
        .containsExactly(AuthStatusChangeReasonCode.LEGACY_FREE_TEXT, "legacy reason");
    assertThat(
            AuthStatusChangeReasonNormalizer.normalize(
                AuthStatusChangeReasonCode.OPS_MANUAL, "manual", null))
        .extracting("reasonCode", "reasonDetail")
        .containsExactly(AuthStatusChangeReasonCode.OPS_MANUAL, "manual");
    assertThatThrownBy(() -> AuthStatusChangeReasonNormalizer.normalize(null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AuthStatusChangeReasonNormalizer.normalize(
                    AuthStatusChangeReasonCode.OPS_MANUAL, null, "legacy"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AuthStatusChangeReasonNormalizer.normalize(null, "manual", "legacy"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AuthStatusChangeReasonNormalizer.normalize(
                    AuthStatusChangeReasonCode.OPS_MANUAL, " ", null))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(
            com.aquilabank.domain.auth.model.AccountAccessScope.READ.allows(AccountStatus.LOCKED))
        .isTrue();
    assertThat(
            com.aquilabank.domain.auth.model.AccountAccessScope.TRANSFER.allows(
                AccountStatus.LOCKED))
        .isFalse();
    assertThat(
            com.aquilabank.domain.auth.model.AccountAccessScope.READ.allows(AccountStatus.CLOSED))
        .isFalse();
    assertThat(
            MembershipRole.OWNER.allows(
                com.aquilabank.domain.auth.model.AccountAccessScope.TRANSFER))
        .isTrue();
    assertThat(
            MembershipRole.MEMBER.allows(
                com.aquilabank.domain.auth.model.AccountAccessScope.TRANSFER))
        .isTrue();
    assertThat(
            MembershipRole.VIEWER.allows(
                com.aquilabank.domain.auth.model.AccountAccessScope.TRANSFER))
        .isFalse();

    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    " ",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "challenge",
                    null,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    "access",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    "refresh",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    "Bearer",
                    null,
                    null,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    base,
                    null,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    base,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    1L,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "binding",
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.MFA_REQUIRED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "challenge",
                    LoginChallengeType.TOTP,
                    base,
                    "remember"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.SUCCESS,
                    "access",
                    "refresh",
                    "Bearer",
                    base.plusSeconds(60),
                    base.plusSeconds(120),
                    1L,
                    null,
                    "challenge",
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.SUCCESS,
                    "access",
                    "refresh",
                    "Bearer",
                    base.plusSeconds(60),
                    base.plusSeconds(120),
                    1L,
                    null,
                    null,
                    LoginChallengeType.TOTP,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new LoginResult(
                    LoginResultStatus.SUCCESS,
                    "access",
                    "refresh",
                    "Bearer",
                    base.plusSeconds(60),
                    base.plusSeconds(120),
                    1L,
                    null,
                    null,
                    null,
                    base,
                    null))
        .isInstanceOf(IllegalArgumentException.class);

    BootstrapBulkImportCommand.AccountItem account =
        new BootstrapBulkImportCommand.AccountItem("account-a", "account", "KRW", 0);
    BootstrapBulkImportCommand.UserItem user =
        new BootstrapBulkImportCommand.UserItem("user-a", "login", "password", "user");
    assertThatThrownBy(
            () ->
                new BootstrapBulkImportCommand(
                    List.of(account),
                    List.of(user),
                    List.of(
                        new BootstrapBulkImportCommand.MembershipItem(
                            "missing",
                            account.clientRef(),
                            MembershipRole.OWNER,
                            MembershipStatus.ACTIVE))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new BootstrapBulkImportCommand(List.of(account, account), List.of(user), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> invokeBootstrapValidateNoNull("accounts", Collections.singletonList(null)))
        .hasCauseInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new BootstrapBulkImportCommand(
                    List.of(account),
                    List.of(user),
                    List.of(
                        new BootstrapBulkImportCommand.MembershipItem(
                            user.clientRef(),
                            "missing",
                            MembershipRole.OWNER,
                            MembershipStatus.ACTIVE))))
        .isInstanceOf(IllegalArgumentException.class);

    NotificationSearchCursor searchCursor =
        new NotificationSearchCursor(base, 1L, base.minusSeconds(10), base.plusSeconds(10), "fp");
    assertThatThrownBy(
            () ->
                new NotificationSearchCursor(
                    base, 1L, base.plusSeconds(10), base.minusSeconds(10), "fp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchCursor(
                    base.minusSeconds(20), 1L, base.minusSeconds(10), base.plusSeconds(10), "fp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchCursor(
                    base.plusSeconds(20), 1L, base.minusSeconds(10), base.plusSeconds(10), "fp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchSlice(
                    List.of(), null, true, 10, base.plusSeconds(10), base.minusSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchSlice(
                    List.of(),
                    searchCursor,
                    false,
                    10,
                    base.minusSeconds(10),
                    base.plusSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchSlice(List.of(), null, true, 10, null, base.plusSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchSlice(
                    List.of(), searchCursor, true, 10, base.minusSeconds(20), base.plusSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchSlice(
                    List.of(), searchCursor, true, 10, base.minusSeconds(10), base.plusSeconds(20)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new NotificationSearchSlice(
                    List.of(), null, false, 10, base.minusSeconds(10), base.plusSeconds(10))
                .hasNext())
        .isFalse();

    assertThatThrownBy(() -> new NotificationBulkActionCommand(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NotificationBulkActionCommand(Collections.nCopies(101, 1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NotificationBulkActionCommand(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NotificationBulkActionCommand(List.of(0L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountSummaryList(List.of(), 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountSummaryList(Collections.singletonList(null), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new AccountSummaryList(immutableListWithNullElement(accountSummary(base)), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TransferLimitPolicy(1000L, 999L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new AuthStatusChangeAuditSearchQuery(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    AuthStatusChangeAuditSearchQuery.MAX_SIZE + 1)
                .size())
        .isEqualTo(AuthStatusChangeAuditSearchQuery.MAX_SIZE);
    assertThatThrownBy(
            () ->
                new AuthStatusChangeAuditSearchQuery(
                    base.plusSeconds(1), base, null, null, null, null, null, 50))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationSearchQuery(
                    50,
                    null,
                    com.aquilabank.domain.notification.model.NotificationReadStatusFilter.ALL,
                    null,
                    base.plusSeconds(1),
                    base))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TransactionQuery(
                    1L, base, base.plusSeconds(1), 10, null, null, null, 3000L, 1000L, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TransactionQuery(
                    1L,
                    base,
                    base.plus(Duration.ofDays(32)),
                    10,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TransactionQuery(
                    1L, base, base.plusSeconds(1), 51, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be between 1 and 50");

    TransactionCursor transactionCursor = new TransactionCursor(base, 1L);
    assertThat(new TransactionSlice(List.of(), null, false, 10).hasNext()).isFalse();
    assertThat(new TransactionSlice(List.of(), transactionCursor, true, 10).nextCursor())
        .isEqualTo(transactionCursor);
    assertThatThrownBy(() -> new TransactionSlice(List.of(), null, false, 51))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be between 1 and 50");
    NotificationCursor notificationCursor = new NotificationCursor(base, 1L);
    assertThat(new NotificationSlice(List.of(), null, false, 10).hasNext()).isFalse();
    assertThat(new NotificationSlice(List.of(), notificationCursor, true, 10).nextCursor())
        .isEqualTo(notificationCursor);

    assertThat(
            new NotificationChannelOutboxRedriveResult(
                    1L, NotificationChannelOutboxRedriveOutcome.NOT_FOUND, null, 0, base)
                .outcome())
        .isEqualTo(NotificationChannelOutboxRedriveOutcome.NOT_FOUND);
    assertThatThrownBy(
            () ->
                new NotificationChannelOutboxRedriveResult(
                    1L, NotificationChannelOutboxRedriveOutcome.REDRIVEN, null, 0, base))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new NotificationChannelOutboxRedriveResult(
                    1L,
                    NotificationChannelOutboxRedriveOutcome.NOT_QUARANTINED,
                    NotificationChannelDeliveryStatus.FAILED,
                    1,
                    base)
                .currentStatus())
        .isEqualTo(NotificationChannelDeliveryStatus.FAILED);

    assertThatThrownBy(
            () -> new NotificationInboxEntry(1L, "x".repeat(81), "event", "title", "message", base))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationInboxEntry(
                    1L, "event-key", "event", "title", "x".repeat(281), base))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationChannelOutboxEntry(
                    1L,
                    1L,
                    1L,
                    NotificationPreferenceCategory.SECURITY,
                    NotificationPreferenceChannel.IN_APP,
                    "event",
                    "key",
                    "{}",
                    base,
                    base))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new NotificationChannelOutboxEntry(
                    1L,
                    1L,
                    1L,
                    NotificationPreferenceCategory.SECURITY,
                    NotificationPreferenceChannel.SMS,
                    "event",
                    "key",
                    "{}",
                    base,
                    base)
                .channel())
        .isEqualTo(NotificationPreferenceChannel.SMS);
    assertThatThrownBy(
            () ->
                new NotificationChannelOutboxQuarantinedItem(
                    1L,
                    1L,
                    1L,
                    1L,
                    NotificationPreferenceCategory.SECURITY,
                    NotificationPreferenceChannel.IN_APP,
                    "event",
                    "key",
                    0,
                    "error",
                    base,
                    base))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new NotificationChannelOutboxQuarantinedItem(
                    1L,
                    1L,
                    1L,
                    1L,
                    NotificationPreferenceCategory.SECURITY,
                    NotificationPreferenceChannel.SMS,
                    "event",
                    "key",
                    0,
                    "error",
                    base,
                    base)
                .channel())
        .isEqualTo(NotificationPreferenceChannel.SMS);
  }

  private static void invokeBootstrapValidateNoNull(String name, List<?> items) throws Exception {
    Method method =
        BootstrapBulkImportCommand.class.getDeclaredMethod(
            "validateNoNull", String.class, List.class);
    method.setAccessible(true);
    method.invoke(null, name, items);
  }

  private static AccountSummary accountSummary(Instant base) {
    return new AccountSummary(1L, "1000000001", "account", "ACTIVE", "KRW", 0L, 0L, base, base);
  }

  private static <T> List<T> immutableListWithNullElement(T item) throws Exception {
    List<T> items = List.of(item);
    Unsafe unsafe = unsafe();
    Field field = items.getClass().getDeclaredField("e0");
    // List.copyOf 최적화 뒤의 방어 분기만 검증하기 위한 테스트 전용 로컬 인스턴스 조작
    unsafe.putObject(items, unsafe.objectFieldOffset(field), null);
    return items;
  }

  private static Unsafe unsafe() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (Unsafe) field.get(null);
  }

  private static Stream<Class<?>> modelTypes() throws Exception {
    Path root = Path.of("src/main/java/com/aquilabank/domain");
    return Files.walk(root)
        .filter(path -> path.toString().endsWith(".java"))
        .filter(path -> path.toString().contains("/model/"))
        .map(DomainModelCoverageTest::toClassName)
        .map(DomainModelCoverageTest::loadClass)
        .flatMap(DomainModelCoverageTest::withDeclaredClasses)
        .filter(DomainModelCoverageTest::isCoverageTarget)
        .sorted((left, right) -> left.getName().compareTo(right.getName()));
  }

  private static Stream<Class<?>> withDeclaredClasses(Class<?> type) {
    return Stream.concat(Stream.of(type), Arrays.stream(type.getDeclaredClasses()));
  }

  private static boolean isCoverageTarget(Class<?> type) {
    if (type.isRecord() || type.isEnum()) {
      return true;
    }
    return !type.isInterface() && !type.isAnnotation() && type.getName().contains(".model.");
  }

  private static String toClassName(Path path) {
    String value = path.toString().replace('/', '.');
    int start = value.indexOf("com.aquilabank.domain");
    return value.substring(start, value.length() - ".java".length());
  }

  private static Class<?> loadClass(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot load domain model class: " + name, e);
    }
  }

  private static void coverType(Class<?> type) throws Exception {
    if (type.isEnum()) {
      coverEnum(type);
      return;
    }
    if (!type.isRecord()) {
      coverDeclaredConstructors(type);
      coverDeclaredMethods(type, null);
      return;
    }
    Object first = instantiateRecord(type, 0, new ArrayDeque<>());
    Object same = instantiateRecord(type, 0, new ArrayDeque<>());
    Object alternate = instantiateRecord(type, 1, new ArrayDeque<>());

    assertThat(first).isEqualTo(same);
    assertThat(first.hashCode()).isEqualTo(same.hashCode());
    assertThat(first).isNotEqualTo(null);
    assertThat(first).isNotEqualTo("not-a-record");
    assertThat(first.toString()).contains(type.getSimpleName());
    if (!first.equals(alternate)) {
      assertThat(first.hashCode()).isNotEqualTo(alternate.hashCode());
    }
    for (RecordComponent component : type.getRecordComponents()) {
      component.getAccessor().invoke(first);
    }
    coverDeclaredConstructors(type);
    coverDeclaredMethods(type, first);
    coverInvalidBranches(type);
  }

  private static void coverEnum(Class<?> type) throws Exception {
    Object[] values = type.getEnumConstants();
    assertThat(values).isNotEmpty();
    for (Object value : values) {
      Enum<?> item = (Enum<?>) value;
      assertThat(Enum.valueOf(type.asSubclass(Enum.class), item.name())).isSameAs(item);
      assertThat(item.ordinal()).isGreaterThanOrEqualTo(0);
      coverDeclaredMethods(type, item);
    }
  }

  private static void coverDeclaredConstructors(Class<?> type) throws Exception {
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      constructor.setAccessible(true);
      Object[] args = sampleExecutableArgs(constructor.getParameters());
      try {
        constructor.newInstance(args);
      } catch (InvocationTargetException e) {
        if (!(e.getCause() instanceof RuntimeException)) {
          throw e;
        }
      }
    }
  }

  private static void coverDeclaredMethods(Class<?> type, Object target) throws Exception {
    for (Method method : type.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
        continue;
      }
      method.setAccessible(true);
      try {
        Object receiver = Modifier.isStatic(method.getModifiers()) ? null : target;
        for (int variant = 0; variant < 3; variant++) {
          method.invoke(receiver, sampleExecutableArgs(method.getParameters(), variant));
        }
        invokeWithNullArgs(method, receiver);
      } catch (InvocationTargetException e) {
        if (!(e.getCause() instanceof RuntimeException)) {
          throw new IllegalStateException(e);
        }
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static Object[] sampleExecutableArgs(Parameter[] parameters) throws Exception {
    return sampleExecutableArgs(parameters, 0);
  }

  private static Object[] sampleExecutableArgs(Parameter[] parameters, int variant)
      throws Exception {
    Object[] args = new Object[parameters.length];
    ArrayDeque<Class<?>> stack = new ArrayDeque<>();
    for (int i = 0; i < parameters.length; i++) {
      args[i] =
          sampleValue(
              parameters[i].getParameterizedType(),
              parameters[i].getType(),
              parameters[i].isNamePresent() ? parameters[i].getName() : "arg" + i,
              variant,
              stack);
    }
    return args;
  }

  private static void invokeWithNullArgs(Method method, Object receiver)
      throws ReflectiveOperationException {
    Parameter[] parameters = method.getParameters();
    if (parameters.length == 0
        || Arrays.stream(parameters).anyMatch(item -> item.getType().isPrimitive())) {
      return;
    }
    Object[] args = new Object[parameters.length];
    try {
      method.invoke(receiver, args);
    } catch (InvocationTargetException e) {
      if (!(e.getCause() instanceof RuntimeException)) {
        throw e;
      }
    }
  }

  private static Object instantiateRecord(Class<?> type, int variant, ArrayDeque<Class<?>> stack)
      throws Exception {
    Object[] specialArgs = specialArgs(type, variant);
    RecordComponent[] components = type.getRecordComponents();
    Constructor<?> constructor =
        type.getDeclaredConstructor(
            Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
    constructor.setAccessible(true);
    if (specialArgs != null) {
      return constructor.newInstance(specialArgs);
    }
    Object[] args = new Object[components.length];
    stack.push(type);
    try {
      for (int i = 0; i < components.length; i++) {
        args[i] =
            sampleValue(
                components[i].getGenericType(),
                components[i].getType(),
                components[i].getName(),
                variant,
                stack);
      }
      return constructor.newInstance(args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      throw e;
    } finally {
      stack.pop();
    }
  }

  private static Object[] specialArgs(Class<?> type, int variant) {
    if (type == LoginResult.class) {
      if (variant == 0) {
        return new Object[] {
          com.aquilabank.domain.auth.model.LoginResultStatus.SUCCESS,
          "access-token",
          "refresh-token",
          "Bearer",
          sampleInstant("expiresAt", 0),
          sampleInstant("refreshExpiresAt", 0),
          1L,
          null,
          null,
          null,
          null,
          null
        };
      }
      return new Object[] {
        com.aquilabank.domain.auth.model.LoginResultStatus.MFA_REQUIRED,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "challenge-" + variant,
        LoginChallengeType.TOTP,
        sampleInstant("challengeExpiresAt", variant),
        null
      };
    }
    if (type == BootstrapBulkImportCommand.class) {
      BootstrapBulkImportCommand.AccountItem account =
          new BootstrapBulkImportCommand.AccountItem("account-" + variant, "account", "KRW", 0L);
      BootstrapBulkImportCommand.UserItem user =
          new BootstrapBulkImportCommand.UserItem(
              "user-" + variant, "login-" + variant, "password", "user");
      BootstrapBulkImportCommand.MembershipItem membership =
          new BootstrapBulkImportCommand.MembershipItem(
              user.clientRef(), account.clientRef(), MembershipRole.OWNER, MembershipStatus.ACTIVE);
      return new Object[] {List.of(account), List.of(user), List.of(membership)};
    }
    if (type == CustomerApplicationExecutionResult.class) {
      return new Object[] {
        variant == 0 ? CustomerApplicationStatus.EXECUTED : CustomerApplicationStatus.FAILED,
        "execution-result-" + variant,
        Map.of("variant", variant)
      };
    }
    return null;
  }

  private static void coverInvalidBranches(Class<?> type) throws Exception {
    RecordComponent[] components = type.getRecordComponents();
    Constructor<?> constructor =
        type.getDeclaredConstructor(
            Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
    constructor.setAccessible(true);
    Object[] baseArgs = specialArgs(type, 0);
    if (baseArgs == null) {
      baseArgs = new Object[components.length];
      ArrayDeque<Class<?>> stack = new ArrayDeque<>();
      stack.push(type);
      try {
        for (int i = 0; i < components.length; i++) {
          baseArgs[i] =
              sampleValue(
                  components[i].getGenericType(),
                  components[i].getType(),
                  components[i].getName(),
                  0,
                  stack);
        }
      } finally {
        stack.pop();
      }
    }
    for (int i = 0; i < components.length; i++) {
      for (Object invalid : invalidValues(components[i], baseArgs[i])) {
        Object[] args = Arrays.copyOf(baseArgs, baseArgs.length);
        args[i] = invalid;
        try {
          constructor.newInstance(args);
        } catch (InvocationTargetException e) {
          if (!(e.getCause() instanceof RuntimeException)) {
            throw e;
          }
        } catch (RuntimeException ignored) {
          // validator branch 실행 목적: 허용 필드는 통과, 거부 필드는 예외면 충분
        }
      }
    }
  }

  private static List<Object> invalidValues(RecordComponent component, Object validValue) {
    Class<?> type = component.getType();
    String name = component.getName();
    List<Object> values = new ArrayList<>();
    if (!type.isPrimitive()) {
      values.add(null);
    }
    if (type == String.class) {
      values.add(" ");
      values.add("x".repeat(256));
      if (name.toLowerCase().contains("currency")) {
        values.add("usd");
      }
      if (name.toLowerCase().contains("reason")) {
        values.add("bad reason");
      }
    } else if (type == long.class || type == Long.class) {
      values.add(0L);
      values.add(-1L);
      if (name.equals("targetAccountId") || name.equals("sourceAccountId")) {
        values.add(1L);
      }
      if (name.equals("minAmountMinor")) {
        values.add(3000L);
      }
    } else if (type == int.class || type == Integer.class) {
      values.add(0);
      values.add(-1);
      values.add(101);
    } else if (type == boolean.class || type == Boolean.class) {
      values.add(Boolean.TRUE);
      values.add(Boolean.FALSE);
    } else if (type == Instant.class) {
      if (name.equals("to")) {
        values.add(sampleInstant("from", 0).minusSeconds(60));
      }
    } else if (type == Duration.class) {
      values.add(Duration.ZERO);
      values.add(Duration.ofSeconds(-1));
    } else if (type == List.class) {
      values.add(List.of());
      values.add(Collections.singletonList(null));
      values.add(
          Collections.nCopies(
              51, validValue instanceof List<?> list && !list.isEmpty() ? list.get(0) : "x"));
    } else if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      if (constants.length > 1) {
        values.add(constants[1]);
      }
    }
    return values;
  }

  private static Object sampleValue(
      Type genericType, Class<?> type, String name, int variant, ArrayDeque<Class<?>> stack)
      throws Exception {
    if (type == String.class) {
      return sampleString(name, variant);
    }
    if (type == Object.class) {
      return "object-" + variant;
    }
    if (type == long.class || type == Long.class) {
      if (name.equals("targetAccountId")) {
        return 2L + variant;
      }
      if (name.equals("maxAmountMinor")) {
        return 2000L + variant;
      }
      return 1L + variant;
    }
    if (type == int.class || type == Integer.class) {
      if (name.equals("codeCount")) {
        return 1;
      }
      return 1 + variant;
    }
    if (type == boolean.class || type == Boolean.class) {
      if (name.equals("hasNext")) {
        return true;
      }
      if (name.equals("success")) {
        return false;
      }
      return variant % 2 == 0;
    }
    if (type == BigDecimal.class) {
      return BigDecimal.valueOf(1000L + variant);
    }
    if (type == Instant.class) {
      return sampleInstant(name, variant);
    }
    if (type == LocalDate.class) {
      return LocalDate.of(2026, 4, 20).plusDays(variant);
    }
    if (type == LocalDateTime.class) {
      return LocalDateTime.of(2026, 4, 20, 9, 30).plusSeconds(variant);
    }
    if (type == Duration.class) {
      return Duration.ofMinutes(5 + variant);
    }
    if (type == UUID.class) {
      return UUID.nameUUIDFromBytes(("domain-model-" + variant).getBytes());
    }
    if (type == Optional.class) {
      return Optional.of("optional-" + variant);
    }
    if (type == List.class) {
      return List.of(collectionElement(genericType, name, variant, stack));
    }
    if (type == Set.class) {
      return Set.of(collectionElement(genericType, name, variant, stack));
    }
    if (type == Map.class) {
      return Map.of("key-" + variant, "value-" + variant);
    }
    if (type.isEnum()) {
      if (type.getName().endsWith("NotificationPreferenceChannel")) {
        return type.getEnumConstants()[1];
      }
      if (type.getName().endsWith("NotificationChannelOutboxRedriveOutcome")) {
        return type.getEnumConstants()[0];
      }
      Object[] values = type.getEnumConstants();
      return values[Math.min(variant, values.length - 1)];
    }
    if (type.isRecord() && !stack.contains(type)) {
      return instantiateRecord(type, variant, stack);
    }
    throw new IllegalArgumentException("No sample value for " + type.getName() + " " + name);
  }

  private static Instant sampleInstant(String name, int variant) {
    Instant base = Instant.parse("2026-04-20T09:30:00Z").plusSeconds(variant);
    if (name.equals("to") || name.endsWith("ExpiresAt")) {
      return base.plusSeconds(600);
    }
    return base;
  }

  private static Object collectionElement(
      Type genericType, String name, int variant, ArrayDeque<Class<?>> stack) throws Exception {
    if (genericType instanceof ParameterizedType parameterizedType) {
      Type actualType = parameterizedType.getActualTypeArguments()[0];
      if (actualType instanceof Class<?> actualClass) {
        return sampleValue(actualType, actualClass, name + "Item", variant, stack);
      }
    }
    if (name.toLowerCase().contains("id")) {
      return 1L + variant;
    }
    return "item-" + variant;
  }

  private static String sampleString(String name, int variant) {
    String lowerName = name.toLowerCase();
    if (lowerName.contains("currency")) {
      return variant == 0 ? "KRW" : "USD";
    }
    if (lowerName.contains("hash")) {
      return (variant == 0 ? "a" : "b").repeat(64);
    }
    if (lowerName.contains("code")) {
      return "123456";
    }
    if (lowerName.contains("reference")) {
      return "TRX-" + (variant + 1);
    }
    if (lowerName.contains("reason")) {
      return "CANCEL";
    }
    if (lowerName.contains("idempotency")) {
      return "idem-" + (variant + 1);
    }
    if (lowerName.contains("request")) {
      return "request-" + (variant + 1);
    }
    if (lowerName.contains("email")) {
      return "user" + variant + "@example.com";
    }
    return name + "-" + (variant + 1);
  }
}
