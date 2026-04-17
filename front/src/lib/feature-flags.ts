export const featureFlagKeys = {
  opsConsolePreview: "ops-console-preview",
} as const;

// build 시점 env를 한 곳에서 해석해 미완성 기능의 기본 비노출을 강제합니다.
export function getEnabledFeatureFlags(): string[] {
  const featureFlags = process.env.NEXT_PUBLIC_FEATURE_FLAGS ?? "";

  return featureFlags
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export function isFeatureFlagEnabled(flag: string): boolean {
  return getEnabledFeatureFlags().includes(flag);
}
