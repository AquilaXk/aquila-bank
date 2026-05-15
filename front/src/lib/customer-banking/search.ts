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

function normalizeSearchText(value: string) {
  return value.toLocaleLowerCase("ko-KR").replace(/\s+/g, "");
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

function scoreSearchItem(item: ServiceSearchItem, query: string) {
  const label = normalizeSearchText(item.label);
  const group = normalizeSearchText(item.group);
  const description = normalizeSearchText(item.description);
  const keywords = item.keywords.map(normalizeSearchText);

  if (label === query) {
    return 100;
  }
  if (label.startsWith(query)) {
    return 90;
  }
  if (label.includes(query)) {
    return 80;
  }
  if (group.includes(query)) {
    return 64;
  }
  if (keywords.some((keyword) => keyword.includes(query))) {
    return 48;
  }
  if (description.includes(query)) {
    return 32;
  }
  return 0;
}

export function searchCustomerBankingServices(query: string, limit = 8) {
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) {
    return [];
  }

  return serviceSearchItems
    .map((item) => ({ item, score: scoreSearchItem(item, normalizedQuery) }))
    .filter((result) => result.score > 0)
    .sort((left, right) => {
      if (right.score !== left.score) {
        return right.score - left.score;
      }
      return left.item.label.localeCompare(right.item.label, "ko-KR");
    })
    .slice(0, limit)
    .map((result) => result.item);
}
