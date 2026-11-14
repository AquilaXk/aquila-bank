export type ColorScheme = "light" | "dark";

const COLOR_SCHEME_STORAGE_KEY = "color-scheme";

function getSystemDark(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function readStoredScheme(): ColorScheme | null {
  try {
    const raw = window.localStorage.getItem(COLOR_SCHEME_STORAGE_KEY);
    return raw === "light" || raw === "dark" ? raw : null;
  } catch {
    return null;
  }
}

export function applyColorSchemeToDom(stored: ColorScheme | null): ColorScheme {
  const resolved: ColorScheme = stored ?? (getSystemDark() ? "dark" : "light");
  if (typeof document !== "undefined") {
    const meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
    if (stored === "light" || stored === "dark") {
      if (meta) meta.content = stored;
      document.documentElement.dataset.theme = stored;
    } else {
      if (meta) meta.content = "light dark";
      delete document.documentElement.dataset.theme;
    }
  }
  return resolved;
}

export function subscribeColorScheme(onChange: (scheme: ColorScheme) => void): () => void {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return () => undefined;
  }
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  onChange(applyColorSchemeToDom(readStoredScheme()));

  const handleStorage = (event: StorageEvent) => {
    if (event.key === COLOR_SCHEME_STORAGE_KEY) {
      const next = event.newValue === "light" || event.newValue === "dark" ? event.newValue : null;
      onChange(applyColorSchemeToDom(next));
    }
  };

  const handleMediaChange = () => {
    const stored = readStoredScheme();
    if (!stored) {
      onChange(media.matches ? "dark" : "light");
    }
  };

  window.addEventListener("storage", handleStorage);
  media.addEventListener("change", handleMediaChange);
  return () => {
    window.removeEventListener("storage", handleStorage);
    media.removeEventListener("change", handleMediaChange);
  };
}

export function toggleStoredColorScheme(current: ColorScheme): ColorScheme {
  const nextScheme: ColorScheme = current === "dark" ? "light" : "dark";
  const systemDark = getSystemDark();
  const matchesSystem =
    (nextScheme === "dark" && systemDark) || (nextScheme === "light" && !systemDark);

  try {
    if (matchesSystem) {
      window.localStorage.removeItem(COLOR_SCHEME_STORAGE_KEY);
      return applyColorSchemeToDom(null);
    }
    window.localStorage.setItem(COLOR_SCHEME_STORAGE_KEY, nextScheme);
    return applyColorSchemeToDom(nextScheme);
  } catch {
    if (typeof document !== "undefined") {
      const meta = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
      if (meta) meta.content = nextScheme;
      document.documentElement.dataset.theme = nextScheme;
    }
    return nextScheme;
  }
}
