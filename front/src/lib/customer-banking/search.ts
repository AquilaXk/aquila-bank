import {
  enterpriseServiceItems,
  favoriteServiceItems,
  mainMenus,
  quickMenus,
  recentMenus,
  securityHubItems,
  serviceMapGroups,
  supportCenterItems,
} from "./constants";
import type { MenuSection, ServiceSearchItem } from "./types";

const publicSearchSections = new Set<MenuSection>(["dashboard", "security"]);

// 한글 음절(AC00~D7A3) 초성 19자 테이블
const HANGUL_CHOSEONG = [
  "ㄱ",
  "ㄲ",
  "ㄴ",
  "ㄷ",
  "ㄸ",
  "ㄹ",
  "ㅁ",
  "ㅂ",
  "ㅃ",
  "ㅅ",
  "ㅆ",
  "ㅇ",
  "ㅈ",
  "ㅉ",
  "ㅊ",
  "ㅋ",
  "ㅌ",
  "ㅍ",
  "ㅎ",
] as const;

// 호환 자모(3131~314E) 중 초성으로 쓰이는 19자 판별 비트마스크 (30비트 내 수용)
const CHOSEONG_COMPAT_OFFSET_BASE = 0x3131;
const CHOSEONG_COMPAT_MASK =
  (1 << 0) | // ㄱ
  (1 << 1) | // ㄲ
  (1 << 3) | // ㄴ
  (1 << 6) | // ㄷ
  (1 << 7) | // ㄸ
  (1 << 8) | // ㄹ
  (1 << 16) | // ㅁ
  (1 << 17) | // ㅂ
  (1 << 18) | // ㅃ
  (1 << 20) | // ㅅ
  (1 << 21) | // ㅆ
  (1 << 22) | // ㅇ
  (1 << 23) | // ㅈ
  (1 << 24) | // ㅉ
  (1 << 25) | // ㅊ
  (1 << 26) | // ㅋ
  (1 << 27) | // ㅌ
  (1 << 28) | // ㅍ
  (1 << 29); // ㅎ

function isChoseongCharCode(code: number): boolean {
  const offset = code - CHOSEONG_COMPAT_OFFSET_BASE;
  return offset >= 0 && offset <= 29 && ((CHOSEONG_COMPAT_MASK >>> offset) & 1) === 1;
}

/**
 * 공백 제거 및 ASCII 소문자 정규화를 단일 루프로 수행한다.
 */
function normalizeSearchText(value: string): string {
  let out = "";
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    if (code <= 32) {
      continue;
    }
    if (code >= 65 && code <= 90) {
      out += String.fromCharCode(code + 32);
    } else {
      out += value[i];
    }
  }
  return out;
}

/**
 * 음절 문자열에서 초성 열을 추출한다(공백 제외).
 */
function extractChoseong(value: string): string {
  let out = "";
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    if (code <= 32) {
      continue;
    }
    if (code >= 0xac00 && code <= 0xd7a3) {
      const idx = Math.floor((code - 0xac00) / 588);
      out += HANGUL_CHOSEONG[idx];
    } else if (isChoseongCharCode(code)) {
      out += value[i];
    } else if (code >= 65 && code <= 90) {
      out += String.fromCharCode(code + 32);
    } else {
      out += value[i];
    }
  }
  return out;
}

function splitNormalizedTokens(value: string): string[] {
  const tokens: string[] = [];
  let current = "";
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    // 공백, '/', '·', '-', ',', '(', ')' 등을 단어 경계로 처리
    if (
      code <= 32 ||
      code === 47 ||
      code === 0x00b7 ||
      code === 45 ||
      code === 44 ||
      code === 40 ||
      code === 41
    ) {
      if (current) {
        tokens.push(current);
        current = "";
      }
      continue;
    }
    current += code >= 65 && code <= 90 ? String.fromCharCode(code + 32) : value[i];
  }
  if (current) {
    tokens.push(current);
  }
  return tokens;
}

/**
 * 문자열 내 모든 문자를 64비트 [lo32, hi32] 존재 비트마스크에 누적한다.
 */
function addCharsToMask(mask: [number, number], text: string): void {
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i);
    const bit = ((code * 2654435761) >>> 26) & 63;
    if (bit < 32) {
      mask[0] |= 1 << bit;
    } else {
      mask[1] |= 1 << (bit - 32);
    }
  }
}

function createSearchItem(
  label: string,
  section: MenuSection,
  group: string,
  description: string,
  keywords: string[] = [],
): ServiceSearchItem {
  return {
    id: `${section}:${normalizeSearchText(label)}`,
    label,
    group,
    section,
    description,
    keywords: [label, group, description, ...keywords],
    requiresSession: !publicSearchSections.has(section),
  };
}

function uniqueItems(items: ServiceSearchItem[]) {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.id)) {
      return false;
    }
    seen.add(item.id);
    return true;
  });
}

