/**
 * StorePoint v2 Material 3 Expressive (matraic/m3e) Design System Tokens & Theme Engine.
 * Provides consistent M3E CSS custom properties, expressive motion springs, and dynamic color palettes
 * across apps/erp-web (Back-Office) and apps/pos-web (Offline-First POS Terminal).
 */

export interface M3ExpressivePalette {
  readonly name: string;
  readonly primary: string;
  readonly onPrimary: string;
  readonly primaryContainer: string;
  readonly onPrimaryContainer: string;
  readonly secondary: string;
  readonly onSecondary: string;
  readonly secondaryContainer: string;
  readonly onSecondaryContainer: string;
  readonly tertiary: string;
  readonly onTertiary: string;
  readonly surface: string;
  readonly surfaceContainer: string;
  readonly surfaceContainerHigh: string;
  readonly onSurface: string;
  readonly onSurfaceVariant: string;
  readonly outline: string;
  readonly error: string;
}

export const STOREPOINT_M3E_THEMES: Readonly<Record<"emerald" | "indigo" | "amber", M3ExpressivePalette>> =
  Object.freeze({
    emerald: {
      name: "StorePoint Emerald Expressive",
      primary: "#006C4C",
      onPrimary: "#FFFFFF",
      primaryContainer: "#89F8C7",
      onPrimaryContainer: "#002114",
      secondary: "#4D6357",
      onSecondary: "#FFFFFF",
      secondaryContainer: "#CFE9D9",
      onSecondaryContainer: "#092016",
      tertiary: "#3D6373",
      onTertiary: "#FFFFFF",
      surface: "#F5FBF5",
      surfaceContainer: "#E9EFEA",
      surfaceContainerHigh: "#E3EAE4",
      onSurface: "#171D1A",
      onSurfaceVariant: "#404943",
      outline: "#707973",
      error: "#BA1A1A"
    },
    indigo: {
      name: "StorePoint Indigo Expressive",
      primary: "#4355B9",
      onPrimary: "#FFFFFF",
      primaryContainer: "#DEE0FF",
      onPrimaryContainer: "#00105C",
      secondary: "#5B5D72",
      onSecondary: "#FFFFFF",
      secondaryContainer: "#E0E1F9",
      onSecondaryContainer: "#181A2C",
      tertiary: "#77536D",
      onTertiary: "#FFFFFF",
      surface: "#FBF8FF",
      surfaceContainer: "#EFEDF4",
      surfaceContainerHigh: "#E9E7EF",
      onSurface: "#1B1B21",
      onSurfaceVariant: "#46464F",
      outline: "#767680",
      error: "#BA1A1A"
    },
    amber: {
      name: "StorePoint Warm Retail Expressive",
      primary: "#825500",
      onPrimary: "#FFFFFF",
      primaryContainer: "#FFDDB3",
      onPrimaryContainer: "#291800",
      secondary: "#6F5B40",
      onSecondary: "#FFFFFF",
      secondaryContainer: "#FBDEBC",
      onSecondaryContainer: "#271904",
      tertiary: "#51643F",
      onTertiary: "#FFFFFF",
      surface: "#FFF8F4",
      surfaceContainer: "#F4ECE4",
      surfaceContainerHigh: "#EEE6DE",
      onSurface: "#1F1B16",
      onSurfaceVariant: "#4F4539",
      outline: "#817567",
      error: "#BA1A1A"
    }
  });

/**
 * Generates CSS variables compatible with matraic/m3e (@m3e/core) Web Components.
 */
export function buildM3ExpressiveCssVariables(
  themeKey: keyof typeof STOREPOINT_M3E_THEMES = "emerald"
): string {
  const palette = STOREPOINT_M3E_THEMES[themeKey];
  return [
    `:root {`,
    `  --m3e-color-primary: ${palette.primary};`,
    `  --m3e-color-on-primary: ${palette.onPrimary};`,
    `  --m3e-color-primary-container: ${palette.primaryContainer};`,
    `  --m3e-color-on-primary-container: ${palette.onPrimaryContainer};`,
    `  --m3e-color-secondary: ${palette.secondary};`,
    `  --m3e-color-on-secondary: ${palette.onSecondary};`,
    `  --m3e-color-secondary-container: ${palette.secondaryContainer};`,
    `  --m3e-color-on-secondary-container: ${palette.onSecondaryContainer};`,
    `  --m3e-color-tertiary: ${palette.tertiary};`,
    `  --m3e-color-on-tertiary: ${palette.onTertiary};`,
    `  --m3e-color-surface: ${palette.surface};`,
    `  --m3e-color-surface-container: ${palette.surfaceContainer};`,
    `  --m3e-color-surface-container-high: ${palette.surfaceContainerHigh};`,
    `  --m3e-color-on-surface: ${palette.onSurface};`,
    `  --m3e-color-on-surface-variant: ${palette.onSurfaceVariant};`,
    `  --m3e-color-outline: ${palette.outline};`,
    `  --m3e-color-error: ${palette.error};`,
    `  --m3e-shape-corner-extra-large: 28px;`,
    `  --m3e-shape-corner-large: 16px;`,
    `  --m3e-shape-corner-medium: 12px;`,
    `  --m3e-motion-spring-fast: cubic-bezier(0.22, 1.25, 0.36, 1);`,
    `}`
  ].join("\n");
}
