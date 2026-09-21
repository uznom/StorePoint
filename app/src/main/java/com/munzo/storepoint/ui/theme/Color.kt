package com.munzo.storepoint.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Scheme — Refined Electric Indigo (M3 Expressive, WCAG AA tuned)
val BluePrimary = Color(0xFF2D55D6)
val BlueSecondary = Color(0xFF4D6DE8)
val BlueTertiary = Color(0xFF7C3AED)

val PrimaryContainerLight = Color(0xFFDEE6FF)
val OnPrimaryContainerLight = Color(0xFF16233F)
val SecondaryContainerLight = Color(0xFFEAF0FF)
val OnSecondaryContainerLight = Color(0xFF2B2A6B)
val TertiaryContainerLight = Color(0xFFF1E8FF)
val OnTertiaryContainerLight = Color(0xFF4A148C)

val BackgroundLight = Color(0xFFF6F8FC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEDF1F7)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF6F8FC)
val SurfaceContainerLight = Color(0xFFEEF2F8)
val SurfaceContainerHighLight = Color(0xFFE6EBF4)
val SurfaceContainerHighestLight = Color(0xFFDCE2EE)
val OutlineLight = Color(0xFFB9C2D4)
val OutlineVariantLight = Color(0xFFDEE3EE)

// Dark Scheme Expressive colors - Deep Midnight Luminous Indigo
val BluePrimaryDark = Color(0xFF819EFE)
val BlueSecondaryDark = Color(0xFFA5B9FF)
val BlueTertiaryDark = Color(0xFFC4B5FD)

val PrimaryContainerDark = Color(0xFF1E293B)
val OnPrimaryContainerDark = Color(0xFFE0E7FF)
val SecondaryContainerDark = Color(0xFF0F172A)
val OnSecondaryContainerDark = Color(0xFFF8FAFC)
val TertiaryContainerDark = Color(0xFF3B0764)
val OnTertiaryContainerDark = Color(0xFFF3E8FF)

val BackgroundDark = Color(0xFF020617)
val SurfaceDark = Color(0xFF0B0F19)
val SurfaceVariantDark = Color(0xFF1E293B)
val SurfaceContainerLowestDark = Color(0xFF020617)
val SurfaceContainerLowDark = Color(0xFF0B0F19)
val SurfaceContainerDark = Color(0xFF0F172A)
val SurfaceContainerHighDark = Color(0xFF1E293B)
val SurfaceContainerHighestDark = Color(0xFF334155)
val OutlineDark = Color(0xFF334155)
val OutlineVariantDark = Color(0xFF1E293B)

// Semantic Colors for Light Theme
val SuccessLight = Color(0xFF16A34A)
val OnSuccessLight = Color(0xFFFFFFFF)
val SuccessContainerLight = Color(0xFFDCFCE7)
val OnSuccessContainerLight = Color(0xFF14532D)

val WarningLight = Color(0xFFD97706)
val OnWarningLight = Color(0xFFFFFFFF)
val WarningContainerLight = Color(0xFFFEF3C7)
val OnWarningContainerLight = Color(0xFF78350F)

val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)

val InfoLight = Color(0xFF0284C7)
val OnInfoLight = Color(0xFFFFFFFF)
val InfoContainerLight = Color(0xFFE0F2FE)
val OnInfoContainerLight = Color(0xFF0C4A6E)

// Semantic Colors for Dark Theme
val SuccessDark = Color(0xFF4ADE80)
val OnSuccessDark = Color(0xFF052E16)
val SuccessContainerDark = Color(0xFF14532D)
val OnSuccessContainerDark = Color(0xFFDCFCE7)

val WarningDark = Color(0xFFFBBF24)
val OnWarningDark = Color(0xFF451A03)
val WarningContainerDark = Color(0xFF78350F)
val OnWarningContainerDark = Color(0xFFFEF3C7)

val ErrorDark = Color(0xFFEF4444)
val OnErrorDark = Color(0xFFFFFFFF)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFEE2E2)

val InfoDark = Color(0xFF38BDF8)
val OnInfoDark = Color(0xFF082F49)
val InfoContainerDark = Color(0xFF0C4A6E)
val OnInfoContainerDark = Color(0xFFE0F2FE)

// POS-specific semantic colors
val CashColor = Color(0xFF16A34A)
val GCashColor = Color(0xFF007DFE)
val MayaColor = Color(0xFF00B4D8)
val LoadColor = Color(0xFF7C3AED)
val CreditColor = Color(0xFFD97706)

