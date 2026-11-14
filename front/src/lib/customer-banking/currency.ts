const MAX_SAFE_KRW = Math.floor(Number.MAX_SAFE_INTEGER / 100);
const MAX_KRW_DIGIT_LENGTH = 13;
const MAX_UI_KRW = 9_999_999_999_999;
const MAX_KRW_INPUT_CHARS = 64;

const KRW_PLAIN_FORMATTER = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 0,
});

const KOREAN_UNITS = ["", "만", "억", "조"] as const;

/**
 * 엄격한 원화 정수 문자열 파서: 허용 문자(숫자·콤마·공백·말미 '원') 외 입력이나
 * 안전 정수·예산 길이를 초과하는 입력은 절삭하지 않고 즉시 fail-closed로 거부한다.
 */
function parseStrictKrwNumber(raw: string): number | undefined {
  if (raw.length > MAX_KRW_INPUT_CHARS) {
    throw new Error("금액 허용 범위를 초과했습니다.");
  }
  let end = raw.length;
  while (end > 0 && raw.charCodeAt(end - 1) === 32) {
    end -= 1;
  }
  if (end > 0 && raw.charCodeAt(end - 1) === 0xc6d0) {
    end -= 1;
  }
  let hasDigit = false;
  let krw = 0;
  for (let i = 0; i < end; i += 1) {
    const code = raw.charCodeAt(i);
    if (code >= 48 && code <= 57) {
      hasDigit = true;
      krw = krw * 10 + (code - 48);
      if (krw > MAX_SAFE_KRW || !Number.isSafeInteger(krw)) {
        throw new Error("금액 허용 범위를 초과했습니다.");
      }
      continue;
    }
    if (code === 44 || code === 32) {
      continue;
    }
    throw new Error("유효한 원화 금액을 입력하세요.");
  }
  return hasDigit ? krw : undefined;
}

/**
 * 입력 문자열에서 0-9 숫자만 단일 패스로 추출하고 불필요한 선행 0을 제거한다.
 */
export function parseKrwDigits(raw: string): string {
  let digits = "";
  for (let i = 0; i < raw.length; i += 1) {
    const code = raw.charCodeAt(i);
    if (code >= 48 && code <= 57) {
      digits += raw[i];
      if (digits.length >= MAX_KRW_DIGIT_LENGTH) {
        break;
      }
    }
  }
  if (!digits) {
    return "";
  }
  let firstNonZero = 0;
  while (firstNonZero < digits.length && digits.charCodeAt(firstNonZero) === 48) {
    firstNonZero += 1;
  }
  if (firstNonZero === digits.length) {
    return "0";
  }
  return firstNonZero > 0 ? digits.slice(firstNonZero) : digits;
}

/**
 * 원화 정수 문자열을 천 단위 콤마가 포함된 표시 문자열로 변환한다.
 */
export function formatKrwDisplay(raw: string): string {
  const digits = parseKrwDigits(raw);
  if (!digits) {
    return "";
  }
  const numeric = Number(digits);
  if (!Number.isSafeInteger(numeric)) {
    return "";
  }
  return KRW_PLAIN_FORMATTER.format(numeric);
}

/**
 * 원화 정수 입력을 안전 정수 범위 내의 minor 단위(원 * 100)로 변환한다.
 * 유효하지 않거나 안전한 정수 범위를 넘으면 절삭 없이 즉시 예외를 던진다.
 */
export function krwToMinor(raw: string): number {
  const krw = parseStrictKrwNumber(raw);
  if (krw === undefined) {
    throw new Error("금액을 입력하세요.");
  }
  if (krw <= 0) {
    throw new Error("유효한 원화 금액을 입력하세요.");
  }
  const minor = krw * 100;
  if (!Number.isSafeInteger(minor)) {
    throw new Error("금액 허용 범위를 초과했습니다.");
  }
  return minor;
}

/**
 * 미리보기·실시간 검증처럼 빈 값을 허용하는 경로에서 원화 입력을 minor 단위로 변환한다.
 */
