# StorePoint — M3 Expressive Overhaul Notes

Date: 2026-09-21 · Target: Material 3 Expressive (Android 16/17 design language), Compose material3 1.4.0

## 1. Design Token Set (single source of truth)

| Token group | File | Contents |
|---|---|---|
| Shape scale | `ui/theme/Shape.kt` | Classic M3 `Shapes` (8/14/20/28/40dp — larger, softer radii) + polygon scale `ExpressiveShapeKind` (SQUIRCLE, CIRCLE, PILL, COOKIE, CLOVER, HEXAGON, SQUARE) built on `androidx.graphics:graphics-shapes` `RoundedPolygon`, with true spring-animated morphing via `MorphShape` / `rememberMorphShape` |
| Motion | `ui/theme/ExpressiveMotion.kt` | M3E spring roles: `springBouncy` (fast spatial), `springSnappy` (default effects), `springFluid` (slow spatial), `pressSpring` (fast effects) + `rememberPressScale` overshoot settle + `LocalReduceMotion` / `readSystemReduceMotion` (honors system animator-duration-scale = 0) |
| Color | `ui/theme/Color.kt` | Material You dynamic color (API 31+) with expressive indigo fallback palettes; **expanded roles**: surface containers lowest→highest, semantic success/warning/info (+ containers), **primary/secondary/tertiary fixed & fixed-dim**, surfaceDim, inverseSurface/inverseOnSurface, POS channel accents (GCash/Maya/Load/Credit) — all via `ExpressiveColorRoles` / `LocalExpressiveColors` / `expressiveColors()`. No hardcoded hex in components. |
| Type | `ui/theme/Type.kt` | M3E type scale: bolder display (Black/ExtraBold) & headline weights, tighter display tracking, bold labels |
| Adaptive | `ui/layout/AdaptiveLayout.kt` | `WindowLayout` Compact (<600dp) / Medium (600–839) / Expanded (840+) — all screens derive responsive behavior from this |

## 2. Reusable Expressive Component Library

File: `ui/components/ExpressiveComponents.kt`

| Component | Spec implemented |
|---|---|
| `ExpressiveButton` | All 5 variants (filled/tonal/outlined/elevated/text) × sizes **XS–XL**; idle **pill → squircle shape morph** on press; bouncy press scale; ≥48dp touch targets; optional error/danger `containerColor` override |
| `ExpressiveButtonGroup` | M3E connected button group (uses `SingleChoiceSegmentedButtonRow` with active primaryContainer) and spaced-pill mode |
| `ExpressiveFabMenu` | Morphing FAB: circle ↔ pill on expand, squircle on press; expands into staggered springy menu of actions (30ms per-item stagger); reduce-motion aware |
| `ExpressiveNavigationBar` | Animated **pill indicator** that springs between destinations, icon scale overshoot on select, surface-container tonal bar (no heavy shadows) |
| `ExpressiveChip` | Selection morph squircle ↔ pill + 1.04x springy select scale |
| `ExpressiveSlider` | 48dp min touch target, thumb grows 1.35× while dragging |
| `ExpressiveSwitch` / `ExpressiveCheckbox` | Springy settle overshoot on toggle; M3 tonal colors |
| `ExpressiveDialog` | Squircle corners, spring scale-in (0.85→1), tonal surface (no drop shadows) |
| `ExpressiveModalBottomSheet` | 32dp top corners, expressive drag handle, M3 sheet motion |
| `ExpressiveLoadingIndicator` | Organic squiggle progress (existing `ExpressiveSquiggleCircularProgress`); note: material3 1.4.0 stable does **not** ship the public `LoadingIndicator` composable (internal tokens only) |
| `Modifier.staggeredEnter(index)` | Staggered springy fade+rise enter for lists/grids (≤8-step stagger), snaps under reduce-motion |
| `ExpressiveSnackbarShape` / `ExpressiveLargeAppBarTitle` | Pill snackbar shape; large expressive top-app-bar title |

## 3. Theme wiring

- `StorePointTheme` now provides `LocalReduceMotion` (from system "Remove animations" setting) and `LocalExpressiveColors`.
- Note: `MaterialExpressiveTheme` / `MotionScheme` are **internal** in material3 1.4.0 stable — we keep `MaterialTheme` and implement the same expressive motion contract with our own springs; the call sites are ready to switch to `MaterialExpressiveTheme(MotionScheme.expressive())` when the team bumps to a release where these are public.
- Added dependency: `androidx.graphics:graphics-shapes:1.0.1` (Morphing shapes).

## 4. Component-by-component: Before → After

| Component | Before | After |
|---|---|---|
| Buttons (Login) | `CircleShape` 50dp stock `Button`/`OutlinedButton` with hand-rolled Icon+Text rows | `ExpressiveButton` FILLED-L (Scan badge, Sign In), TONAL-L (Biometric), OUTLINED-M (About); pill↔squircle press morph + spring scale; test tags preserved |
| Login cards | `RoundedCornerShape(24.dp)` | `ExpressiveCardShape` (28dp) |
| Username field | `RoundedCornerShape(16.dp)` | theme `shapes.medium` (20dp) |
| Charge CTA (POS) | stock `Button` 48dp | `ExpressiveButton` FILLED-L, spring morph |
| Drawer End-Shift / Logout (POS) | stock error Button / OutlinedButton | `ExpressiveButton` with `error` container / outlined, 48dp targets |
| Park / item-delete / bulk-delete / shift-start / shift-close dialogs (POS) | stock dialog `Button`s | `ExpressiveButton` M; destructive confirms use error colors |
| Admin dialogs (Delete / Category Save / User Save) | stock `Button`s | `ExpressiveButton` (error where destructive) |
| Admin catalog header (compact + wide) | stock `Button`s with hand-rolled icon rows | `ExpressiveButton` S/M tonal/filled |
| Audit Sync-All (compact + wide) + per-item Correct | stock small `Button`s | `ExpressiveButton` XS |
| Loading | `CircularProgressIndicator` on splash | morphing `ExpressiveMorphingLoader` + squiggle progress (pre-existing) |
| Adaptive layout | — | existing `WindowLayout` contract (Compact/Medium/Expanded) retained; components adapt via it |

## 5. Remaining migration targets (documented, not yet migrated)

Remaining stock `Button`/`TextButton` instances in lower-traffic dialogs (GCash/Globe balance adjusters in the POS drawer, ProductCrudDialog, ProductVariantsDialog, ReceiveInventoryDialog, ReturnRefundDialog, SuppliersAndPurchasesScreen, InventoryPortalScreen, OnboardingScreen, SetupScreen, AboutScreen, Admin*Tab files) follow the identical recipe: replace stock `Button(onClick) { Icon(); Text() }` with `ExpressiveButton(onClick, label, icon, variant, size)`; replace hardcoded `RoundedCornerShape(...)` with `Expressive*Shape` tokens or `rememberMorphShape`; wrap dialogs in `ExpressiveDialog`; use `ExpressiveFabMenu` where a FAB opens an action list; use `ExpressiveChip` for filters and `ExpressiveButtonGroup` for tab-like toggles.

## 6. Accessibility

- All expressive buttons enforce ≥48dp min touch targets (`heightIn(min = 48.dp)`).
- Reduce-motion: every spring/animation in the new library checks `LocalReduceMotion` (wired to the OS animator duration scale) and snaps instead of animating.
- Tonal elevation (surface container tiers) is the primary depth cue; shadows are used sparingly (FAB only).
- Contrast: dynamic color + the AA-tuned fallback palettes keep on-color pairings; testTags retained for UI test parity.