package com.linkvault.app.ui.theme

import androidx.compose.ui.graphics.Color

// LinkVault's palette: a deep indigo-navy grounding color (the same family
// as the launcher icon background) with a confident violet-blue for
// interactive elements. Chosen deliberately instead of leaving Compose's
// out-of-the-box template purple (Phase 0's Color.kt) — a personal, local
// bookmark vault should feel calm and considered, not like an unstyled
// scaffold. A warm bronze/gold tertiary is defined for later use (e.g.
// pinned items in Phase 4.5) but isn't shown anywhere yet, so the app
// stays disciplined about spending color on one accent family for now.

// Indigo — primary. Interactive elements: buttons, FAB, selected states.
val IndigoLight = Color(0xFF4F46C4)
val OnIndigoLight = Color(0xFFFFFFFF)
val IndigoContainerLight = Color(0xFFE3E0FF)
val OnIndigoContainerLight = Color(0xFF191254)

val IndigoDark = Color(0xFFC4C0FF)
val OnIndigoDark = Color(0xFF2A2270)
val IndigoContainerDark = Color(0xFF3B3488)
val OnIndigoContainerDark = Color(0xFFE3E0FF)

// Slate — secondary. Lower-emphasis actions and supporting UI.
val SlateLight = Color(0xFF5C5A72)
val OnSlateLight = Color(0xFFFFFFFF)
val SlateDark = Color(0xFFC6C3DD)
val OnSlateDark = Color(0xFF2E2C42)

// Bronze — tertiary. Defined for consistency; not used yet (see note above).
val BronzeLight = Color(0xFF7C5800)
val OnBronzeLight = Color(0xFFFFFFFF)
val BronzeContainerLight = Color(0xFFFFDEA6)
val OnBronzeContainerLight = Color(0xFF271900)

val BronzeDark = Color(0xFFF0BD5C)
val OnBronzeDark = Color(0xFF412D00)
val BronzeContainerDark = Color(0xFF5E4200)
val OnBronzeContainerDark = Color(0xFFFFDEA6)

// Neutral surfaces.
val SurfaceLight = Color(0xFFFFFBFF)
val OnSurfaceLight = Color(0xFF1C1B1F)
val SurfaceVariantLight = Color(0xFFE5E1EC)
val OnSurfaceVariantLight = Color(0xFF47464F)
val OutlineLight = Color(0xFF787680)

// Deep navy — the same family as the launcher icon background, so the app
// icon and the in-app dark theme read as one considered identity rather
// than two unrelated color choices.
val SurfaceDark = Color(0xFF131218)
val OnSurfaceDark = Color(0xFFE5E1E6)
val SurfaceVariantDark = Color(0xFF47464F)
val OnSurfaceVariantDark = Color(0xFFC8C5D0)
val OutlineDark = Color(0xFF918F9A)

// Error — Material3's standard error red. No reason to deviate; it's
// already well-tested for legibility and universally recognized as "error."
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