// Low stock alert colors
val LowStockBadge = Color(0xFFDC2626)
val ExpiringSoonBadge = Color(0xFFD97706)
val InStockBadge = Color(0xFF16A34A)

// ---------------------------------------------------------------- M3E extended roles

/**
 * Expanded M3 Expressive color roles not carried by the base [ColorScheme]:
 *  - fixed / fixedDim accent palettes (stable across light/dark)
 *  - surfaceDim / inverseSurface treatments
 *  - semantic success / warning / info roles + containers
 *  - POS channel accent colors (GCash, Maya, load, credit)
 *
 * Provided by [StorePointTheme] via [LocalExpressiveColors]; automatically adapts to
 * Material You dynamic color by falling back to theme-derived roles.
 */
data class ExpressiveColorRoles(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val surfaceDim: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val gcash: Color = GCashColor,
    val maya: Color = MayaColor,
    val load: Color = LoadColor,
    val credit: Color = CreditColor,
)

val LocalExpressiveColors = androidx.compose.runtime.staticCompositionLocalOf {
    ExpressiveColorRoles(
        success = SuccessLight, onSuccess = OnSuccessLight,
        successContainer = SuccessContainerLight, onSuccessContainer = OnSuccessContainerLight,
        warning = WarningLight, onWarning = OnWarningLight,
        warningContainer = WarningContainerLight, onWarningContainer = OnWarningContainerLight,
        info = InfoLight, onInfo = OnInfoLight,
        infoContainer = InfoContainerLight, onInfoContainer = OnInfoContainerLight,
        primaryFixed = PrimaryContainerLight, primaryFixedDim = BluePrimary,
        onPrimaryFixed = OnPrimaryContainerLight,
        secondaryFixed = SecondaryContainerLight, secondaryFixedDim = BlueSecondary,
        tertiaryFixed = TertiaryContainerLight, tertiaryFixedDim = BlueTertiary,
        surfaceDim = Color(0xFFDCE2EE), inverseSurface = Color(0xFF0B0F19),
        inverseOnSurface = Color(0xFFF6F8FC)
    )
}

/** Compose-facing accessor for the expanded M3E roles. */
@Composable
fun expressiveColors(): ExpressiveColorRoles = LocalExpressiveColors.current

/** Light-scheme expressive roles. */
val ExpressiveRolesLight = ExpressiveColorRoles(
    success = SuccessLight, onSuccess = OnSuccessLight,
    successContainer = SuccessContainerLight, onSuccessContainer = OnSuccessContainerLight,
    warning = WarningLight, onWarning = OnWarningLight,
    warningContainer = WarningContainerLight, onWarningContainer = OnWarningContainerLight,
    info = InfoLight, onInfo = OnInfoLight,
    infoContainer = InfoContainerLight, onInfoContainer = OnInfoContainerLight,
    primaryFixed = PrimaryContainerLight, primaryFixedDim = BluePrimary,
    onPrimaryFixed = OnPrimaryContainerLight,
    secondaryFixed = SecondaryContainerLight, secondaryFixedDim = BlueSecondary,
    tertiaryFixed = TertiaryContainerLight, tertiaryFixedDim = BlueTertiary,
    surfaceDim = Color(0xFFDCE2EE), inverseSurface = Color(0xFF0B0F19),
    inverseOnSurface = Color(0xFFF6F8FC)
)

/** Dark-scheme expressive roles. */
val ExpressiveRolesDark = ExpressiveColorRoles(
    success = SuccessDark, onSuccess = OnSuccessDark,
    successContainer = SuccessContainerDark, onSuccessContainer = OnSuccessContainerDark,
    warning = WarningDark, onWarning = OnWarningDark,
    warningContainer = WarningContainerDark, onWarningContainer = OnWarningContainerDark,
    info = InfoDark, onInfo = OnInfoDark,
    infoContainer = InfoContainerDark, onInfoContainer = OnInfoContainerDark,
    primaryFixed = Color(0xFF44599B), primaryFixedDim = Color(0xFF2A3B66),
    onPrimaryFixed = Color(0xFFE0E7FF),
    secondaryFixed = Color(0xFF31406B), secondaryFixedDim = Color(0xFF1E293B),
    tertiaryFixed = Color(0xFF4A148C), tertiaryFixedDim = Color(0xFF31055F),
    surfaceDim = Color(0xFF020617), inverseSurface = Color(0xFFF6F8FC),
    inverseOnSurface = Color(0xFF0B0F19)
)