export function tryKrwToMinor(raw: string): number | undefined {
  try {
    const krw = parseStrictKrwNumber(raw);
    if (krw === undefined) {
      return undefined;
    }
    const minor = krw * 100;
    return Number.isSafeInteger(minor) ? minor : undefined;
  } catch {
    return undefined;
  }
}

/**
 * 선택적 필터(최소/최대 금액 등)용 원화 입력을 minor 단위로 변환한다.
 * 공백이면 undefined를 반환하고, 잘못된 문자·음수·범위 초과는 fail-closed로 거부한다.
 */
export function toOptionalMinorFromKrw(raw: string): number | undefined {
  const krw = parseStrictKrwNumber(raw);
  if (krw === undefined) {
    return undefined;
  }
  const minor = krw * 100;
  if (!Number.isSafeInteger(minor)) {
    throw new Error("금액 허용 범위를 초과했습니다.");
  }
  return minor;
}

/**
 * minor 단위를 화면 입력용 원화 정수 문자열로 복원한다.
 */
export function minorToKrwString(minor: number | null | undefined): string {
  if (typeof minor !== "number" || !Number.isFinite(minor) || minor <= 0) {
    return "";
  }
  return String(Math.round(minor / 100));
}

/**
 * 원화 정수 입력에 정액 버튼을 합산한다.
 */
export function addKrwAmount(currentRaw: string, deltaKrw: number): string {
  const currentDigits = parseKrwDigits(currentRaw);
  const base = currentDigits ? Number(currentDigits) : 0;
  if (!Number.isSafeInteger(base) || !Number.isSafeInteger(deltaKrw)) {
    return currentDigits;
  }
  const next = base + deltaKrw;
  if (!Number.isSafeInteger(next) || next < 0 || next > MAX_UI_KRW) {
    return String(MAX_UI_KRW);
  }
  return String(next);
}

/**
 * 원화 정수 문자열을 한국어 단위 표기(예: 1만 2,000원)로 변환한다.
 */
export function formatKoreanCurrencyUnits(raw: string): string {
  const digits = parseKrwDigits(raw);
  if (!digits) {
    return "";
  }
  let remaining = Number(digits);
  if (!Number.isSafeInteger(remaining) || remaining < 0) {
    return "";
  }
  if (remaining === 0) {
    return "0원";
  }
  const parts: string[] = [];
  let unitIndex = 0;
  while (remaining > 0 && unitIndex < KOREAN_UNITS.length) {
    const chunk = remaining % 10_000;
    if (chunk > 0) {
      const chunkText = KRW_PLAIN_FORMATTER.format(chunk);
      const suffix = KOREAN_UNITS[unitIndex];
      parts.push(suffix ? `${chunkText}${suffix}` : chunkText);
    }
    remaining = Math.floor(remaining / 10_000);
    unitIndex += 1;
  }
  return `${parts.reverse().join(" ")}원`;
}

/**
 * 커서 앞쪽에 놓인 숫자 개수를 계산해 콤마 포맷팅 후에도 입력 위치를 보존한다.
 */
export function countDigitsBeforeCaret(text: string, caretPos: number): number {
  const limit = Math.max(0, Math.min(caretPos, text.length));
  let count = 0;
  for (let i = 0; i < limit; i += 1) {
    const code = text.charCodeAt(i);
    if (code >= 48 && code <= 57) {
      count += 1;
    }
  }
  return count;
}

/**
 * 지정된 숫자 개수 직후의 포맷팅 문자열 인덱스를 찾는다.
 */
export function findCaretForDigitCount(formattedText: string, digitCount: number): number {
  if (digitCount <= 0) {
    return 0;
  }
  let seen = 0;
  for (let i = 0; i < formattedText.length; i += 1) {
    const code = formattedText.charCodeAt(i);
    if (code >= 48 && code <= 57) {
      seen += 1;
      if (seen === digitCount) {
        return i + 1;
      }
    }
  }
  return formattedText.length;
}