export const serviceSearchItems = uniqueItems([
  ...mainMenus.map((item) =>
    createSearchItem(item.label, item.id, item.group, `${item.group} 업무`),
  ),
  ...quickMenus.map((item) =>
    createSearchItem(item.label, item.section, "빠른업무", "자주 쓰는 개인뱅킹 업무"),
  ),
  ...recentMenus.map((item) =>
    createSearchItem(item.label, item.section, "최근업무", "최근 이용 업무"),
  ),
  ...serviceMapGroups.flatMap((group) =>
    group.items.map((item) =>
      createSearchItem(item.label, item.section, group.title, `${group.title} 메뉴`),
    ),
  ),
  ...favoriteServiceItems.map((item) =>
    createSearchItem(item.label, item.section, item.group, "자주찾는서비스"),
  ),
  ...enterpriseServiceItems.map((item) =>
    createSearchItem(item.title, "enterpriseServices", item.category, item.description, [
      item.status,
    ]),
  ),
  ...supportCenterItems.map((item) =>
    createSearchItem(item.title, "supportCenter", "고객센터", item.description, [
      item.action,
    ]),
  ),
  ...securityHubItems.map((item) =>
    createSearchItem(item.title, "securityHub", "인증/보안", item.description, [
      item.status,
    ]),
  ),
]);

interface IndexedSearchEntry {
  item: ServiceSearchItem;
  maskLo: number;
  maskHi: number;
  label: string;
  labelWords: string[];
  labelChoseong: string;
  group: string;
  groupChoseong: string;
  description: string;
  descriptionChoseong: string;
  keywords: string[];
  keywordChoseongs: string[];
}

// 모듈 적재 시 ko-KR 사전 순 정렬과 정규화/초성/64비트 비트마스크를 미리 색인한다.
const PREINDEXED_ENTRIES: IndexedSearchEntry[] = [...serviceSearchItems]
  .sort((left, right) => left.label.localeCompare(right.label, "ko-KR"))
  .map((item) => {
    const label = normalizeSearchText(item.label);
    const labelChoseong = extractChoseong(item.label);
    const group = normalizeSearchText(item.group);
    const groupChoseong = extractChoseong(item.group);
    const description = normalizeSearchText(item.description);
    const descriptionChoseong = extractChoseong(item.description);
    const keywords = item.keywords.map(normalizeSearchText);
    const keywordChoseongs = item.keywords.map(extractChoseong);

    const mask: [number, number] = [0, 0];
    addCharsToMask(mask, label);
    addCharsToMask(mask, labelChoseong);
    addCharsToMask(mask, group);
    addCharsToMask(mask, groupChoseong);
    addCharsToMask(mask, description);
    addCharsToMask(mask, descriptionChoseong);
    for (let i = 0; i < keywords.length; i += 1) {
      addCharsToMask(mask, keywords[i]);
      addCharsToMask(mask, keywordChoseongs[i]);
    }

    return {
      item,
      maskLo: mask[0],
      maskHi: mask[1],
      label,
      labelWords: splitNormalizedTokens(item.label),
      labelChoseong,
      group,
      groupChoseong,
      description,
      descriptionChoseong,
      keywords,
      keywordChoseongs,
    };
  });

const BUCKET_COUNT = 7;

function scoreBucket(entry: IndexedSearchEntry, q: string, qIsChoseong: boolean): number {
  if (entry.label === q) {
    return 0;
  }
  if (qIsChoseong && entry.labelChoseong === q) {
    return 1;
  }
  if (entry.label.startsWith(q)) {
    return 1;
  }
  if (qIsChoseong && entry.labelChoseong.startsWith(q)) {
    return 2;
  }
  for (let i = 0; i < entry.labelWords.length; i += 1) {
    if (entry.labelWords[i].startsWith(q)) {
      return 2;
    }
  }
  if (entry.label.includes(q) || (qIsChoseong && entry.labelChoseong.includes(q))) {
    return 3;
  }
  if (entry.group.includes(q) || (qIsChoseong && entry.groupChoseong.includes(q))) {
    return 4;
  }
  for (let i = 0; i < entry.keywords.length; i += 1) {
    if (
      entry.keywords[i].includes(q) ||
      (qIsChoseong && entry.keywordChoseongs[i].includes(q))
    ) {
      return 5;
    }
  }
  if (
    entry.description.includes(q) ||
    (qIsChoseong && entry.descriptionChoseong.includes(q))
  ) {
    return 6;
  }
  return -1;
}

export function searchCustomerBankingServices(query: string, limit = 8): ServiceSearchItem[] {
  if (limit <= 0) {
    return [];
  }
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) {
    return [];
  }

  let qIsChoseong = true;
  let qLo = 0;
  let qHi = 0;
  for (let i = 0; i < normalizedQuery.length; i += 1) {
    const code = normalizedQuery.charCodeAt(i);
    if (!isChoseongCharCode(code)) {
      qIsChoseong = false;
    }
    const bit = ((code * 2654435761) >>> 26) & 63;
    if (bit < 32) {
      qLo |= 1 << bit;
    } else {
      qHi |= 1 << (bit - 32);
    }
  }

  const buckets: ServiceSearchItem[][] = [[], [], [], [], [], [], []];

  for (let i = 0; i < PREINDEXED_ENTRIES.length; i += 1) {
    const entry = PREINDEXED_ENTRIES[i];
    if ((entry.maskLo & qLo) !== qLo || (entry.maskHi & qHi) !== qHi) {
      continue;
    }
    const bucket = scoreBucket(entry, normalizedQuery, qIsChoseong);
    if (bucket >= 0 && buckets[bucket].length < limit) {
      buckets[bucket].push(entry.item);
    }
  }

  const results: ServiceSearchItem[] = [];
  for (let b = 0; b < BUCKET_COUNT && results.length < limit; b += 1) {
    const group = buckets[b];
    for (let j = 0; j < group.length && results.length < limit; j += 1) {
      results.push(group[j]);
    }
  }
  return results;
}
